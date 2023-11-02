package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getDockstoreServerUrl;
import static io.dockstore.utils.ConfigFileUtils.getOpenAIApiKey;

import org.apache.commons.configuration2.INIConfiguration;

public record TopicGeneratorConfig(String dockstoreServerUrl, String openaiApiKey) {

    public TopicGeneratorConfig(INIConfiguration iniConfig) {
        this(getDockstoreServerUrl(iniConfig), getOpenAIApiKey(iniConfig));
    }
}
