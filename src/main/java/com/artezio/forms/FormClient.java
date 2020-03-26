package com.artezio.forms;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface FormClient {
    String getFormWithData(String formKey, ObjectNode currentVariables);
    String getFormWithData(String formKey, ObjectNode currentVariables, FileStorage fileStorage);
    String getFormWithData(String formKey, ObjectNode currentVariables, ResourceLoader resourceLoader);
    String getFormWithData(String formKey, ObjectNode currentVariables, ResourceLoader resourceLoader, FileStorage fileStorage);
    String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables);
    String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables, FileStorage fileStorage);
    String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables, ResourceLoader resourceLoader);
    String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables, ResourceLoader resourceLoader, FileStorage fileStorage);
    boolean shouldProcessSubmission(String formKey, String submissionState);
    boolean shouldProcessSubmission(String formKey, String submissionState, ResourceLoader resourceLoader);
    List<String> getRootFormFieldNames(String formKey);
    List<String> getRootFormFieldNames(String formKey, ResourceLoader resourceLoader);
    List<String> getFormFieldPaths(String formKey);
    List<String> getFormFieldPaths(String formKey, ResourceLoader resourceLoader);
}
