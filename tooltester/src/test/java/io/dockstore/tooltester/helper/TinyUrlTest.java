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
        String tinyUrl = TinyUrl.getTinyUrl("https://www.google.ca");
        assertTrue(tinyUrl.equals("http://tinyurl.com/d4gfaxc"));
    }

}
