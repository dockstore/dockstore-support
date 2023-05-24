package io.dockstore.tooltester.blueOceanJsonObjects;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Json object part of the PipelineStepImpl json object
 */
public class LogActionImpl {
    @SerializedName("_links")
    @Expose
    private Links links;

    public Links getLinks() {
        return links;
    }

}
