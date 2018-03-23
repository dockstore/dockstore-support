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
        return baseConfig(url) + "\\ncwlrunner: cwltool";
    }
    private static String toilConfig(String url) { return baseConfig(url) + "\\ncwlrunner: cwlrunner"; }
    private static String cwlrunnerConfig(String url) { return baseConfig(url) + "\\ncwlrunner: toil"; }
    private static String rabixConfig(String url) {
        return baseConfig(url) + "\\ncwlrunner: bunny";
    }

    public static String getConfig(String url, String runner) {
        switch (runner) {
        case "toil":
            return toilConfig(url);
        case "cwlrunner":
            return cwlrunnerConfig(url);
        case "cromwell":
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
