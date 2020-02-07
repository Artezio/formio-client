package com.artezio.bpm.services;

import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RepositoryService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import java.io.InputStream;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Named
public class ResourceLoader {

    private final static String EMBEDDED_APP_FORM_STORAGE_PROTOCOL = "embedded:app:";
    private final static String EMBEDDED_DEPLOYMENT_FORM_STORAGE_PROTOCOL = "embedded:deployment:";
    private final static Pattern RESOURCE_STORAGE_PROTOCOL_PATTERN = Pattern.compile("(embedded:\\w+:)?.+");
    private final static String PROCESS_ENGINE_NAME = System.getenv("PROCESS_ENGINE_NAME");

    @Inject
    private ServletContext servletContext;

    public InputStream loadResource(String deploymentId, String resourceKey) {
        String storageProtocol = identifyProtocol(resourceKey);
        return loadResource(deploymentId, resourceKey, storageProtocol);
    }

    public InputStream loadDependentResource(String deploymentId, String resourceKey, String masterResourceKey) {
        String storageProtocol = identifyProtocol(masterResourceKey);
        return loadResource(deploymentId, resourceKey, storageProtocol);
    }

    private InputStream loadResource(String deploymentId, String rootResourceKey, String storageProtocol) {
        return storageProtocol.equals(EMBEDDED_DEPLOYMENT_FORM_STORAGE_PROTOCOL)
                ? getRepositoryService().getResourceAsStream(deploymentId, rootResourceKey)
                : servletContext.getResourceAsStream(rootResourceKey);
    }

    private String identifyProtocol(String resourceName) {
        Matcher matcher = RESOURCE_STORAGE_PROTOCOL_PATTERN.matcher(resourceName);
        matcher.find();
        String formStorageProtocol = matcher.group(1);
        return !StringUtils.isNotEmpty(formStorageProtocol)
                ? formStorageProtocol
                : EMBEDDED_APP_FORM_STORAGE_PROTOCOL;
    }

    private RepositoryService getRepositoryService() {
        return getProcessEngine().getRepositoryService();
    }

    /**
     * Extracted from https://github.com/camunda/camunda-bpm-platform/blob/master/engine-rest/engine-rest/src/main/java/org/camunda/bpm/engine/rest/impl/application/ContainerManagedProcessEngineProvider.java
     * Changes are: added engine name
     */
    private ProcessEngine getProcessEngine() {
        String processEngineName = Optional.ofNullable(PROCESS_ENGINE_NAME).orElse(ProcessEngines.NAME_DEFAULT);
        ProcessEngine defaultProcessEngine = BpmPlatform.getProcessEngineService().getProcessEngine(processEngineName);
        return defaultProcessEngine != null
                ? defaultProcessEngine
                : ProcessEngines.getProcessEngine(processEngineName);
    }

}
