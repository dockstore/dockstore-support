package io.dockstore.tooltester.helper;

import java.util.List;

import io.swagger.client.ApiClient;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author gluu
 * @since 03/04/19
 */
public class DockstoreEntryHelperTest {
    // This actually uses the real dockstore staging server
    private final String serverUrl = "https://staging.dockstore.org/api";


    @Test
    public void generateLaunchToolCommand() {
        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();

        defaultApiClient.setBasePath(serverUrl);

        ContainersApi containersApi = new ContainersApi(defaultApiClient);
        // No tool name
        Long toolId = 1055L;
        DockstoreTool dockstoreTool = containersApi.getPublishedContainer(toolId);
        List<Tag> tags = dockstoreTool.getTags();
        Tag tag1 = tags.stream().filter(tag -> tag.getName().equals("1.0.4"))
                .findFirst().get();
        String command = DockstoreEntryHelper.generateLaunchEntryCommand(dockstoreTool, tag1, "test.json");
        Assert.assertEquals(command, "dockstore tool launch --entry quay.io/briandoconnor/dockstore-tool-md5sum:1.0.4 --json test.json --script");
    }

    @Test
    public void generateLaunchWorkflowCommand() {
        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        // This actually uses the real dockstore staging server
        defaultApiClient.setBasePath(serverUrl);

        WorkflowsApi workflowsApi = new WorkflowsApi(defaultApiClient);
        // No workflow name
        Long workflowId = 1678L;
        Workflow publishedWorkflow = workflowsApi.getPublishedWorkflow(workflowId);
        List<WorkflowVersion> workflowVersions = publishedWorkflow.getWorkflowVersions();
        WorkflowVersion workflowVersion1 = workflowVersions.stream().filter(workflowVersion -> workflowVersion.getName().equals("1.4.0"))
                .findFirst().get();
        String command = DockstoreEntryHelper.generateLaunchEntryCommand(publishedWorkflow, workflowVersion1, "test.json");
        Assert.assertEquals(command, "dockstore workflow launch --entry github.com/briandoconnor/dockstore-workflow-md5sum:1.4.0 --json test.json --script");
        // With workflow name
        workflowId = 5181L;
        publishedWorkflow = workflowsApi.getPublishedWorkflow(workflowId);
        workflowVersions = publishedWorkflow.getWorkflowVersions();
        workflowVersion1 = workflowVersions.stream().filter(workflowVersion -> workflowVersion.getName().equals("dockstore"))
                .findFirst().get();
        command = DockstoreEntryHelper.generateLaunchEntryCommand(publishedWorkflow, workflowVersion1, "test.yaml");
        Assert.assertEquals(command, "dockstore workflow launch --entry github.com/HumanCellAtlas/skylab/HCA_SmartSeq2:dockstore --yaml test.yaml --script");
    }
}
