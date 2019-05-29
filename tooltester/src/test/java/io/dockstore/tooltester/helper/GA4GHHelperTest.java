package io.dockstore.tooltester.helper;

import io.swagger.client.model.ToolVersion;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 07/05/19
 */
public class GA4GHHelperTest {

    @Test
    public void runnerSupportsDescriptorType() {
        Assert.assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.CWL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.WDL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwl-runner", ToolVersion.DescriptorTypeEnum.NFL));
        Assert.assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.CWL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.WDL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cwltool", ToolVersion.DescriptorTypeEnum.NFL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.CWL));
        Assert.assertTrue(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.WDL));
        Assert.assertFalse(GA4GHHelper.runnerSupportsDescriptorType("cromwell", ToolVersion.DescriptorTypeEnum.NFL));
    }
}
