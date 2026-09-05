package mx.jobmatch.cvimport.application;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CvTextExtractorTest {
    @TempDir Path directory;
    private final UUID javaId=UUID.fromString("12000000-0000-0000-0000-000000000001");
    private final CvTextExtractor extractor=new CvTextExtractor(text->text.contains("java")
            ?List.of(new CvCatalogPort.SkillMatch(javaId,"Java")):List.of());

    @Test void extractsVersionedProposalsFromTextualPdf()throws Exception{
        Path file=directory.resolve("cv.pdf");
        try(PDDocument document=new PDDocument()){
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream content=new PDPageContentStream(document,page)){
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),10);
                content.newLineAtOffset(40,750);
                for(String line:lines()){content.showText(line);content.newLineAtOffset(0,-18);}
                content.endText();
            }
            document.save(file.toFile());
        }
        assertProposals(extractor.extract(file,"application/pdf"));
    }

    @Test void extractsVersionedProposalsFromDocx()throws Exception{
        Path file=directory.resolve("cv.docx");
        try(XWPFDocument document=new XWPFDocument();var output=Files.newOutputStream(file)){
            for(String line:lines())document.createParagraph().createRun().setText(line);
            document.write(output);
        }
        assertProposals(extractor.extract(file,"application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    private void assertProposals(CvTextExtractor.Extraction result){
        assertThat(result.textHash()).hasSize(64);
        assertThat(result.proposals()).extracting("type")
                .contains("TRAJECTORY","EDUCATION","CERTIFICATION","LANGUAGE","SKILL");
        var experience=result.proposals().stream().filter(p->p.type().equals("TRAJECTORY")).findFirst().orElseThrow();
        assertThat(experience.payload()).containsEntry("startYear",2022).containsEntry("startMonth",1)
                .containsEntry("endYear",2024).containsEntry("endMonth",6);
        var skill=result.proposals().stream().filter(p->p.type().equals("SKILL")).findFirst().orElseThrow();
        assertThat(skill.payload().get("evidenceTrajectoryIds"))
                .isEqualTo(List.of(experience.payload().get("id")));
    }
    private static List<String> lines(){return List.of(
            "EXPERIENCE: Backend Developer | Example Corp | 2022-01 | 2024-06",
            "EDUCATION: Computer Engineering | UNAM | 2017 | 2021",
            "CERTIFICATION: Spring Professional | VMware | 2025",
            "LANGUAGE: es | Espanol | NATIVE",
            "SKILLS: Java, Spring Boot and PostgreSQL");}
}
