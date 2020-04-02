package com.artezio.forms.formio;

import com.artezio.forms.resources.ResourceLoader;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Named
public class DefaultResourceLoader implements ResourceLoader {

    private static final Pattern RESOURCE_KEY_PATTERN = Pattern.compile("(:?embedded:\\w*:)?(.+)");
    private static final String ROOT_DIRECTORY = "public";

    private ServletContext servletContext;

    @Inject
    public DefaultResourceLoader(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public InputStream getResource(String resourceKey) {
        resourceKey = getResourcePath(resourceKey);
        return servletContext.getResourceAsStream(ROOT_DIRECTORY + "/" + resourceKey);
    }

    @Override
    public List<String> listResourceNames() {
        return listResourceNames(ROOT_DIRECTORY).stream()
                .map(resourceName -> resourceName.substring((ROOT_DIRECTORY + "/").length()))
                .collect(Collectors.toList());
    }

    private List<String> listResourceNames(String resourcesDirectory) {
        try {
            String resourcePath = resourcesDirectory.startsWith("/") ? resourcesDirectory : "/" + resourcesDirectory;
            URL url = servletContext.getResource(resourcePath);
            if (url == null) return emptyList();
            File resource = new File(url.toURI());
            return Arrays.stream(resource.listFiles())
                    .flatMap(file -> {
                        String resourceName = resourcesDirectory + "/" + file.getName();
                        return file.isDirectory()
                                ? listResourceNames(resourceName).stream()
                                : Stream.of(resourceName);
                    })
                    .collect(Collectors.toList());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String getResourcePath(String resourceKey) {
        Matcher matcher = RESOURCE_KEY_PATTERN.matcher(resourceKey);
        matcher.matches();
        return matcher.group(2);
    }

}
