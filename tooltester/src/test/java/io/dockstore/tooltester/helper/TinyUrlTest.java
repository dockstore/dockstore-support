package io.dockstore.tooltester.helper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * @author gluu
 * @since 16/03/17
 */
public class TinyUrlTest {
    @Test
    public void getTinyUrl() throws Exception {
        String originalURL = "https://www.google.ca";
        String tinyUrl = TinyUrl.getTinyUrl(originalURL);
        assertTrue("https://tinyurl.com/d4gfaxc".equals(tinyUrl) || tinyUrl.equals(originalURL));
    }

}
