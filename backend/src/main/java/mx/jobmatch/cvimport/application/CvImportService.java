package mx.jobmatch.cvimport.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.cvimport.domain.CvImportView;
import mx.jobmatch.cvimport.domain.CvDocumentSummary;
import mx.jobmatch.operations.application.BackgroundTaskPort;
import mx.jobmatch.profile.application.ProfileService;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static mx.jobmatch.cvimport.application.CvImportExceptions.*;

@Profile("api")
@Service
public class CvImportService {
    private final CvImportRepository imports;
    private final CvStoragePort storage;
    private final BackgroundTaskPort tasks;
    private final ProfileService profiles;
    private final ObjectMapper json;
    private final int lifetimeDays;

    public CvImportService(CvImportRepository imports,CvStoragePort storage,BackgroundTaskPort tasks,
                           ProfileService profiles,ObjectMapper json,
                           @Value("${jobmatch.cv.proposal-lifetime-days}") int lifetimeDays){
        this.imports=imports;this.storage=storage;this.tasks=tasks;this.profiles=profiles;this.json=json;this.lifetimeDays=lifetimeDays;
    }

    @Transactional
    public CvImportView upload(UUID accountId,String originalFilename,InputStream input){
        String filename=filename(originalFilename);
        var stored=storage.store(input,filename);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED)storage.delete(stored.storageKey());
            }
        });
        try{
            long baseVersion=profiles.get(accountId).version();
            CvImportView created=imports.create(accountId,filename,stored,baseVersion,
                    Instant.now().plus(lifetimeDays,ChronoUnit.DAYS),CvTextExtractor.VERSION);
            tasks.enqueue("EXTRACT_CV","{\"importId\":\""+created.id()+"\"}","cv-import:"+created.id(),Instant.now(),1);
            return created;
        }catch(RuntimeException failure){storage.delete(stored.storageKey());throw failure;}
    }

    @Transactional(readOnly=true)
    public CvImportView get(UUID accountId,UUID importId){return imports.find(accountId,importId).orElseThrow(ImportNotFound::new);}

    @Transactional(readOnly=true)
    public List<CvDocumentSummary> listDocuments(UUID accountId){return imports.listDocuments(accountId);}

    @Transactional
    public CvImportView decide(UUID accountId,UUID importId,UUID candidateId,long version,String decision,String resolution){
        return imports.decide(accountId,importId,candidateId,version,upper(decision),upper(resolution));
    }

    @Transactional
    public ProfessionalProfile confirm(UUID accountId,UUID importId){
        CvImportView view=imports.lockForConfirmation(accountId,importId).view();
        ProfessionalProfile current=profiles.get(accountId);
        if(view.status().equals("CONFIRMED")) return current;
        if(current.version()!=view.profileBaseVersion()) throw new mx.jobmatch.profile.application.ProfileExceptions.VersionConflict();
        boolean changes=view.candidates().stream().anyMatch(candidate->candidate.decision().equals("ACCEPTED")
                && (candidate.duplicate()==null||!"SKIP".equals(candidate.duplicate().resolution())));
        if(!changes){imports.markConfirmed(importId,current.version());return current;}
        ProfileDraft merged=merge(current.data(),view.candidates());
        ProfessionalProfile saved=profiles.replace(accountId,view.profileBaseVersion(),merged);
        imports.markConfirmed(importId,saved.version());
        return saved;
    }

    @Transactional
    public void delete(UUID accountId,UUID documentId){
        String key=imports.deleteDocument(accountId,documentId).orElseThrow(ImportNotFound::new);
        storage.delete(key);
    }

    private ProfileDraft merge(ProfileDraft current,List<CvImportView.Candidate> candidates){
        var trajectory=new ArrayList<>(current.trajectory());
        var education=new ArrayList<>(current.education());
        var certifications=new ArrayList<>(current.certifications());
        var languages=new ArrayList<>(current.languages());
        var skills=new ArrayList<>(current.skills());
        for(var candidate:candidates){
            if(!candidate.decision().equals("ACCEPTED") || (candidate.duplicate()!=null&&"SKIP".equals(candidate.duplicate().resolution()))) continue;
            UUID replaced=candidate.duplicate()!=null&&"REPLACE_EXISTING".equals(candidate.duplicate().resolution())
                    ?candidate.duplicate().existingEntityId():null;
            switch(candidate.type()){
                case "TRAJECTORY"->{if(replaced!=null)trajectory.removeIf(v->v.id().equals(replaced));trajectory.add(convert(candidate,ProfileDraft.TrajectoryItem.class));}
                case "EDUCATION"->{if(replaced!=null)education.removeIf(v->v.id().equals(replaced));education.add(convert(candidate,ProfileDraft.Education.class));}
                case "CERTIFICATION"->{if(replaced!=null)certifications.removeIf(v->v.id().equals(replaced));certifications.add(convert(candidate,ProfileDraft.Certification.class));}
                case "LANGUAGE"->{if(replaced!=null)languages.removeIf(v->v.id().equals(replaced));languages.add(convert(candidate,ProfileDraft.Language.class));}
                case "SKILL"->{if(replaced!=null)skills.removeIf(v->v.id().equals(replaced));skills.add(convert(candidate,ProfileDraft.Skill.class));}
                default->throw new ImportNotReady("Tipo de propuesta inválido.");
            }
        }
        var trajectoryIds=new HashSet<UUID>();trajectory.forEach(item->trajectoryIds.add(item.id()));
        List<ProfileDraft.Skill> recalculable=skills.stream().map(skill->new ProfileDraft.Skill(skill.id(),skill.catalogSkillId(),
                skill.customName(),skill.name(),skill.proficiency(),skill.matchEligible(),
                skill.evidenceTrajectoryIds().stream().filter(trajectoryIds::contains).toList(),0,java.math.BigDecimal.ZERO)).toList();
        return new ProfileDraft(current.headline(),current.summary(),current.location(),current.seniority(),current.targetRoles(),
                current.preferences(),current.excludedEmployers(),trajectory,education,certifications,languages,recalculable);
    }

    private <T>T convert(CvImportView.Candidate candidate,Class<T> type){return json.convertValue(candidate.proposal(),type);}
    private static String upper(String value){return value==null?null:value.strip().toUpperCase(Locale.ROOT);}
    private static String filename(String value){
        if(value==null||value.isBlank())throw new InvalidFile("El nombre del archivo es obligatorio.");
        String cleaned=value.replace('\\','/');cleaned=cleaned.substring(cleaned.lastIndexOf('/')+1).strip();
        if(cleaned.isEmpty()||cleaned.length()>255||cleaned.chars().anyMatch(c->c<32))throw new InvalidFile("El nombre del archivo no es válido.");
        return cleaned;
    }
}
