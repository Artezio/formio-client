package com.artezio.forms.formio;

import com.artezio.forms.FileStorageEntity;
import com.artezio.forms.formio.exceptions.DataUrlSchemeIncorrectFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormioFileToFileStorageEntityAdapter implements FileStorageEntity {

    private static final Pattern BASE64_DATA_IN_URL_PATTERN = Pattern.compile("^data:.*?;base64,(.+)$");

    private String id;
    private ObjectNode formioFile;

    public FormioFileToFileStorageEntityAdapter(String id, JsonNode formioFile) {
        this.id = id;
        this.formioFile = (ObjectNode) formioFile;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getMimeType() {
        return formioFile.get("type").asText();
    }

    @Override
    public InputStream getContent() {
        return getContent(formioFile);
    }

    @Override
    public String getName() {
        return formioFile.get("originalName").asText();
    }

    @Override
    public String getUrl() {
        return formioFile.get("url").asText();
    }

    @Override
    public String getStorage() {
        return formioFile.get("storage").asText();
    }

    @Override
    public void setMimeType(String mimeType) {
        formioFile.set("type", new TextNode(mimeType));
    }

    @Override
    public void setContent(InputStream content) {
    }

    @Override
    public void setName(String name) {
        formioFile.set("originalName", new TextNode(name));
    }

    @Override
    public void setUrl(String url) {
        formioFile.set("url", new TextNode(url));
    }

    @Override
    public void setStorage(String storage) {
        formioFile.set("storage", new TextNode(storage));
    }

    private InputStream getContent(JsonNode file) {
        String base64EncodedDataInUrl = file.get("url").asText();
        String encodedContent = extractContent(base64EncodedDataInUrl);
        byte[] decodedContent = Base64.getDecoder().decode(encodedContent);
        return new ByteArrayInputStream(decodedContent);
    }

    private String extractContent(String base64EncodedDataInUrl) {
        try {
            Matcher matcher = BASE64_DATA_IN_URL_PATTERN.matcher(base64EncodedDataInUrl);
            if (!matcher.matches())
                throw new DataUrlSchemeIncorrectFormat("Expected 'data:[<mime type>];base64,<data>'");
            return matcher.group(1);
        } catch (IllegalStateException ex) {
            throw new DataUrlSchemeIncorrectFormat("Data segment is absent");
        }
    }

}
