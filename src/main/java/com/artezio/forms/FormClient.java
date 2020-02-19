package com.artezio.forms;

import com.artezio.bpm.resources.ResourceLoader;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface FormClient {
    String getFormWithData(String formKey, ObjectNode taskVariables);
    String getFormWithData(String formKey, ObjectNode taskVariables, ResourceLoader resourceLoader);
    String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode taskVariables);
    String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode taskVariables, ResourceLoader resourceLoader);
    boolean shouldProcessSubmission(String formKey, String submissionState, ResourceLoader resourceLoader);
    List<String> getFormVariableNames(String formKey);
    List<String> getFormVariableNames(String formKey, ResourceLoader resourceLoader);
}
