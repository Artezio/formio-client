package com.artezio.bpm.services;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;

@Named
public class AppResourceLoader implements AbstractResourceLoader {

    private final static String PROCESS_ENGINE_NAME = System.getenv("PROCESS_ENGINE_NAME");

    @Inject
    private ServletContext servletContext;

    @Override
    public InputStream getResource(String deploymentId, String resourceKey) {
        return servletContext.getResourceAsStream(resourceKey);
    }

    @Override
    public List<String> listResources(String deploymentId, String initialPath) {
        try {
            String resourcePath = (initialPath.startsWith("/") ?  initialPath : "/" + initialPath);
            URL url = servletContext.getResource(resourcePath);
            if (url == null) return Collections.emptyList();
            
            try {
                System.out.println("!!!! URL = " + url.toURI().toString());
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            File resource = new File(url.getPath());
            
            System.out.println("File = " + resource.getAbsolutePath());
            
            return Arrays.stream(resource.listFiles())
                    .flatMap(file -> {
                        String resourceName = initialPath + "/" + file.getName();
                        return file.isDirectory()
                                ? listResources(deploymentId, resourceName).stream()
                                : Arrays.asList(resourceName).stream();
                    })
                    .collect(Collectors.toList());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

}
