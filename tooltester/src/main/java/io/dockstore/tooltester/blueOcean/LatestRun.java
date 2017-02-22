package io.dockstore.tooltester.blueOcean;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class LatestRun {
    @SerializedName("_links")
    @Expose
    private Links links;

    public Links getLinks() {
        return links;
    }

}
