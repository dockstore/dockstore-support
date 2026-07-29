package io.dockstore.categorizer.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getDockstoreServerUrl;
import static io.dockstore.utils.ConfigFileUtils.getDockstoreToken;

import org.apache.commons.configuration2.INIConfiguration;

public record CategorizerConfig(String dockstoreServerUrl, String dockstoreToken) {

    public CategorizerConfig(INIConfiguration iniConfig) {
        this(getDockstoreServerUrl(iniConfig), getDockstoreToken(iniConfig));
    }
}
