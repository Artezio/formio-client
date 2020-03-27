package com.artezio.forms.storages.exceptions;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entityId) {
        super(String.format("Entity with id = %s is not found in file storage", entityId));
    }
}
