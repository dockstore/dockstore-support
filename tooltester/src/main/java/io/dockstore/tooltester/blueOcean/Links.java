package io.dockstore.tooltester.blueOcean;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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
