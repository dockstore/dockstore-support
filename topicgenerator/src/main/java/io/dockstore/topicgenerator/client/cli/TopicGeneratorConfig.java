package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getDockstoreServerUrl;
import static io.dockstore.utils.ConfigFileUtils.getDockstoreToken;

import org.apache.commons.configuration2.INIConfiguration;

public record TopicGeneratorConfig(String dockstoreServerUrl, String dockstoreToken) {

    public TopicGeneratorConfig(INIConfiguration iniConfig) {
        this(getDockstoreServerUrl(iniConfig), getDockstoreToken(iniConfig));
    }
}
