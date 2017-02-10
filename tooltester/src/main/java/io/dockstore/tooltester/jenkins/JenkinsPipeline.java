package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */
public class JenkinsPipeline {
    private String id;
    private String name;
    private String status;
    private Long startTimeMillis;
    private Long endTimeMillis;
    private Long durationMillis;
    private Long queueDurationMillis;
    private Long pauseDurationMillis;
    private Stage[] stages;

    public JenkinsPipeline() {
    }

    public String getStatus() {
        return status;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public Stage[] getStages() {
        return stages;
    }
}
