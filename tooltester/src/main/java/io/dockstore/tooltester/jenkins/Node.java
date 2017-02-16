package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 16/02/17
 */

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Node {

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
    private JenkinsError error;
    @SerializedName("startTimeMillis")
    @Expose
    private Long startTimeMillis;
    @SerializedName("durationMillis")
    @Expose
    private Long durationMillis;
    @SerializedName("pauseDurationMillis")
    @Expose
    private Long pauseDurationMillis;
    @SerializedName("stageFlowNodes")
    @Expose
    private List<StageFlowNode> stageFlowNodes = null;

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

    public JenkinsError getError() {
        return error;
    }

    public void setError(JenkinsError error) {
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

    public List<StageFlowNode> getStageFlowNodes() {
        return stageFlowNodes;
    }

    public void setStageFlowNodes(List<StageFlowNode> stageFlowNodes) {
        this.stageFlowNodes = stageFlowNodes;
    }

}

