package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Links {

    @SerializedName("self")
    @Expose
    private Self self;
    @SerializedName("log")
    @Expose
    private Self log;

    public Self getSelf() {
        return self;
    }

    public Self getLog() {
        return log;
    }

}
