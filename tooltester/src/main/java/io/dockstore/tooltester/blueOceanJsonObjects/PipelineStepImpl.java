package io.dockstore.tooltester.blueOceanJsonObjects;

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * @author gluu
 * @since 22/02/17
 */

/**
 * Json object returned when grabbing pipeline steps from Blue Ocean REST API
 */
public class PipelineStepImpl {
    @SerializedName("actions")
    @Expose
    private List<LogActionImpl> actions = null;

    @SerializedName("durationInMillis")
    @Expose
    private Long durationInMillis;

    @SerializedName("result")
    @Expose
    private String result;

    public List<LogActionImpl> getActions() {
        return actions;
    }

    public Long getDurationInMillis() {
        return durationInMillis;
    }

    public String getResult() {
        return result;
    }

}
