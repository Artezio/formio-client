package com.artezio.bpm.services;

import java.io.InputStream;
import java.util.List;

interface AbstractResourceLoader {

    InputStream getResource(String deploymentId, String resourceKey);

    List<String> listResources(String deploymentId, String initialPath);

}
