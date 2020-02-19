package com.artezio.bpm.resources;

import java.io.InputStream;
import java.util.List;

public interface ResourceLoader {
    InputStream getResource(String resourceKey);
    List<String> listResourceNames(String initialPath);
}
