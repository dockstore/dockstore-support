package io.dockstore.tooltester.blueOcean;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class PipelineImpl {

    @SerializedName("latestRun")
    @Expose
    private LatestRun latestRun;

    public LatestRun getLatestRun() {
        return latestRun;
    }
}
