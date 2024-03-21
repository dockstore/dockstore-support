package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getDockstoreServerUrl;
import static io.dockstore.utils.ConfigFileUtils.getDockstoreToken;
import static io.dockstore.utils.ConfigFileUtils.getOpenAIApiKey;

import org.apache.commons.configuration2.INIConfiguration;

public record TopicGeneratorConfig(String dockstoreServerUrl, String dockstoreToken, String openaiApiKey) {

    public TopicGeneratorConfig(INIConfiguration iniConfig) {
        this(getDockstoreServerUrl(iniConfig), getDockstoreToken(iniConfig), getOpenAIApiKey(iniConfig));
    }
}
