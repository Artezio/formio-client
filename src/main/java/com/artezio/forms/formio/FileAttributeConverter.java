package com.artezio.forms.formio;

import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.utils.Base64Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class FileAttributeConverter {

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper();

    private FileStorage fileStorage;

    @Inject
    public FileAttributeConverter(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public JsonNode toFormioFile(JsonNode file) {
        ObjectNode formioFile = JSON_MAPPER.createObjectNode();
        formioFile.set("type", new TextNode(file.path("mimeType").asText()));
        formioFile.set("name", new TextNode(file.path("filename").asText()));
        formioFile.set("originalName", new TextNode(file.path("filename").asText()));
        formioFile.set("url", new TextNode(file.path("url").asText()));
        formioFile.set("size", new IntNode(file.path("size").asInt()));
        formioFile.set("storage", new TextNode("base64"));
        return formioFile;
    }

    public JsonNode fromFormioFile(JsonNode formiofile) {
        ObjectNode file = JSON_MAPPER.createObjectNode();
        JsonNode filename = formiofile.has("originalName")
                ? formiofile.get("originalName")
                : new TextNode(formiofile.path("name").asText());
        file.set("filename", filename);
        String url = formiofile.path("url").asText();
        if (Base64Utils.isBase64DataUrl(url)) {
            url = fileStorage.store(Base64Utils.getData(url));
        }
        file.set("url", new TextNode(url));
        file.set("size", new IntNode(formiofile.path("size").asInt()));
        file.set("mimeType", new TextNode(formiofile.path("type").asText()));
        return file;
    }

}
