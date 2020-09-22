package io.dockstore.toolbackup.client.cli;

import java.util.List;

public class DockerHubImageDetail {
    private final String imagePath;
    private final String imageTag;
    private final String selfTrsID;
    private List<String> trsIdsRepeatingImage;

    private String dataToWrite;
    public boolean failedToRetrieve;

    DockerHubImageDetail(String imagePath, String imageTag, String selfTrsID, List<String> trsIdsRepeatingImage) {
        this.imagePath = imagePath;
        this.imageTag = imageTag;
        this.selfTrsID = selfTrsID;
        this.trsIdsRepeatingImage = trsIdsRepeatingImage;
    }

    public String getImagePath() { return this.imagePath; }

    public String getImageTag() { return this.imageTag; }

    public String getSelfTrsID() { return this.selfTrsID; }

    public List<String> getTrsIdsRepeatingImage() { return this.trsIdsRepeatingImage; }

    public String getDataToWrite() { return this.dataToWrite; }

    public Boolean getRetrieveStatus() { return this.failedToRetrieve; }

    public void appendToRepetitionList(String trsID) {
        getTrsIdsRepeatingImage().add(trsID);
    }

    public void setData(String data, Boolean failedToRetrieve) {
        this.dataToWrite = data;
        this.failedToRetrieve = failedToRetrieve;
    }
}
