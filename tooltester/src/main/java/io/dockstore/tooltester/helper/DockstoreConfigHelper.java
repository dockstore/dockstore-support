package io.dockstore.tooltester.helper;

/**
 * @author gluu
 * @since 05/12/17
 */
public class DockstoreConfigHelper {
    public enum CWLRUNNER {
        CWLTOOL,
        RABIX
    }
    private static String baseConfig(String url) {
        return "token: test \\nserver-url: " + url;
    }
    private static String cwltoolConfig(String url) {
        return baseConfig(url);
    }

    private static String rabixConfig(String url) {
        return baseConfig(url) + "\\ncwlrunner: bunny";
    }

    public static String getConfig(String url, CWLRUNNER runner) {
        switch (runner) {
        case CWLTOOL:
            return cwltoolConfig(url);
        case RABIX:
            return rabixConfig(url);
        default:
            ExceptionHandler.errorMessage("Unknown runner.", ExceptionHandler.CLIENT_ERROR);
        }
        return null;
    }
}
