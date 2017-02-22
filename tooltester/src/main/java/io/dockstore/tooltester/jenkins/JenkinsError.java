package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 08/02/17
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class JenkinsError {
    @SerializedName("message")
    @Expose
    private String message;

    public String getMessage() {
        return message;
    }
}
