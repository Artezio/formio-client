package com.artezio.forms;

public interface FileStorage {
    void store(FileStorageEntity fileStorageEntity);
    FileStorageEntity retrieve(String id);
    String getDownloadUrlPrefix();
}
