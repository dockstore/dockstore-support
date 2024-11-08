package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.client.cli.BaseIT.ADMIN_USERNAME;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

class TopicGeneratorClientIT {
    public static final String CONFIG_FILE_PATH = ResourceHelpers.resourceFilePath("topic-generator.config");

    private static TestingPostgres testingPostgres;

    private static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @BeforeAll
    public static void setup() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @BeforeEach
    public void dropAndRecreateDB() {
        CommonTestUtilities.dropAndCreateWithTestDataAndAdditionalToolsAndWorkflows(SUPPORT, false, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.after();
    }

    @Test
    void testUploadAITopics() {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, null);
        assertNull(workflow.getTopicAI());
        // This file is modelled after the output file from the "generate-topics" command. It contains 1 row
        String aiTopicsFilePath = ResourceHelpers.resourceFilePath("generated-ai-topics.csv");

        TopicGeneratorClient.main(new String[] {"--config", CONFIG_FILE_PATH, "upload-topics", "--aiTopics", aiTopicsFilePath});
        workflow = workflowsApi.getPublishedWorkflow(32L, null);
        assertNotNull(workflow.getTopicAI());
    }
}
