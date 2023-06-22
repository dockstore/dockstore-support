/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.metricsaggregator;

import static io.dockstore.client.cli.BaseIT.ADMIN_USERNAME;
import static io.dockstore.metricsaggregator.common.TestUtilities.BUCKET_NAME;
import static io.dockstore.metricsaggregator.common.TestUtilities.ENDPOINT_OVERRIDE;
import static io.dockstore.metricsaggregator.common.TestUtilities.createRunExecution;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.common.Partner;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ContainertagsApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(LocalstackDockerExtension.class)
@LocalstackDockerProperties(imageTag = LocalStackTestUtilities.IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
class MetricsAggregatorS3ClientIT {
    private static MetricsAggregatorS3Client metricsAggregatorS3Client;
    private static S3Client s3Client;
    private static TestingPostgres testingPostgres;

    private static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeAll
    public static void setup() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);

        metricsAggregatorS3Client = new MetricsAggregatorS3Client(BUCKET_NAME, ENDPOINT_OVERRIDE);
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client
        // Create a bucket to be used for tests
        LocalStackTestUtilities.createBucket(s3Client, BUCKET_NAME);
        LocalStackTestUtilities.deleteBucketContents(s3Client, BUCKET_NAME); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @BeforeEach
    public void dropAndRecreateDB() {
        CommonTestUtilities.dropAndCreateWithTestDataAndAdditionalToolsAndWorkflows(SUPPORT, false, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    }

    @AfterEach
    void tearDown() {
        LocalStackTestUtilities.deleteBucketContents(s3Client, BUCKET_NAME);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.after();
    }

    @Test
    void testGetDirectories() {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        final ContainersApi containersApi = new ContainersApi(apiClient);
        final ContainertagsApi containertagsApi = new ContainertagsApi(apiClient);
        String platform1 = Partner.TERRA.name();
        String platform2 = Partner.DNA_STACK.name();

        DockstoreTool tool = containersApi.getContainer(6L, "");
        Tag tag = containertagsApi.getTagsByPath(tool.getId()).stream().filter(t -> "fakeName".equals(t.getName())).findFirst().orElse(null);
        assertNotNull(tag);
        final String toolId = tool.getToolPath();
        final String toolVersionId = tag.getName();

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        final String workflowId = "#workflow/" + workflow.getFullWorkflowPath();
        final String workflowVersionId = version.getName();

        // A successful execution that ran for 5 minutes, requires 2 CPUs and 2 GBs of memory
        RunExecution execution = createRunExecution(SUCCESSFUL, "PT5M", 2, 2.0);
        ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().runExecutions(List.of(execution));
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform1, workflowId, workflowVersionId, "");
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform2, workflowId, workflowVersionId, "");
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform1, toolId, toolVersionId, "");

        List<MetricsAggregatorS3Client.S3DirectoryInfo> s3DirectoryInfos = metricsAggregatorS3Client.getDirectories();
        assertEquals(2, s3DirectoryInfos.size());
        MetricsAggregatorS3Client.S3DirectoryInfo s3DirectoryInfo = s3DirectoryInfos.stream()
                .filter(directoryInfo -> Objects.equals(directoryInfo.versionS3KeyPrefix(), String.join("/", S3ClientHelper.convertToolIdToPartialKey(workflowId), workflowVersionId + "/")))
                .findFirst()
                .orElse(null);
        assertNotNull(s3DirectoryInfo);
        assertEquals(workflowId, s3DirectoryInfo.toolId());
        assertEquals(workflowVersionId, s3DirectoryInfo.versionId());
        assertEquals(2, s3DirectoryInfo.platforms().size());
        assertTrue(s3DirectoryInfo.platforms().contains(platform1) && s3DirectoryInfo.platforms().contains(platform2));

        s3DirectoryInfo = s3DirectoryInfos.stream()
                .filter(directoryInfo -> Objects.equals(directoryInfo.versionS3KeyPrefix(), String.join("/", S3ClientHelper.convertToolIdToPartialKey(toolId), toolVersionId + "/")))
                .findFirst()
                .orElse(null);
        assertNotNull(s3DirectoryInfo);
        assertEquals(toolId, s3DirectoryInfo.toolId());
        assertEquals(toolVersionId, s3DirectoryInfo.versionId());
        assertEquals(1, s3DirectoryInfo.platforms().size());
        assertTrue(s3DirectoryInfo.platforms().contains(platform1));
    }
}
