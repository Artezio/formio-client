package com.artezio.bpm.services;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ResourceLoader {

    public final static String APP_PROTOCOL = "embedded:app:";
    public final static String DEPLOYMENT_PROTOCOL = "embedded:deployment:";
    private final static Pattern RESOURCE_KEY_PATTERN = Pattern.compile("(embedded:\\w+:)?(.+)");

    @Inject
    private AppResourceLoader appResourceLoader;
    @Inject
    private DeploymentResourceLoader deploymentResourceLoader;

    @SuppressWarnings("serial")
    public String getProtocol(String resourceKey) {
        Matcher matcher = RESOURCE_KEY_PATTERN.matcher(resourceKey);
        return matcher.matches() && DEPLOYMENT_PROTOCOL.equals(matcher.group(1))
                ? DEPLOYMENT_PROTOCOL
                : APP_PROTOCOL;
    }

    public InputStream getResource(String deploymentId, String resourceKey) {
        String protocol = getProtocol(resourceKey);
        return getResource(deploymentId, protocol, resourceKey);
    }

    public InputStream getResource(String deploymentId, String protocol, String resourceKey) {
        String formPath = getFormPath(resourceKey);
        return getLoader(protocol)
                .getResource(deploymentId, formPath);
    }

    protected String getFormPath(String resourceKey) {
        Matcher matcher = RESOURCE_KEY_PATTERN.matcher(resourceKey);
        matcher.matches();
        return matcher.group(2);
    }

    public List<String> listResources(String deploymentId, String protocol, String initialPath) {
        return getLoader(protocol)
                .listResources(deploymentId, initialPath);
    }

    private AbstractResourceLoader getLoader(String protocol) {
        return DEPLOYMENT_PROTOCOL.equals(protocol)
                ? deploymentResourceLoader
                : appResourceLoader;
    }

}