package io.dockstore.tooltester.client.cli;

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

    public String getId() {
        return id;
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

    public Long getEndTimeMillis() {
        return endTimeMillis;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public Long getQueueDurationMillis() {
        return queueDurationMillis;
    }

    public Long getPauseDurationMillis() {
        return pauseDurationMillis;
    }

    public Stage[] getStages() {
        return stages;
    }
}
