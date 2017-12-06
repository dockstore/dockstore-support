package io.dockstore.tooltester.helper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 05/12/17
 */
public class DockstoreConfigHelperTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testCwltoolConfig() {
        final String url = "https://staging.dockstore.org:8443";
        String cwltoolConfig = DockstoreConfigHelper.getConfig(url, "cwltool");
        assertEquals(cwltoolConfig, "token: test \\nserver-url: https://staging.dockstore.org:8443");
    }

    @Test
    public void testRabixConfig() {
        final String url = "https://staging.dockstore.org:8443";
        String rabixConfig = DockstoreConfigHelper.getConfig(url, "bunny");
        assertEquals(rabixConfig, "token: test \\nserver-url: https://staging.dockstore.org:8443\\ncwlrunner: bunny");
    }

    @Test
    public void testPotatoConfig() {
        final String url = "https://staging.dockstore.org:8443";
        exit.expectSystemExitWithStatus(ExceptionHandler.CLIENT_ERROR);
        String rabixConfig = DockstoreConfigHelper.getConfig(url, "potato");
    }
}
