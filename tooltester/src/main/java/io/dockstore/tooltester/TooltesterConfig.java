package io.dockstore.tooltester;

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import static io.dockstore.tooltester.helper.ExceptionHandler.API_ERROR;
import static io.dockstore.tooltester.helper.ExceptionHandler.exceptionMessage;

/**
 * @author gluu
 * @since 23/04/19
 */
public class TooltesterConfig {

    private HierarchicalINIConfiguration config;
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
            File configFile = new File(userHome + File.separator + ".tooltester" + File.separator + "config");
            setConfig(new HierarchicalINIConfiguration(configFile));
            setServerUrl(config.getString("server-url", "https://staging.dockstore.org/api"));
            setRunner(config.getString("runner", "cwltool cwl-runner cromwell").split(" "));
            setDockstoreVersion(config.getString("dockstore-version", "1.13.1"));
            setS3Bucket(config.getString("s3-bucket", "dockstore.tooltester.backup"));
            setS3Endpoint(config.getString("s3-endpoint", "https://s3.amazonaws.com"));
            setJenkinsServerUrl(config.getString("jenkins-server-url", "http://172.18.0.22:8080"));
            setDockstoreAuthorizationToken(config.getString("authorization-token", null));
        } catch (ConfigurationException e) {
            exceptionMessage(e, "Could not get configuration file", API_ERROR);
        }
    }

    public HierarchicalINIConfiguration getConfig() {
        return config;
    }

    private void setConfig(HierarchicalINIConfiguration config) {
        this.config = config;
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

}
