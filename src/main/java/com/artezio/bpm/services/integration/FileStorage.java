package com.artezio.bpm.services.integration;

import java.io.InputStream;

public interface FileStorage {
    String store(InputStream data);
    InputStream retrieve(String id);
}
