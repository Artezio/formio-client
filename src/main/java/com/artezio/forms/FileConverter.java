package com.artezio.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

abstract public class FileConverter {

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper();

    abstract public JsonNode fromFormioFile(JsonNode formioFile);
    abstract protected int getSize(JsonNode file);
    abstract protected String getUrl(JsonNode file);
    abstract protected String getOriginalName(JsonNode file);
    abstract protected String getName(JsonNode file);
    abstract protected String getMimeType(JsonNode file);
    abstract protected String getStorage(JsonNode file);

    public JsonNode toFormioFile(JsonNode file) {
        ObjectNode formioFile = JSON_MAPPER.createObjectNode();
        formioFile.set("type", new TextNode(getMimeType(file)));
        formioFile.set("name", new TextNode(getName(file)));
        formioFile.set("originalName", new TextNode(getOriginalName(file)));
        formioFile.set("url", new TextNode(getUrl(file)));
        formioFile.set("size", new IntNode(getSize(file)));
        formioFile.set("storage", new TextNode(getStorage(file)));
        return formioFile;
    }

    protected int getFormioFileSize(JsonNode formioFile) {
        return formioFile.path("size").asInt();
    }
    protected String getFormioFileUrl(JsonNode formioFile) {
        return formioFile.path("url").asText();
    }
    protected String getFormioFileOriginalName(JsonNode formioFile) {
        return formioFile.path("originalName").asText();
    }
    protected String getFormioFileName(JsonNode formioFile) {
        return formioFile.path("name").asText();
    }
    protected String getFormioFileMimeType(JsonNode formioFile) {
        return formioFile.path("type").asText();
    }
    protected String getFormioFileStorage(JsonNode formioFile) {
        return formioFile.path("storage").asText();
    }

}
