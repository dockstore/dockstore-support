package io.dockstore.tooltester;

import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

import java.io.File;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * @author gluu
 * @since 23/04/19
 */
public class TooltesterConfig {

    private HierarchicalINIConfiguration dockstoreConfig;
    private String serverUrl;

    public TooltesterConfig() {
        String userHome = System.getProperty("user.home");

        try {
            // This is the config file used by the dockstore CLI, it should be in ~/.dockstore/config
            // The below variables are used in the tooltester function "run-workflows"
            File configFile = new File(userHome + File.separator + ".dockstore" + File.separator + "config");
            setDockstoreConfig(new HierarchicalINIConfiguration(configFile));
            setServerUrl(dockstoreConfig.getString("server-url", "https://staging.dockstore.org/api"));
        } catch (ConfigurationException e) {
            exceptionMessage(e, "Could not get ~/.dockstore/config configuration file", API_ERROR);
        }
    }

    public String getServerUrl() {
        return serverUrl;
    }

    private void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    private void setDockstoreConfig(HierarchicalINIConfiguration dockstoreConfig) {
        this.dockstoreConfig = dockstoreConfig;
    }

}
