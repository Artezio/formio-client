package com.artezio.forms;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FormClient {
    String getFormWithData(String deploymentId, String formPath, ObjectNode taskVariables);
    String dryValidationAndCleanup(String deploymentId, String formPath, ObjectNode submittedVariables, ObjectNode taskVariables);
    boolean shouldProcessSubmission(String deploymentId, String formPath, String submissionState);
    List<String> getFormVariableNames(String deploymentId, String formPath);
    Map<String, String> listResources(String deploymentId, String formKey);
    InputStream getResource(String deploymentId, String resourcePath);
}
