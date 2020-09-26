package io.dockstore.toolbackup.client.cli;

import java.util.List;

public class DockerHubImageDetail {
    private final String imagePath;
    private final String imageTag;
    private final String selfTrsID;
    private Long imageSize;
    private List<String> trsIdsRepeatingImage;

    private String dataToWrite;
    public boolean failedToRetrieve;

    DockerHubImageDetail(String imagePath, String imageTag, String selfTrsID, List<String> trsIdsRepeatingImage) {
        this.imagePath = imagePath;
        this.imageTag = imageTag;
        this.selfTrsID = selfTrsID;
        this.trsIdsRepeatingImage = trsIdsRepeatingImage;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getImageTag() {
        return imageTag;
    }

    public Long getImageSize() {
        return imageSize;
    }

    public String getSelfTrsID() {
        return selfTrsID;
    }

    public List<String> getTrsIdsRepeatingImage() {
        return trsIdsRepeatingImage;
    }

    public String getDataToWrite() {
        return dataToWrite;
    }

    public Boolean getRetrieveStatus() {
        return failedToRetrieve;
    }

    public void appendToRepetitionList(String trsID) {
        getTrsIdsRepeatingImage().add(trsID);
    }

    public void setImageSize(Long size) {
        imageSize = size;
    }

    public void setData(String data, Boolean failedRetrieveStatus) {
        dataToWrite = data;
        failedToRetrieve = failedRetrieveStatus;
    }
}
