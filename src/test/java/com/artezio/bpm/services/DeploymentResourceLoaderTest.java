package com.artezio.bpm.services;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

public class DeploymentResourceLoaderTest {

    private DeploymentResourceLoader loader = new DeploymentResourceLoader();

    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    private String getLatestDeploymentId() {
        return processEngineRule.getRepositoryService()
                .createDeploymentQuery()
                .list()
                .stream()
                .sorted((d1, d2) -> d1.getDeploymentTime().compareTo(d2.getDeploymentTime()))
                .findFirst().get().getId();
    }

    @Test
    @Deployment(resources = {"forms/formWithState.json"})
    public void testGetResource() throws IOException {
        InputStream actual = loader.getResource(getLatestDeploymentId(), "forms/formWithState.json");

        assertNotNull(actual);
        assertTrue(actual.available() > 0);
    }

    @Test
    @Deployment(resources = {"forms/formWithState.json", "forms/formWithSubform.json"})
    public void testListResources() {
        List<String> actuals = loader.listResources(getLatestDeploymentId(), "forms");

        assertTrue(actuals.size() == 2);
        assertTrue(actuals.contains("forms/formWithState.json"));
        assertTrue(actuals.contains("forms/formWithSubform.json"));
    }

}
