package com.artezio.forms;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.List;

public interface FormClient {
    String getFormWithData(String deploymentId, String formPath, ObjectNode taskVariables);
    String dryValidationAndCleanup(String deploymentId, String formPath, ObjectNode submittedVariables, ObjectNode taskVariables);
    boolean shouldProcessSubmission(String deploymentId, String formPath, String submissionState);
    List<String> getFormVariableNames(String deploymentId, String formPath);
    List<String> listCustomComponents(String deploymentId, String formPath);
    InputStream getCustomComponent(String deploymentId, String componentName);
}
