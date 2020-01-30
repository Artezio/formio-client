package com.artezio.forms.formio.exceptions;

public class FormValidationException extends RuntimeException {
    public FormValidationException(Exception cause) {
        super("Error while form validation.", cause);
    }
}
