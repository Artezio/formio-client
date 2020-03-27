package com.artezio.forms.storages;

import java.io.InputStream;

public interface FileStorageEntity {
    String getId();
    String getMimeType();
    InputStream getContent();
    String getName();
    String getUrl();
    String getStorage();
    void setMimeType(String mimeType);
    void setContent(InputStream content);
    void setName(String name);
    void setUrl(String url);
    void setStorage(String storage);
}
