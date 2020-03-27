package com.artezio.forms.formio;

import com.artezio.forms.storages.FileStorage;
import com.artezio.forms.storages.FileStorageEntity;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class FormioBase64FileStorage implements FileStorage {

    @Override
    public void store(FileStorageEntity fileStorageEntity) {}

    @Override
    public FileStorageEntity retrieve(String id) {
        return new FormioFileToFileStorageEntityAdapter(id, JsonNodeFactory.instance.objectNode());
    }

    @Override
    public String getDownloadUrlPrefix() {
        return "";
    }

}
