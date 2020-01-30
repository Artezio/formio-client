package com.artezio.forms.formio;

import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.utils.Base64Utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;

@Named
public class FileAttributeConverter {

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper();

    private FileStorage fileStorage;

    @Inject
    public FileAttributeConverter(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public ObjectNode toFormioFile(ObjectNode camundaFileAttributes) {
        ObjectNode formioFile = JSON_MAPPER.createObjectNode();
        formioFile.set("type", new TextNode(camundaFileAttributes.get("mimeType").asText()));
        formioFile.set("name", new TextNode(camundaFileAttributes.get("filename").asText()));
        formioFile.set("originalName", new TextNode(camundaFileAttributes.get("filename").asText()));
        formioFile.set("url", new TextNode(camundaFileAttributes.get("url").asText()));
        formioFile.set("size", new IntNode(camundaFileAttributes.get("size").asInt()));
        formioFile.set("storage", new TextNode("url"));
        return formioFile;
    }

    public ObjectNode toCamundaFile(ObjectNode formioFileAttributes) {
        ObjectNode camunaFile = JSON_MAPPER.createObjectNode();
        String filename = Optional.ofNullable(formioFileAttributes.get("originalName"))
                .orElse(formioFileAttributes.get("name"))
                .asText();
        camunaFile.set("filename", new TextNode(filename));
        String url = formioFileAttributes.get("url").asText();
        if (Base64Utils.isBase64DataUrl(url)) {
            url = fileStorage.store(Base64Utils.getData(url));
        }
        camunaFile.set("size", new TextNode(formioFileAttributes.get("size").asText()));
        camunaFile.set("url", new TextNode(url));
        camunaFile.set("mimeType", new TextNode(formioFileAttributes.get("type").asText()));
        return camunaFile;
    }

}
