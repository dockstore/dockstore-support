package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Stage {

    @SerializedName("_links")
    @Expose
    private Links links;
    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("execNode")
    @Expose
    private String execNode;
    @SerializedName("status")
    @Expose
    private String status;
    @SerializedName("error")
    @Expose
    private Error error;
    @SerializedName("startTimeMillis")
    @Expose
    private Long startTimeMillis;
    @SerializedName("durationMillis")
    @Expose
    private Long durationMillis;
    @SerializedName("pauseDurationMillis")
    @Expose
    private Long pauseDurationMillis;

    public Links getLinks() {
        return links;
    }

    public void setLinks(Links links) {
        this.links = links;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExecNode() {
        return execNode;
    }

    public void setExecNode(String execNode) {
        this.execNode = execNode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public Long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(Long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public Long getPauseDurationMillis() {
        return pauseDurationMillis;
    }

    public void setPauseDurationMillis(Long pauseDurationMillis) {
        this.pauseDurationMillis = pauseDurationMillis;
    }

}
