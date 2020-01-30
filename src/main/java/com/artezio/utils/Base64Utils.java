package com.artezio.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

public class Base64Utils {

    public static final String ENCODING_PREFIX = "base64,";

    public static InputStream getData(String base64DataUrl) {
        String encodingPrefix = "base64,";
        int contentStartIndex = base64DataUrl.indexOf(encodingPrefix) + encodingPrefix.length();
        String data = base64DataUrl.substring(contentStartIndex);
        return new ByteArrayInputStream(Base64.getMimeDecoder().decode(data));
    }

    public static boolean isBase64DataUrl(String url) {
        String dataHeader = "data:";
        return url.contains(dataHeader) && url.contains(ENCODING_PREFIX);
    }

}
