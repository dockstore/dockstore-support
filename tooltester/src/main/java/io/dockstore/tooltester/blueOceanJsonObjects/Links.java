package io.dockstore.tooltester.blueOceanJsonObjects;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Json object part of LogActionImpl, PipelineNodeImpl, and LatestRun Json objects
 */
public class Links {

    @SerializedName("self")
    @Expose
    private Link self;
    @SerializedName("nodes")
    @Expose
    private Link nodes;
    @SerializedName("steps")
    @Expose
    private Link steps;

    public Link getNodes() {
        return nodes;
    }

    public Link getSteps() {
        return steps;
    }

    public Link getSelf() {
        return self;
    }

}
