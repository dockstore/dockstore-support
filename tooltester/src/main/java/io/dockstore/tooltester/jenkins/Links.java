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
    @SerializedName("runs")
    @Expose
    private Runs runs;
    @SerializedName("log")
    @Expose
    private Self log;

    public Self getSelf() {
        return self;
    }

    public void setSelf(Self self) {
        this.self = self;
    }

    public Self getLog() {
        return log;
    }

    public Runs getRuns() {
        return runs;
    }

    public void setRuns(Runs runs) {
        this.runs = runs;
    }

}
