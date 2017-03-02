package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 10/02/17
 */
public class JenkinsLog {

    private String nodeId;
    private String nodeStatus;
    private int length;
    private boolean hasMore;
    private String text;
    private String consoleUrl;

    public String getText() {
        return text;
    }

    public String getConsoleUrl() {
        return consoleUrl;
    }
}
