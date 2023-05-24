package io.dockstore.tooltester.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;

/**
 * @author gluu
 * @since 15/03/17
 */

public class TinyUrl {
    private static final String TINY_URL = "http://tinyurl.com/api-create.php?url=";

    public static String getTinyUrl(String longUrl) {
        String tinyUrlLookup = TINY_URL + longUrl;
        InputStream in = null;
        try {
            in = new URL(tinyUrlLookup).openStream();
            String tinyURL = IOUtils.toString(in);
            if (tinyURL.equals("Error")) {
                return longUrl;
            } else {
                return tinyURL;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
}
