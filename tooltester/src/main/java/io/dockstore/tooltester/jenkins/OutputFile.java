package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 15/02/17
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class OutputFile {
    @SerializedName("checksum")
    @Expose
    private String checksum;
    @SerializedName("size")
    @Expose
    private Integer size;

    public String getChecksum() {
        return checksum;
    }

    public Integer getSize() {
        return size;
    }
}
