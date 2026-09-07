package mx.jobmatch.cvimport.adapters.web;

import mx.jobmatch.cvimport.application.CvImportService;
import mx.jobmatch.cvimport.domain.CvImportView;
import mx.jobmatch.cvimport.domain.CvDocumentSummary;
import mx.jobmatch.identity.adapters.security.AccountPrincipal;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;
import java.util.List;

import static mx.jobmatch.cvimport.application.CvImportExceptions.InvalidFile;

@Profile("api")
@RestController
@RequestMapping("/api/v1/me")
public class CvImportController {
    private final CvImportService imports;
    public CvImportController(CvImportService imports){this.imports=imports;}

    @PostMapping(value="/cv-imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CvImportView> upload(@AuthenticationPrincipal AccountPrincipal principal,
                                        @RequestPart("file")MultipartFile file){
        try{
            CvImportView created=imports.upload(principal.accountId(),file.getOriginalFilename(),file.getInputStream());
            return ResponseEntity.accepted().location(URI.create("/api/v1/me/cv-imports/"+created.id())).body(created);
        }catch(java.io.IOException failure){throw new InvalidFile("No fue posible leer el archivo.");}
    }

    @GetMapping("/cv-imports/{id}")
    CvImportView get(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID id){return imports.get(principal.accountId(),id);}

    @GetMapping("/cv-documents")
    List<CvDocumentSummary> documents(@AuthenticationPrincipal AccountPrincipal principal){
        return imports.listDocuments(principal.accountId());
    }

    @GetMapping("/cv-documents/{id}/download")
    ResponseEntity<FileSystemResource> download(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID id){
        var file=imports.download(principal.accountId(),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(file.originalFilename(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .body(new FileSystemResource(file.path()));
    }

    @PutMapping("/cv-imports/{id}/candidates/{candidateId}")
    ResponseEntity<CvImportView> decide(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID id,
                                        @PathVariable UUID candidateId,@RequestHeader("If-Match")String ifMatch,
                                        @RequestBody DecisionRequest request){
        if(request==null)throw new mx.jobmatch.cvimport.application.CvImportExceptions.ImportNotReady("La decisión es obligatoria.");
        CvImportView result=imports.decide(principal.accountId(),id,candidateId,version(ifMatch),request.decision(),request.duplicateResolution());
        return ResponseEntity.ok().eTag(Long.toString(result.candidates().stream().filter(c->c.id().equals(candidateId)).findFirst().orElseThrow().decisionVersion())).body(result);
    }

    @PostMapping("/cv-imports/{id}/confirm")
    ResponseEntity<ProfessionalProfile> confirm(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID id){
        ProfessionalProfile profile=imports.confirm(principal.accountId(),id);
        return ResponseEntity.ok().eTag(Long.toString(profile.version())).body(profile);
    }

    @DeleteMapping("/cv-documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID id){imports.delete(principal.accountId(),id);}

    public record DecisionRequest(String decision,String duplicateResolution){}
    private static long version(String header){try{return Long.parseLong(header.strip().replace("W/","").replace("\"",""));}
        catch(Exception failure){throw new mx.jobmatch.cvimport.application.CvImportExceptions.DecisionConflict();}}
}
