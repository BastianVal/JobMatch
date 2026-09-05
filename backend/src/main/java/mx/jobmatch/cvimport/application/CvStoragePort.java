package mx.jobmatch.cvimport.application;

import java.io.InputStream;
import java.nio.file.Path;

public interface CvStoragePort {
    StoredFile store(InputStream input, String originalFilename);
    Path resolve(String storageKey);
    void delete(String storageKey);

    record StoredFile(String storageKey, String mediaType, String sha256, long byteSize) {}
}
