package mx.jobmatch.cvimport.adapters.storage;

import mx.jobmatch.cvimport.application.CvImportExceptions.InvalidFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemCvStorageAdapterTest {
    @TempDir Path directory;

    @Test void storesDocxUnderRandomInternalNameAndDetectsItsType() throws Exception {
        byte[] content;
        try (var bytes = new ByteArrayOutputStream(); var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Java developer");
            document.write(bytes);
            content = bytes.toByteArray();
        }
        var storage = new FileSystemCvStorageAdapter(directory.toString(), 5 * 1024 * 1024);

        var stored = storage.store(new ByteArrayInputStream(content), "../../Curriculum Personal.docx");

        assertThat(stored.storageKey()).matches("[0-9a-f-]{36}\\.bin").doesNotContain("Curriculum");
        assertThat(stored.mediaType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(stored.sha256()).hasSize(64);
        assertThat(Files.readAllBytes(storage.resolve(stored.storageKey()))).isEqualTo(content);
    }

    @Test void rejectsSpoofedExtensionAndOversizedContent() {
        var storage = new FileSystemCvStorageAdapter(directory.toString(), 8);
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream("plain text".getBytes(StandardCharsets.UTF_8)), "cv.pdf"))
                .isInstanceOf(InvalidFile.class);
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream("%PDF-more".getBytes(StandardCharsets.UTF_8)), "cv.pdf"))
                .isInstanceOf(InvalidFile.class).hasMessageContaining("5 MB");
    }

    @Test void rejectsHighlyCompressedDocxPayload() throws Exception {
        byte[] content;
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("types".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(new byte[1024 * 1024]);
            zip.closeEntry();
            zip.finish();
            content = bytes.toByteArray();
        }
        var storage = new FileSystemCvStorageAdapter(directory.toString(), 5 * 1024 * 1024);
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(content), "cv.docx"))
                .isInstanceOf(InvalidFile.class).hasMessageContaining("límites seguros");
    }
}
