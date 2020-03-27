package com.artezio.forms.formio;

import com.artezio.forms.converters.FileConverter;
import com.fasterxml.jackson.databind.JsonNode;

class DefaultFileConverter extends FileConverter {

    @Override
    public JsonNode fromFormioFile(JsonNode formioFile) {
        return formioFile;
    }

    @Override
    protected int getSize(JsonNode file) {
        return getFormioFileSize(file);
    }

    @Override
    protected String getUrl(JsonNode file) {
        return getFormioFileUrl(file);
    }

    @Override
    protected String getOriginalName(JsonNode file) {
        return getFormioFileOriginalName(file);
    }

    @Override
    protected String getName(JsonNode file) {
        return getFormioFileName(file);
    }

    @Override
    protected String getMimeType(JsonNode file) {
        return getFormioFileMimeType(file);
    }

    @Override
    protected String getStorage(JsonNode file) {
        return getFormioFileStorage(file);
    }
}
