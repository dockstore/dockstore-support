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

    private HierarchicalINIConfiguration tooltesterConfig;
    private HierarchicalINIConfiguration dockstoreConfig;
    private String jenkinsServerUrl;
    private String serverUrl;
    private String[] runner;
    private String dockstoreVersion;
    private String s3Bucket;
    private String s3Endpoint;
    private String dockstoreAuthorizationToken;

    public TooltesterConfig() {
        String userHome = System.getProperty("user.home");
        try {
            // This is the config file for production use.
            // If there's no configuration file, all properties will default to Travis ones.
            // None of the below variables are used in the tooltester function "run-workflows"
            File configFile = new File(userHome + File.separator + ".tooltester" + File.separator + "config");
            setTooltesterConfig(new HierarchicalINIConfiguration(configFile));
            setRunner(tooltesterConfig.getString("runner", "cwltool cwl-runner cromwell").split(" "));
            setDockstoreVersion(tooltesterConfig.getString("dockstore-version", "1.13.1"));
            setS3Bucket(tooltesterConfig.getString("s3-bucket", "dockstore.tooltester.backup"));
            setS3Endpoint(tooltesterConfig.getString("s3-endpoint", "https://s3.amazonaws.com"));
            setJenkinsServerUrl(tooltesterConfig.getString("jenkins-server-url", "http://172.18.0.22:8080"));
        } catch (ConfigurationException e) {
            exceptionMessage(e, "Could not get ~/.tooltester/config configuration file", API_ERROR);
        }

        try {
            // This is the config file used by the dockstore CLI, it should be in ~/.dockstore/config
            // The below variables are used in the tooltester function "run-workflows"
            File configFile = new File(userHome + File.separator + ".dockstore" + File.separator + "config");
            setDockstoreConfig(new HierarchicalINIConfiguration(configFile));
            setServerUrl(dockstoreConfig.getString("server-url", "https://staging.dockstore.org/api"));
            setDockstoreAuthorizationToken(dockstoreConfig.getString("token", null));
        } catch (ConfigurationException e) {
            exceptionMessage(e, "Could not get ~/.dockstore/config configuration file", API_ERROR);
        }
    }

    public HierarchicalINIConfiguration getTooltesterConfig() {
        return tooltesterConfig;
    }

    private void setTooltesterConfig(HierarchicalINIConfiguration tooltesterConfig) {
        this.tooltesterConfig = tooltesterConfig;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    private void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String[] getRunner() {
        return runner;
    }

    public void setRunner(String[] runner) {
        this.runner = runner;
    }

    public String getDockstoreVersion() {
        return dockstoreVersion;
    }

    private void setDockstoreVersion(String dockstoreVersion) {
        this.dockstoreVersion = dockstoreVersion;
    }

    String getS3Bucket() {
        return s3Bucket;
    }

    private void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    String getS3Endpoint() {
        return s3Endpoint;
    }

    private void setS3Endpoint(String s3Endpoint) {
        this.s3Endpoint = s3Endpoint;
    }

    public String getJenkinsServerUrl() {
        return jenkinsServerUrl;
    }

    private void setJenkinsServerUrl(String jenkinsServerUrl) {
        this.jenkinsServerUrl = jenkinsServerUrl;
    }

    public String getDockstoreAuthorizationToken() {
        return dockstoreAuthorizationToken;
    }

    private void setDockstoreAuthorizationToken(String dockstoreAuthorizationToken) {
        this.dockstoreAuthorizationToken = dockstoreAuthorizationToken;
    }

    private void setDockstoreConfig(HierarchicalINIConfiguration dockstoreConfig) {
        this.dockstoreConfig = dockstoreConfig;
    }

}
