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
    @SerializedName("error")
    @Expose
    private JenkinsError error;
    @SerializedName("stageFlowNodes")
    @Expose
    private List<StageFlowNode> stageFlowNodes = null;

    public Links getLinks() {
        return links;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JenkinsError getError() {
        return error;
    }

    public List<StageFlowNode> getStageFlowNodes() {
        return stageFlowNodes;
    }

}

