package io.dockstore.tooltester.blueOceanJsonObjects;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Json object returned when grabbing pipeline from Blue Ocean REST API
 */
public class PipelineImpl {

    @SerializedName("latestRun")
    @Expose
    private LatestRun latestRun;

    public LatestRun getLatestRun() {
        return latestRun;
    }
}
