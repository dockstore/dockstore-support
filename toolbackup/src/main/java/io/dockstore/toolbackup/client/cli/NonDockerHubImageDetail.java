package io.dockstore.toolbackup.client.cli;

import java.util.List;

public class NonDockerHubImageDetail {
    String dockerImage;
    String imageRepoLink;
    String selfTrsID;
    List<String> trsIDsRepeatingImage;

    NonDockerHubImageDetail(String dockerImage, String imageRepoLink, String selfTrsID, List<String> trsIDsRepeatingImage) {
        this.dockerImage = dockerImage;
        this.imageRepoLink = imageRepoLink;
        this.selfTrsID = selfTrsID;
        this.trsIDsRepeatingImage = trsIDsRepeatingImage;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public String getImageRepoLink() {
        return imageRepoLink;
    }

    public String getSelfTrsID() {
        return selfTrsID;
    }

    public List<String> getTrsIDsRepeatingImage() {
        return trsIDsRepeatingImage;
    }

    public void appendToRepetitionList(String trsID) {
        getTrsIDsRepeatingImage().add(trsID);
    }
}
