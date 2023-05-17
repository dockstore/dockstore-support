package io.dockstore.tooltester.blueOceanJsonObjects;

/**
 * @author gluu
 * @since 22/02/17
 */
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Json object returned by JenkinsHelper's getBlueOceanJenkinsPipeline function
 */
public class PipelineNodeImpl {

    @SerializedName("_links")
    @Expose
    private Links links;
    @SerializedName("displayName")
    @Expose
    private String displayName;
    @SerializedName("durationInMillis")
    @Expose
    private Long durationInMillis;
    @SerializedName("result")
    @Expose
    private String result;
    @SerializedName("startTime")
    @Expose
    private String startTime;
    @SerializedName("state")
    @Expose
    private String state;

    public Links getLinks() {
        return links;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Long getDurationInMillis() {
        return durationInMillis;
    }

    public String getResult() {
        return result;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getState() {
        return state;
    }

}
