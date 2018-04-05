package io.dockstore.tooltester.helper;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 16/03/17
 */
public class TinyUrlTest {
    @Test
    public void getTinyUrl() throws Exception {
        String originalURL = "https://www.google.ca";
        String tinyUrl = TinyUrl.getTinyUrl(originalURL);
        assertTrue(tinyUrl.equals("http://tinyurl.com/d4gfaxc") || tinyUrl.equals(originalURL));
    }

}
