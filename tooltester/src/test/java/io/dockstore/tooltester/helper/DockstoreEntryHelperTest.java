package io.dockstore.tooltester.helper;

import java.util.List;

import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tool;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author gluu
 * @since 03/04/19
 */

/**
 * Many tests ignored due to reasons explained in this PR https://github.com/dockstore/dockstore-support/pull/448
 */
public class DockstoreEntryHelperTest {
    // This actually uses the real Dockstore staging server
    private static final String serverUrl = "https://staging.dockstore.org/api";
    private static WorkflowsApi workflowsApi;
    private static ContainersApi containersApi;

    static {
        ApiClient defaultApiClient = Configuration.getDefaultApiClient();
        defaultApiClient.setBasePath(serverUrl);
        workflowsApi = new WorkflowsApi(defaultApiClient);
        containersApi = new ContainersApi(defaultApiClient);
    }

    @Test
    public void generateLaunchToolCommand() {
        // No tool name
        Long toolId = 1055L;
        DockstoreTool dockstoreTool = containersApi.getPublishedContainer(toolId, null);
        List<Tag> tags = dockstoreTool.getWorkflowVersions();
        Tag tag1 = tags.stream().filter(tag -> tag.getName().equals("1.0.4"))
                .findFirst().get();
        String command = DockstoreEntryHelper.generateLaunchEntryCommand(dockstoreTool, tag1, "test.json");
        Assert.assertEquals(command, "dockstore tool launch --entry quay.io/briandoconnor/dockstore-tool-md5sum:1.0.4 --json test.json --script");
    }

    @Ignore
    @Test
    public void generateLaunchWorkflowCommand() {
        // No workflow name
        Long workflowId = 1678L;
        Workflow publishedWorkflow = workflowsApi.getPublishedWorkflow(workflowId, null);
        List<WorkflowVersion> workflowVersions = publishedWorkflow.getWorkflowVersions();
        WorkflowVersion workflowVersion1 = workflowVersions.stream().filter(workflowVersion -> workflowVersion.getName().equals("1.4.0"))
                .findFirst().get();
        String command = DockstoreEntryHelper.generateLaunchEntryCommand(publishedWorkflow, workflowVersion1, "test.json");
        Assert.assertEquals("dockstore workflow launch --entry github.com/briandoconnor/dockstore-workflow-md5sum:1.4.0 --json test.json --script", command);
        // With workflow name
        workflowId = 4818L;
        publishedWorkflow = workflowsApi.getPublishedWorkflow(workflowId, null);
        workflowVersions = publishedWorkflow.getWorkflowVersions();
        workflowVersion1 = workflowVersions.stream().filter(workflowVersion -> workflowVersion.getName().equals("1.0.0"))
                .findFirst().get();
        command = DockstoreEntryHelper.generateLaunchEntryCommand(publishedWorkflow, workflowVersion1, "test.yaml");
        Assert.assertEquals("dockstore workflow launch --entry github.com/ICGC-TCGA-PanCancer/pcawg-snv-indel-annotation:1.0.0 --yaml test.yaml --script", command);
    }

    @Test
    public void convertTRSToolToDockstoreTool() {
        Tool tool = new Tool();
        tool.setId("quay.io/briandoconnor/dockstore-tool-md5sum");
        DockstoreTool dockstoreTool = DockstoreEntryHelper.convertTRSToolToDockstoreEntry(tool, containersApi);
        Assert.assertNotNull(dockstoreTool);
    }

    @Ignore
    @Test
    public void convertTRSToolToWorkflow() {
        Tool tool = new Tool();
        tool.setId("#workflow/github.com/briandoconnor/dockstore-workflow-md5sum");
        Workflow workflow = DockstoreEntryHelper.convertTRSToolToDockstoreEntry(tool, workflowsApi);
        Assert.assertNotNull(workflow);
    }

    @Test
    public void convertGitSSHUrlToGitHTTPSUrl() {
        String originalGitUrl = "git@github.com:briandoconnor/dockstore-workflow-md5sum.git";
        Assert.assertEquals("https://github.com/briandoconnor/dockstore-workflow-md5sum.git", DockstoreEntryHelper.convertGitSSHUrlToGitHTTPSUrl(originalGitUrl));
    }
    
    @Test
    public void convertDockstoreAbsolutePathToJenkinsRelativePath() {
        String dockstoreAbsolutePath = "/Dockerfile";
        Assert.assertEquals("Dockerfile", DockstoreEntryHelper.convertDockstoreAbsolutePathToJenkinsRelativePath(dockstoreAbsolutePath));
    }
}
