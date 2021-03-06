package io.dockstore.tooltester.helper;

import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * @author gluu
 * @since 20/01/17
 */
public class PipelineTester extends JenkinsHelper {
    public static final String PREFIX = "PipelineTest";

    public PipelineTester(HierarchicalINIConfiguration config) {
        super(config);
    }

    public String getPREFIX() {
        return PREFIX;
    }

}
