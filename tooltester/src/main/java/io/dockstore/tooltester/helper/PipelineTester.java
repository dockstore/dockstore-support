package io.dockstore.tooltester.helper;

import com.offbytwo.jenkins.JenkinsServer;

/**
 * @author gluu
 * @since 20/01/17
 */
public class PipelineTester extends JenkinsHelper {
    private static final String PREFIX = "PipelineTest";

    public PipelineTester(JenkinsServer jenkins) {
        super(jenkins);
    }

    public String getPREFIX() {
        return PREFIX;
    }

}
