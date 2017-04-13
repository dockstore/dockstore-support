package io.dockstore.tooltester.helper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * @author gluu
 * @since 15/03/17
 */

public class TinyUrl {
    private static final String TINY_URL = "http://tinyurl.com/api-create.php?url=";

    public static String getTinyUrl(String longUrl) {
        String tinyUrlLookup = TINY_URL + longUrl;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(tinyUrlLookup).openStream(), "UTF-8")))
        {
            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return longUrl;
        }
    }
}
