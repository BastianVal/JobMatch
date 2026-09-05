package mx.jobmatch.cvimport.adapters.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.cvimport.application.CvImportRepository;
import mx.jobmatch.cvimport.application.CvStoragePort;
import mx.jobmatch.cvimport.application.CvTextExtractor;
import mx.jobmatch.operations.application.BackgroundTaskHandler;
import mx.jobmatch.operations.application.NonRetryableTaskException;
import mx.jobmatch.operations.domain.BackgroundTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Profile("worker")
@Component
public class CvExtractionTaskHandler implements BackgroundTaskHandler {
    private final CvImportRepository imports;private final CvStoragePort storage;private final CvTextExtractor extractor;
    private final ObjectMapper json;private final long timeoutSeconds;
    public CvExtractionTaskHandler(CvImportRepository imports,CvStoragePort storage,CvTextExtractor extractor,ObjectMapper json,
                                   @Value("${jobmatch.cv.parse-timeout-seconds}")long timeoutSeconds){
        this.imports=imports;this.storage=storage;this.extractor=extractor;this.json=json;this.timeoutSeconds=timeoutSeconds;
    }
    @Override public boolean supports(String type){return "EXTRACT_CV".equals(type);}
    @Override public void handle(BackgroundTask task){
        UUID importId=read(task.payload());
        var input=imports.startExtraction(importId).orElseThrow(()->new NonRetryableTaskException("CV_IMPORT_NOT_QUEUED"));
        var executor=Executors.newVirtualThreadPerTaskExecutor();
        var future=executor.submit(()->extractor.extract(storage.resolve(input.storageKey()),input.mediaType()));
        try{
            var result=future.get(timeoutSeconds,TimeUnit.SECONDS);
            imports.completeExtraction(importId,result.textHash(),result.proposals());
        }catch(TimeoutException timeout){future.cancel(true);imports.failExtraction(importId,"CV_PARSE_TIMEOUT");throw new NonRetryableTaskException("CV_PARSE_TIMEOUT");}
        catch(ExecutionException failure){
            if(failure.getCause() instanceof CvTextExtractor.ExtractionFailure extraction){
                imports.failExtraction(importId,extraction.safeCode());
                throw new NonRetryableTaskException(extraction.safeCode());
            }
            imports.failExtraction(importId,"CV_PARSE_FAILED");
            throw new NonRetryableTaskException("CV_PARSE_FAILED");
        }
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();imports.failExtraction(importId,"CV_PARSE_INTERRUPTED");throw new NonRetryableTaskException("CV_PARSE_INTERRUPTED");}
        catch(NonRetryableTaskException failure){throw failure;}
        catch(Exception failure){imports.failExtraction(importId,"CV_PARSE_FAILED");throw new NonRetryableTaskException("CV_PARSE_FAILED");}
        finally{executor.shutdownNow();}
    }
    private UUID read(String payload){try{return json.readValue(payload,Payload.class).importId();}catch(Exception e){throw new NonRetryableTaskException("INVALID_CV_TASK");}}
    private record Payload(UUID importId){}
}
