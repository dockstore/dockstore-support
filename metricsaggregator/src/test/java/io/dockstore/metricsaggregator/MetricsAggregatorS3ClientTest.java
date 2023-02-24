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

import java.util.List;
import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ContainertagsApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.helpers.S3ClientHelper;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import static io.dockstore.client.cli.BaseIT.ADMIN_USERNAME;
import static io.dockstore.metricsaggregator.common.TestUtilities.BUCKET_NAME;
import static io.dockstore.metricsaggregator.common.TestUtilities.ENDPOINT_OVERRIDE;
import static io.dockstore.metricsaggregator.common.TestUtilities.createExecution;
import static io.dockstore.openapi.client.model.Execution.ExecutionStatusEnum.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(LocalstackDockerExtension.class)
@LocalstackDockerProperties(imageTag = LocalStackTestUtilities.IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
class MetricsAggregatorS3ClientTest {
    private static MetricsAggregatorS3Client metricsAggregatorS3Client;
    private static S3Client s3Client;
    private static TestingPostgres testingPostgres;

    public static DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeAll
    public static void setup() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);

        metricsAggregatorS3Client = new MetricsAggregatorS3Client(BUCKET_NAME, ENDPOINT_OVERRIDE);
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client
        // Create a bucket to be used for tests
        CreateBucketRequest request = CreateBucketRequest.builder().bucket(BUCKET_NAME).build();
        s3Client.createBucket(request);
        deleteBucketContents(); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @BeforeEach
    public void dropAndRecreateDB() {
        CommonTestUtilities.dropAndCreateWithTestDataAndAdditionalToolsAndWorkflows(SUPPORT, false, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    }

    @AfterEach
    void tearDown() {
        deleteBucketContents();
    }

    private static void deleteBucketContents() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<S3Object> contents = response.contents();
        contents.forEach(s3Object -> {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(s3Object.key()).build();
            s3Client.deleteObject(deleteObjectRequest);
        });
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
        Execution execution = createExecution(SUCCESSFUL, "PT5M", 2, "2 GB");
        extendedGa4GhApi.executionMetricsPost(List.of(execution), platform1, workflowId, workflowVersionId, "");
        extendedGa4GhApi.executionMetricsPost(List.of(execution), platform2, workflowId, workflowVersionId, "");
        extendedGa4GhApi.executionMetricsPost(List.of(execution), platform1, toolId, toolVersionId, "");

        List<String> directories = metricsAggregatorS3Client.getDirectories();
        assertEquals(3, directories.size());
        assertTrue(directories.contains(String.join("/", S3ClientHelper.convertToolIdToPartialKey(workflowId), workflowVersionId, platform1 + "/")));
        assertTrue(directories.contains(String.join("/", S3ClientHelper.convertToolIdToPartialKey(workflowId), workflowVersionId, platform2 + "/")));
        assertTrue(directories.contains(String.join("/", S3ClientHelper.convertToolIdToPartialKey(toolId), toolVersionId, platform1 + "/")));
    }
}
