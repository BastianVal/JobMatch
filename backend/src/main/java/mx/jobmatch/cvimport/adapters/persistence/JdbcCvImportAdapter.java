package mx.jobmatch.cvimport.adapters.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.cvimport.application.CvImportExceptions.*;
import mx.jobmatch.cvimport.application.CvImportRepository;
import mx.jobmatch.cvimport.application.CvStoragePort;
import mx.jobmatch.cvimport.domain.CvImportView;
import mx.jobmatch.cvimport.domain.CvDocumentSummary;
import mx.jobmatch.cvimport.domain.ImportProposal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCvImportAdapter implements CvImportRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final int maxActiveDocuments;

    public JdbcCvImportAdapter(JdbcClient jdbc, ObjectMapper json,
                               @Value("${jobmatch.cv.max-active-documents}") int maxActiveDocuments) {
        this.jdbc=jdbc; this.json=json; this.maxActiveDocuments=maxActiveDocuments;
    }

    @Override
    @Transactional
    public CvImportView create(UUID accountId, String originalFilename, CvStoragePort.StoredFile file,
                               long profileBaseVersion, Instant expiresAt, String extractorVersion) {
        long account = jdbc.sql("SELECT id FROM iam.account WHERE public_id=:id AND status='ACTIVE' FOR UPDATE")
                .param("id",accountId).query(Long.class).optional().orElseThrow(ImportNotFound::new);
        int active = jdbc.sql("SELECT count(*) FROM cvimport.cv_document WHERE account_id=:account AND status='ACTIVE'")
                .param("account",account).query(Integer.class).single();
        if(active>=maxActiveDocuments) throw new ActiveDocumentLimit();
        try {
            long stored = jdbc.sql("""
                    INSERT INTO cvimport.stored_file(public_id,account_id,storage_key,detected_media_type,sha256,byte_size,status)
                    VALUES (:id,:account,:key,:media,:hash,:size,'STORED') RETURNING id
                    """).param("id",UUID.randomUUID()).param("account",account).param("key",file.storageKey())
                    .param("media",file.mediaType()).param("hash",file.sha256()).param("size",file.byteSize())
                    .query(Long.class).single();
            UUID documentId=UUID.randomUUID();
            long document=jdbc.sql("""
                    INSERT INTO cvimport.cv_document(public_id,account_id,stored_file_id,original_filename,status)
                    VALUES (:id,:account,:stored,:name,'ACTIVE') RETURNING id
                    """).param("id",documentId).param("account",account).param("stored",stored).param("name",originalFilename)
                    .query(Long.class).single();
            UUID importId=UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO cvimport.import_run(public_id,cv_document_id,profile_base_version,status,extractor_version,expires_at)
                    VALUES (:id,:document,:version,'QUEUED',:extractor,:expires)
                    """).param("id",importId).param("document",document).param("version",profileBaseVersion)
                    .param("extractor",extractorVersion).param("expires",Timestamp.from(expiresAt)).update();
            return find(accountId,importId).orElseThrow();
        } catch (DataIntegrityViolationException duplicate) { throw new DuplicateFile(); }
    }

    @Override
    public Optional<CvImportView> find(UUID accountId, UUID importId) {
        return jdbc.sql("""
                SELECT run.id internal_id,run.public_id,document.public_id document_id,document.original_filename,
                  run.status,run.profile_base_version,run.extractor_version,run.safe_error_code,run.expires_at,
                  run.confirmed_profile_version
                FROM cvimport.import_run run JOIN cvimport.cv_document document ON document.id=run.cv_document_id
                JOIN iam.account account ON account.id=document.account_id
                WHERE account.public_id=:account AND run.public_id=:id
                """).param("account",accountId).param("id",importId).query((rs,row)->new RunRow(rs.getLong("internal_id"),
                rs.getObject("public_id",UUID.class),rs.getObject("document_id",UUID.class),rs.getString("original_filename"),
                rs.getString("status"),rs.getLong("profile_base_version"),rs.getString("extractor_version"),
                rs.getString("safe_error_code"),rs.getTimestamp("expires_at").toInstant(),
                nullableLong(rs,"confirmed_profile_version"))).optional().map(this::hydrate);
    }

    @Override
    public List<CvDocumentSummary> listDocuments(UUID accountId) {
        return jdbc.sql("""
                SELECT document.public_id, document.original_filename, document.created_at,
                       (SELECT run.status FROM cvimport.import_run run
                        WHERE run.cv_document_id=document.id ORDER BY run.created_at DESC, run.id DESC LIMIT 1) latest_status
                FROM cvimport.cv_document document
                JOIN iam.account account ON account.id=document.account_id
                WHERE account.public_id=:accountId AND account.status='ACTIVE' AND document.status='ACTIVE'
                ORDER BY document.created_at DESC, document.id DESC
                LIMIT 5
                """).param("accountId", accountId).query((rs, row) -> new CvDocumentSummary(
                rs.getObject("public_id", UUID.class), rs.getString("original_filename"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("latest_status"))).list();
    }

    @Override
    public Optional<DocumentFile> documentFile(UUID accountId,UUID documentId){
        return jdbc.sql("""
                SELECT file.storage_key,document.original_filename,file.detected_media_type
                FROM cvimport.cv_document document JOIN cvimport.stored_file file ON file.id=document.stored_file_id
                JOIN iam.account account ON account.id=document.account_id
                WHERE account.public_id=:account AND account.status='ACTIVE'
                  AND document.public_id=:document AND document.status='ACTIVE' AND file.status='STORED'
                """).param("account",accountId).param("document",documentId)
                .query((rs,row)->new DocumentFile(rs.getString("storage_key"),rs.getString("original_filename"),
                        rs.getString("detected_media_type"))).optional();
    }

    @Override
    public Optional<ExtractionInput> startExtraction(UUID importId) {
        return jdbc.sql("""
                UPDATE cvimport.import_run run SET status='PROCESSING',started_at=now()
                FROM cvimport.cv_document document,cvimport.stored_file file,iam.account account
                WHERE run.public_id=:id AND run.status='QUEUED' AND run.expires_at>now()
                  AND document.id=run.cv_document_id AND file.id=document.stored_file_id AND account.id=document.account_id
                RETURNING run.public_id,account.public_id account_id,file.storage_key,file.detected_media_type
                """).param("id",importId).query((rs,row)->new ExtractionInput(rs.getObject("public_id",UUID.class),
                rs.getObject("account_id",UUID.class),rs.getString("storage_key"),rs.getString("detected_media_type"))).optional();
    }

    @Override
    @Transactional
    public void completeExtraction(UUID importId, String textHash, List<ImportProposal> proposals) {
        long run=jdbc.sql("SELECT id FROM cvimport.import_run WHERE public_id=:id AND status='PROCESSING' FOR UPDATE")
                .param("id",importId).query(Long.class).single();
        for(var proposal:proposals) insertCandidate(run,proposal);
        jdbc.sql("UPDATE cvimport.import_run SET status='READY',extracted_text_hash=:hash,finished_at=now() WHERE id=:id")
                .param("hash",textHash).param("id",run).update();
    }

    private void insertCandidate(long run,ImportProposal proposal) {
        UUID publicId=UUID.randomUUID();
        long candidate=jdbc.sql("""
                INSERT INTO cvimport.import_candidate(public_id,import_run_id,candidate_type,proposed_payload,fingerprint)
                VALUES (:id,:run,:type,CAST(:payload AS jsonb),:fingerprint) RETURNING id
                """).param("id",publicId).param("run",run).param("type",proposal.type())
                .param("payload",write(proposal.payload())).param("fingerprint",proposal.fingerprint()).query(Long.class).single();
        Existing duplicate=findDuplicate(run,proposal);
        if(duplicate!=null) jdbc.sql("""
                INSERT INTO cvimport.duplicate_case(public_id,import_candidate_id,existing_entity_type,
                  existing_entity_public_id,similarity_score) VALUES (:id,:candidate,:type,:existing,1.0)
                """).param("id",UUID.randomUUID()).param("candidate",candidate).param("type",duplicate.type())
                .param("existing",duplicate.id()).update();
    }

    private Existing findDuplicate(long run,ImportProposal proposal) {
        Map<String,Object> p=proposal.payload();
        String sql=switch(proposal.type()) {
            case "TRAJECTORY" -> """
                SELECT item.public_id FROM profile.trajectory_item item JOIN profile.professional_profile profile ON profile.id=item.profile_id
                JOIN cvimport.cv_document document ON document.account_id=profile.account_id JOIN cvimport.import_run run ON run.cv_document_id=document.id
                WHERE run.id=:run AND item.item_type=:a AND lower(unaccent(item.title))=lower(unaccent(:b))
                  AND lower(unaccent(coalesce(item.organization,'')))=lower(unaccent(coalesce(:c,'')))
                  AND item.start_year=:d AND item.start_month=:e LIMIT 1""";
            case "EDUCATION" -> """
                SELECT item.public_id FROM profile.education item JOIN profile.professional_profile profile ON profile.id=item.profile_id
                JOIN cvimport.cv_document document ON document.account_id=profile.account_id JOIN cvimport.import_run run ON run.cv_document_id=document.id
                WHERE run.id=:run AND lower(unaccent(item.institution))=lower(unaccent(:a))
                  AND lower(unaccent(item.degree))=lower(unaccent(:b)) AND item.end_year=:c LIMIT 1""";
            case "CERTIFICATION" -> """
                SELECT item.public_id FROM profile.course_certification item JOIN profile.professional_profile profile ON profile.id=item.profile_id
                JOIN cvimport.cv_document document ON document.account_id=profile.account_id JOIN cvimport.import_run run ON run.cv_document_id=document.id
                WHERE run.id=:run AND lower(unaccent(item.name))=lower(unaccent(:a))
                  AND lower(unaccent(coalesce(item.issuer,'')))=lower(unaccent(coalesce(:b,''))) LIMIT 1""";
            case "LANGUAGE" -> """
                SELECT item.public_id FROM profile.language item JOIN profile.professional_profile profile ON profile.id=item.profile_id
                JOIN cvimport.cv_document document ON document.account_id=profile.account_id JOIN cvimport.import_run run ON run.cv_document_id=document.id
                WHERE run.id=:run AND lower(item.language_code)=lower(:a) LIMIT 1""";
            case "SKILL" -> """
                SELECT item.public_id FROM profile.profile_skill item JOIN catalog.skill skill ON skill.id=item.skill_id
                JOIN profile.professional_profile profile ON profile.id=item.profile_id JOIN cvimport.cv_document document ON document.account_id=profile.account_id
                JOIN cvimport.import_run run ON run.cv_document_id=document.id WHERE run.id=:run AND skill.public_id=CAST(:a AS uuid) LIMIT 1""";
            default -> throw new IllegalArgumentException("Unsupported proposal");
        };
        var statement=jdbc.sql(sql).param("run",run);
        statement=switch(proposal.type()) {
            case "TRAJECTORY" -> statement.param("a",p.get("type")).param("b",p.get("title")).param("c",p.get("organization"))
                    .param("d",p.get("startYear")).param("e",p.get("startMonth"));
            case "EDUCATION" -> statement.param("a",p.get("institution")).param("b",p.get("degree")).param("c",p.get("endYear"));
            case "CERTIFICATION" -> statement.param("a",p.get("name")).param("b",p.get("issuer"));
            case "LANGUAGE" -> statement.param("a",p.get("code"));
            case "SKILL" -> statement.param("a",p.get("catalogSkillId"));
            default -> statement;
        };
        return statement.query(UUID.class).optional().map(id->new Existing(proposal.type(),id)).orElse(null);
    }

    @Override public void failExtraction(UUID importId,String code){jdbc.sql("UPDATE cvimport.import_run SET status='FAILED',safe_error_code=:code,finished_at=now() WHERE public_id=:id AND status IN ('QUEUED','PROCESSING')").param("code",code).param("id",importId).update();}

    @Override
    @Transactional
    public CvImportView decide(UUID accountId,UUID importId,UUID candidateId,long expected,String decision,String resolution){
        if(decision==null||!List.of("ACCEPTED","SKIPPED").contains(decision)) throw new ImportNotReady("Decisión inválida.");
        CandidateState state=jdbc.sql("""
                SELECT candidate.id,candidate.candidate_type,candidate.decision_version,duplicate.id duplicate_id
                FROM cvimport.import_candidate candidate JOIN cvimport.import_run run ON run.id=candidate.import_run_id
                JOIN cvimport.cv_document document ON document.id=run.cv_document_id JOIN iam.account account ON account.id=document.account_id
                LEFT JOIN cvimport.duplicate_case duplicate ON duplicate.import_candidate_id=candidate.id
                WHERE account.public_id=:account AND run.public_id=:run AND candidate.public_id=:candidate
                  AND run.status='READY' AND run.expires_at>now() FOR UPDATE OF candidate
                """).param("account",accountId).param("run",importId).param("candidate",candidateId)
                .query((rs,row)->new CandidateState(rs.getLong("id"),rs.getString("candidate_type"),rs.getLong("decision_version"),nullableLong(rs,"duplicate_id"))).optional()
                .orElseThrow(CandidateNotFound::new);
        if(state.version()!=expected) throw new DecisionConflict();
        if(state.duplicateId()!=null){
            String required=decision.equals("SKIPPED")?"SKIP":resolution;
            if(required==null || (decision.equals("ACCEPTED")&&!List.of("KEEP_BOTH","REPLACE_EXISTING").contains(required))
                    || (List.of("SKILL","LANGUAGE").contains(state.type())&&"KEEP_BOTH".equals(required)))
                throw new ImportNotReady("Resuelve el posible duplicado.");
            jdbc.sql("UPDATE cvimport.duplicate_case SET resolution=:resolution,resolved_at=now() WHERE id=:id")
                    .param("resolution",required).param("id",state.duplicateId()).update();
        } else if(resolution!=null) throw new ImportNotReady("Esta propuesta no tiene duplicado.");
        jdbc.sql("UPDATE cvimport.import_candidate SET decision=:decision,decision_version=decision_version+1,updated_at=now() WHERE id=:id")
                .param("decision",decision).param("id",state.id()).update();
        return find(accountId,importId).orElseThrow();
    }

    @Override
    public Confirmation lockForConfirmation(UUID accountId,UUID importId){
        String status=jdbc.sql("""
                SELECT run.status FROM cvimport.import_run run JOIN cvimport.cv_document document ON document.id=run.cv_document_id
                JOIN iam.account account ON account.id=document.account_id WHERE account.public_id=:account AND run.public_id=:id FOR UPDATE OF run
                """).param("account",accountId).param("id",importId).query(String.class).optional().orElseThrow(ImportNotFound::new);
        CvImportView view=find(accountId,importId).orElseThrow();
        if(status.equals("CONFIRMED")) return new Confirmation(view);
        if(!status.equals("READY")||!view.expiresAt().isAfter(Instant.now())) throw new ImportNotReady("La importación no está lista o expiró.");
        if(view.candidates().stream().anyMatch(c->c.decision().equals("PENDING"))) throw new ImportNotReady("Revisa todas las propuestas.");
        if(view.candidates().stream().anyMatch(c->c.duplicate()!=null&&c.duplicate().resolution()==null)) throw new ImportNotReady("Resuelve todos los duplicados.");
        return new Confirmation(view);
    }

    @Override public void markConfirmed(UUID id,long version){jdbc.sql("UPDATE cvimport.import_run SET status='CONFIRMED',confirmed_profile_version=:version,confirmed_at=now() WHERE public_id=:id AND status='READY'").param("version",version).param("id",id).update();}

    @Override public List<String> storageKeysForAccount(UUID accountId){return jdbc.sql("SELECT file.storage_key FROM cvimport.stored_file file JOIN iam.account account ON account.id=file.account_id WHERE account.public_id=:id AND file.status='STORED'").param("id",accountId).query(String.class).list();}

    @Override
    @Transactional
    public Optional<String> deleteDocument(UUID accountId,UUID documentId){
        Optional<FileRow> file=jdbc.sql("""
                SELECT document.id document_id,file.id file_id,file.storage_key
                FROM cvimport.cv_document document JOIN cvimport.stored_file file ON file.id=document.stored_file_id
                JOIN iam.account account ON account.id=document.account_id
                WHERE account.public_id=:account AND document.public_id=:document AND document.status='ACTIVE'
                FOR UPDATE OF document,file
                """).param("account",accountId).param("document",documentId)
                .query((rs,row)->new FileRow(rs.getLong("document_id"),rs.getLong("file_id"),rs.getString("storage_key"))).optional();
        file.ifPresent(value->{
            jdbc.sql("UPDATE cvimport.cv_document SET status='DELETED',deleted_at=now() WHERE id=:id").param("id",value.documentId()).update();
            jdbc.sql("UPDATE cvimport.stored_file SET status='DELETED',deleted_at=now() WHERE id=:id").param("id",value.fileId()).update();
        });
        return file.map(FileRow::key);
    }

    @Override public void expireReady(Instant now){jdbc.sql("UPDATE cvimport.import_run SET status='EXPIRED',finished_at=coalesce(finished_at,now()) WHERE status IN ('QUEUED','READY') AND expires_at<=:now").param("now",Timestamp.from(now)).update();}

    private CvImportView hydrate(RunRow run){
        var candidates=jdbc.sql("""
                SELECT candidate.public_id,candidate.candidate_type,candidate.payload_version,candidate.proposed_payload::text,
                  candidate.decision,candidate.decision_version,duplicate.public_id duplicate_id,duplicate.existing_entity_type,
                  duplicate.existing_entity_public_id,duplicate.similarity_score,duplicate.resolution
                FROM cvimport.import_candidate candidate LEFT JOIN cvimport.duplicate_case duplicate ON duplicate.import_candidate_id=candidate.id
                WHERE candidate.import_run_id=:run ORDER BY candidate.id
                """).param("run",run.id()).query((rs,row)->new CvImportView.Candidate(rs.getObject("public_id",UUID.class),
                rs.getString("candidate_type"),rs.getInt("payload_version"),read(rs.getString("proposed_payload")),
                rs.getString("decision"),rs.getLong("decision_version"),rs.getObject("duplicate_id")==null?null:
                new CvImportView.Duplicate(rs.getObject("duplicate_id",UUID.class),rs.getString("existing_entity_type"),
                        rs.getObject("existing_entity_public_id",UUID.class),rs.getDouble("similarity_score"),rs.getString("resolution")))).list();
        return new CvImportView(run.publicId(),run.documentId(),run.filename(),run.status(),run.baseVersion(),run.extractor(),
                run.error(),run.expiresAt(),run.confirmedVersion(),candidates);
    }
    private String write(Map<String,Object> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> read(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private static Long nullableLong(java.sql.ResultSet rs,String column)throws java.sql.SQLException{long value=rs.getLong(column);return rs.wasNull()?null:value;}
    private record RunRow(long id,UUID publicId,UUID documentId,String filename,String status,long baseVersion,String extractor,String error,Instant expiresAt,Long confirmedVersion){}
    private record Existing(String type,UUID id){}
    private record CandidateState(long id,String type,long version,Long duplicateId){}
    private record FileRow(long documentId,long fileId,String key){}
}
