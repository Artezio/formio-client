package com.artezio.bpm.services.integration;

import com.artezio.bpm.services.integration.cdi.DefaultImplementation;
import com.artezio.utils.Base64Utils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;

import javax.inject.Named;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;

@Named
@DefaultImplementation
public class Base64UrlFileStorage implements FileStorage {

    @Override
    public String store(InputStream dataStream) {
        try {
            byte[] data = IOUtils.toByteArray(dataStream);
            String base64encodedData = Base64.getMimeEncoder().encodeToString(data);
            String dataHeader = String.format("data:%s;base64,", getContentType(data));
            return dataHeader + base64encodedData;
        } catch (IOException e) {
            throw new RuntimeException("An error occured while serializing the file to base64 url components", e);
        }
    }

    @Override
    public InputStream retrieve(String id) {
        return Base64Utils.getData(id);
    }

    private String getContentType(byte[] data) {
        String guessedContentType = new Tika().detect(data);
        return Optional
                .ofNullable(guessedContentType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
