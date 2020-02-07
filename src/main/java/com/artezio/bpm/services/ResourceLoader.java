package com.artezio.bpm.services;

import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RepositoryService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import java.io.InputStream;
import java.util.Optional;

@Named
public class ResourceLoader {

    private final static String EMBEDDED_APP_FORM_STORAGE_PROTOCOL = "embedded:app:";
    private final static String EMBEDDED_DEPLOYMENT_FORM_STORAGE_PROTOCOL = "embedded:deployment:";
    private final static String DEFAULT_FORM_STORAGE_PROTOCOL = "";
    private final static String PROCESS_ENGINE_NAME = System.getenv("PROCESS_ENGINE_NAME");

    @Inject
    private ServletContext servletContext;

    public InputStream loadResource(ResourceId resourceId, String storageProtocol) {
        return storageProtocol.equals(EMBEDDED_DEPLOYMENT_FORM_STORAGE_PROTOCOL)
                ? getRepositoryService().getResourceAsStream(resourceId.deploymentId, resourceId.resourcePath)
                : servletContext.getResourceAsStream(resourceId.resourcePath);
    }

    public String identifyProtocol(String resourceName) {
        String protocolName;
        if (resourceName.startsWith(EMBEDDED_DEPLOYMENT_FORM_STORAGE_PROTOCOL)) {
            protocolName = EMBEDDED_DEPLOYMENT_FORM_STORAGE_PROTOCOL;
        } else if (resourceName.startsWith(EMBEDDED_APP_FORM_STORAGE_PROTOCOL)) {
            protocolName = EMBEDDED_APP_FORM_STORAGE_PROTOCOL;
        } else {
            protocolName = DEFAULT_FORM_STORAGE_PROTOCOL;
        }
        return protocolName;
    }

    public String transformToResourcePath(String resourceName, String resourceFileExtension) {
        return resourceName.replace(identifyProtocol(resourceName), "").concat(resourceFileExtension);
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

    public static class ResourceId {
        final String deploymentId;
        final String resourcePath;

        public ResourceId(String deploymentId, String resourcePath) {
            this.deploymentId = deploymentId;
            this.resourcePath = resourcePath;
        }
    }

}
