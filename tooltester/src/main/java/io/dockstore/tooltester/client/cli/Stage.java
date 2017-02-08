package io.dockstore.tooltester.client.cli;

/**
 * @author gluu
 * @since 08/02/17
 */
public class Stage {
    private JenkinsLink links;
    private String id;
    private String name;
    private String execNode;
    private String status;
    private JenkinsError error;
    private Long startTimeMillis;
    private Long durationMillis;
    private Long pauseDurationMillis;

    public Stage() {
    }

    public JenkinsLink getLinks() {
        return links;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getExecNode() {
        return execNode;
    }

    public String getStatus() {
        return status;
    }

    public JenkinsError getError() {
        return error;
    }

    public Long getStartTimeMillis() {
        return startTimeMillis;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public Long getPauseDurationMillis() {
        return pauseDurationMillis;
    }
}
