package io.dockstore.tooltester.helper;

import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * @author gluu
 * @since 20/01/17
 */
public class PipelineTester extends JenkinsHelper {
    private static final String PREFIX = "PipelineTest2";

    public PipelineTester(HierarchicalINIConfiguration config) {
        super(config);
    }

    public String getPREFIX() {
        return PREFIX;
    }

}
