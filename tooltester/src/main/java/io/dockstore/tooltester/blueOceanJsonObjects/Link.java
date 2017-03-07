package io.dockstore.tooltester.blueOceanJsonObjects;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Json object part of the Links json object
 */
public class Link {
    @SerializedName("href")
    @Expose
    private String href;

    public String getHref() {
        return href;
    }

}
