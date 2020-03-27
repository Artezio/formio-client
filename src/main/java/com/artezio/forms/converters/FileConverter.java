package com.artezio.forms.converters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

abstract public class FileConverter {

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Converts formio file to another form.
     *
     * @param formioFile Json object of a formio file
     * @return Json object of result file
     */
    abstract public JsonNode fromFormioFile(JsonNode formioFile);

    /**
     * Get size of a file.
     *
     * @param file Json object of the file
     * @return The file size
     */
    abstract protected int getSize(JsonNode file);

    /**
     * Get url of a file.
     *
     * @param file Json object of the file
     * @return Url to download the file
     */
    abstract protected String getUrl(JsonNode file);

    /**
     * Get name of a file.
     *
     * @param file Json object of the file
     * @return Name of the file
     */
    abstract protected String getOriginalName(JsonNode file);

    /**
     * Get unique name of a file (e.g. using GUID).
     *
     * @param file Json object of the file
     * @return Unique name of the file
     */
    abstract protected String getName(JsonNode file);

    /**
     * Get mime type of a file.
     *
     * @param file Json object of the file
     * @return Mime type of the file
     */
    abstract protected String getMimeType(JsonNode file);

    /**
     * Get storage type of a file.
     *
     * @param file Json object of the file
     * @return Storage type of the file
     */
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
