package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class JenkinsPipeline {

    @SerializedName("_links")
    @Expose
    private Links links;
    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("status")
    @Expose
    private String status;
    @SerializedName("startTimeMillis")
    @Expose
    private Long startTimeMillis;
    @SerializedName("endTimeMillis")
    @Expose
    private Long endTimeMillis;
    @SerializedName("durationMillis")
    @Expose
    private Long durationMillis;
    @SerializedName("queueDurationMillis")
    @Expose
    private Long queueDurationMillis;
    @SerializedName("pauseDurationMillis")
    @Expose
    private Long pauseDurationMillis;
    @SerializedName("stages")
    @Expose
    private List<Stage> stages = null;

    public Links getLinks() {
        return links;
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

    public List<Stage> getStages() {
        return stages;
    }

}
