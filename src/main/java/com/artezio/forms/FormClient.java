package com.artezio.forms;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FormClient {
    String getFormWithData(InputStream form, ObjectNode taskVariables, Map<String, InputStream> publicResources);
    String dryValidationAndCleanup(InputStream form, ObjectNode submittedVariables, ObjectNode taskVariables, Map<String, InputStream> publicResources);
    boolean shouldProcessSubmission(InputStream form, String submissionState);
    List<String> getFormVariableNames(InputStream form);
}
