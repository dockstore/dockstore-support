package io.dockstore.tooltester.jenkins;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * @author gluu
 * @since 10/02/17
 */
public class StageFlowNode {
    @SerializedName("_links")
    @Expose
    private Links links;
    private String status;

    public String getStatus() {
        return status;
    }

    public Links getLinks() {
        return links;
    }
}
