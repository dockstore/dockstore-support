package io.dockstore.tooltester.blueOcean;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Link {
    @SerializedName("href")
    @Expose
    private String href;

    public String getHref() {
        return href;
    }

}
