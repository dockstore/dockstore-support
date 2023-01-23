package io.dockstore.tooltester.helper;

import org.junit.jupiter.api.Assertions;
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
        Assertions.assertTrue(tinyUrl.equals("https://tinyurl.com/d4gfaxc") || tinyUrl.equals(originalURL));
    }

}
