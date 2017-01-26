package io.dockstore.tooltester.client.cli;

import com.offbytwo.jenkins.JenkinsServer;

/**
 * @author gluu
 * @since 20/01/17
 */
class ParameterFileTester extends JenkinsJob {
    private static final String PREFIX = "ParameterFileTest";

    ParameterFileTester(JenkinsServer jenkins) {
        super(jenkins);
    }

    public String getPREFIX() {
        return PREFIX;
    }

}
