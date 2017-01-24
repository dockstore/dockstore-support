package io.dockstore.tooltester.client.cli;

import com.offbytwo.jenkins.JenkinsServer;


/**
 * @author gluu
 * @since 20/01/17
 */
class ParameterFileTester extends JenkinsJob{
    ParameterFileTester(JenkinsServer jenkins) {
        super(jenkins);
    }
    public String getPREFIX() {
        return PREFIX;
    }
    private static final String PREFIX = "ParameterFileTest";
}
