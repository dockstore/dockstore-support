package io.dockstore.tooltester.blueOcean;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class LogActionImpl {
    @SerializedName("_links")
    @Expose
    private Links links;

    public Links getLinks() {
        return links;
    }

}
