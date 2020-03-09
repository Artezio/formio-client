package com.artezio.forms.formio.exceptions;

public class NodeJsException extends RuntimeException {
    public NodeJsException(String message, Throwable cause) {
        super(message, cause);
    }
    public NodeJsException(String message) {
        super(message);
    }
}
