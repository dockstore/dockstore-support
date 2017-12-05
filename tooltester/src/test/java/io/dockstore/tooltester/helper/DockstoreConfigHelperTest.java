package io.dockstore.tooltester.helper;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 05/12/17
 */
public class DockstoreConfigHelperTest {
    @Test
    public void testCwltoolConfig() {
        final String url = "https://staging.dockstore.org:8443";
        String cwltoolConfig = DockstoreConfigHelper.getConfig(url, DockstoreConfigHelper.CWLRUNNER.CWLTOOL);
        assertEquals(cwltoolConfig, "token: test \\nserver-url: https://staging.dockstore.org:8443");
    }

    @Test
    public void testRabixConfig() {
        final String url = "https://staging.dockstore.org:8443";
        String rabixConfig = DockstoreConfigHelper.getConfig(url, DockstoreConfigHelper.CWLRUNNER.RABIX);
        assertEquals(rabixConfig, "token: test \\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: bunny");
    }
}
