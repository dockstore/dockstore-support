package io.dockstore.tooltester.helper;

/**
 * @author gluu
 * @since 05/12/17
 */
public class DockstoreConfigHelper {
    private static String baseConfig(String url) {
        return "token: test \\nserver-url: " + url;
    }
    private static String cwltoolConfig(String url) {
        return baseConfig(url);
    }

    private static String rabixConfig(String url) {
        return baseConfig(url) + "\\ncwlrunner: bunny";
    }

    public static String getConfig(String url, String runner) {
        switch (runner) {
        case "cwltool":
            return cwltoolConfig(url);
        case "bunny":
            return rabixConfig(url);
        default:
            ExceptionHandler.errorMessage("Unknown runner.", ExceptionHandler.CLIENT_ERROR);
        }
        return cwltoolConfig(url);
    }
}
