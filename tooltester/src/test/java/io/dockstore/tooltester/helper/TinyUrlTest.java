package io.dockstore.tooltester.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gluu
 * @since 16/03/17
 */
public class TinyUrlTest {
    @Test
    public void getTinyUrl() throws Exception {
        String originalURL = "https://www.google.ca";
        String tinyUrl = TinyUrl.getTinyUrl(originalURL);
        assertTrue(tinyUrl.equals("https://tinyurl.com/d4gfaxc") || tinyUrl.equals(originalURL));
    }

}
