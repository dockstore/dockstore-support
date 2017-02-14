package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */
public class Stage {
    //@Checkstyle:off
    private JenkinsLink _links;
    //@Checkstyle:on
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
        return _links;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Long getStartTimeMillis() {
        return startTimeMillis;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

}
