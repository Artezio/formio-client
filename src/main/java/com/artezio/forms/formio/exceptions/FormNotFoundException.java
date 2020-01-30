package com.artezio.forms.formio.exceptions;

public class FormNotFoundException extends RuntimeException {
    public FormNotFoundException(String formPath) {
        super(String.format("Form '%s' not found", formPath));
    }
}
