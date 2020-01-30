package com.artezio.forms.formio.exceptions;

public class FormioFileSchemeFormatException extends RuntimeException {

    public FormioFileSchemeFormatException(String jsonScheme) {
        super("Incompatible format of file field in json scheme: " + jsonScheme + ".");
    }

}
