package io.dockstore.utils;

import java.io.File;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigFileUtils {
    public static final String CONFIG_FILE_ERROR = "Could not get configuration file";
    public static final String DOCKSTORE_SECTION = "dockstore";
    public static final String DOCKSTORE_SERVER_URL = "server-url";
    public static final String AI_SECTION = "ai";
    public static final String OPENAI_API_KEY = "openai-api-key";
    private static final Logger LOG = LoggerFactory.getLogger(ConfigFileUtils.class);

    private ConfigFileUtils() {
    }

    public static Optional<INIConfiguration> getConfiguration(File iniFile) {
        Configurations configs = new Configurations();

        try {
            INIConfiguration config = configs.ini(iniFile);
            return Optional.of(config);
        } catch (ConfigurationException e) {
            LOG.error(CONFIG_FILE_ERROR, e);
            return Optional.empty();
        }
    }

    public static SubnodeConfiguration getDockstoreSection(INIConfiguration iniConfig) {
        return iniConfig.getSection(DOCKSTORE_SECTION);
    }

    public static String getDockstoreServerUrl(INIConfiguration iniConfig) {
        return getDockstoreSection(iniConfig).getString(DOCKSTORE_SERVER_URL);
    }

    public static SubnodeConfiguration getAISection(INIConfiguration iniConfig) {
        return iniConfig.getSection(AI_SECTION);
    }

    public static String getOpenAIApiKey(INIConfiguration iniConfig) {
        return getAISection(iniConfig).getString(OPENAI_API_KEY);
    }
}
