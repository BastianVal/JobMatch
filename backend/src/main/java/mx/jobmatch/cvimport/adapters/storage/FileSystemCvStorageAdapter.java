package mx.jobmatch.cvimport.adapters.storage;

import mx.jobmatch.cvimport.application.CvStoragePort;
import mx.jobmatch.cvimport.application.CvImportExceptions.InvalidFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class FileSystemCvStorageAdapter implements CvStoragePort {
    private final Path root;
    private final long maxBytes;

    public FileSystemCvStorageAdapter(@Value("${jobmatch.cv.storage-root}") String root,
                                      @Value("${jobmatch.cv.max-file-bytes}") long maxBytes) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @Override
    public StoredFile store(InputStream input, String originalFilename) {
        String extension = extension(originalFilename);
        if (!extension.equals("pdf") && !extension.equals("docx"))
            throw new InvalidFile("Solo se aceptan archivos PDF o DOCX.");
        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = root.resolve("." + UUID.randomUUID() + ".upload");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream source = input; OutputStream target = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = source.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    size += read;
                    if (size > maxBytes) throw new InvalidFile("El CV supera el límite de 5 MB.");
                    digest.update(buffer, 0, read);
                    target.write(buffer, 0, read);
                }
            }
            if (size == 0) throw new InvalidFile("El archivo está vacío.");
            String mediaType = validateType(temporary, extension);
            String key = UUID.randomUUID() + ".bin";
            Path destination = checked(key);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination); }
            return new StoredFile(key, mediaType, HexFormat.of().formatHex(digest.digest()), size);
        } catch (InvalidFile failure) {
            deleteQuietly(temporary);
            throw failure;
        } catch (Exception failure) {
            deleteQuietly(temporary);
            throw new InvalidFile("No fue posible almacenar el CV.");
        }
    }

    @Override public Path resolve(String storageKey) {
        Path path = checked(storageKey);
        if (!Files.isRegularFile(path)) throw new InvalidFile("El archivo del CV no está disponible.");
        return path;
    }

    @Override public void delete(String storageKey) { deleteQuietly(checked(storageKey)); }

    private String validateType(Path file, String extension) throws Exception {
        byte[] prefix = Files.readAllBytes(file);
        boolean pdf = prefix.length >= 5 && prefix[0]=='%' && prefix[1]=='P' && prefix[2]=='D' && prefix[3]=='F' && prefix[4]=='-';
        if (extension.equals("pdf") && pdf) return "application/pdf";
        if (extension.equals("docx") && prefix.length >= 4 && prefix[0]=='P' && prefix[1]=='K') {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                int entries = 0;
                long expandedBytes = 0;
                var contents = zip.entries();
                while (contents.hasMoreElements()) {
                    ZipEntry entry = contents.nextElement();
                    long size = entry.getSize();
                    long compressed = entry.getCompressedSize();
                    if (++entries > 1_000 || size < 0 || size > 10L * 1024 * 1024)
                        throw new InvalidFile("El DOCX excede los límites seguros de contenido.");
                    expandedBytes += size;
                    if (expandedBytes > 20L * 1024 * 1024 || (compressed > 0 && size / compressed > 100))
                        throw new InvalidFile("El DOCX excede los límites seguros de contenido.");
                }
                if (zip.getEntry("[Content_Types].xml") != null && zip.getEntry("word/document.xml") != null)
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
        }
        throw new InvalidFile("El contenido no coincide con la extensión declarada.");
    }

    private Path checked(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.bin")) throw new InvalidFile("Referencia de archivo inválida.");
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) throw new InvalidFile("Referencia de archivo inválida.");
        return path;
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (Exception ignored) {}
    }
}
