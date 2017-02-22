package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Stage {

    @SerializedName("_links")
    @Expose
    private Links links;
    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("name")
    @Expose
    private String name;

    public Links getLinks() {
        return links;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

}
