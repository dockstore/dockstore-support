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

package io.dockstore.metricsaggregator.client.cli;

import static io.dockstore.client.cli.BaseIT.ADMIN_USERNAME;
import static io.dockstore.common.Partner.DNA_STACK;
import static io.dockstore.metricsaggregator.common.TestUtilities.BUCKET_NAME;
import static io.dockstore.metricsaggregator.common.TestUtilities.CONFIG_FILE_PATH;
import static io.dockstore.metricsaggregator.common.TestUtilities.ENDPOINT_OVERRIDE;
import static io.dockstore.metricsaggregator.common.TestUtilities.createRunExecution;
import static io.dockstore.metricsaggregator.common.TestUtilities.createTasksExecutions;
import static io.dockstore.metricsaggregator.common.TestUtilities.createValidationExecution;
import static io.dockstore.metricsaggregator.common.TestUtilities.generateExecutionId;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.ABORTED;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.ALL;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_RUNTIME_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_SEMANTIC_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.SUCCESSFUL;
import static io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum.MINIWDL;
import static io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum.WOMTOOL;
import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.gson.Gson;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.common.Partner;
import io.dockstore.common.TestingPostgres;
import io.dockstore.common.metrics.MetricsData;
import io.dockstore.common.metrics.MetricsDataS3Client;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Cost;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.MetricsByStatus;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith({LocalstackDockerExtension.class, SystemStubsExtension.class})
@LocalstackDockerProperties(imageTag = LocalStackTestUtilities.IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
class MetricsAggregatorClientIT {
    private static S3Client s3Client;
    private static TestingPostgres testingPostgres;
    private static MetricsDataS3Client metricsDataS3Client;
    private static final Gson GSON = new Gson();

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

        metricsDataS3Client = new MetricsDataS3Client(BUCKET_NAME, ENDPOINT_OVERRIDE);
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
    @SuppressWarnings("checkstyle:methodlength")
    void testAggregateMetrics() {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        String platform1 = Partner.TERRA.name();
        String platform2 = Partner.DNA_STACK.name();

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertTrue(version.getMetricsByPlatform().isEmpty());

        String id = "#workflow/" + workflow.getFullWorkflowPath();
        String versionId = version.getName();

        // A successful workflow run execution that ran for 5 minutes, requires 2 CPUs and 2 GBs of memory
        List<RunExecution> workflowExecutions = List.of(createRunExecution(SUCCESSFUL, "PT5M", 2, 2.0, new Cost().value(2.00), "us-central1"));
        // A successful miniwdl validation
        final String validatorToolVersion1 = "1.0";
        ValidationExecution validationExecution1 = createValidationExecution(MINIWDL, validatorToolVersion1, true);
        final String  validatorToolVersion2 = "2.0";
        ValidationExecution validationExecution2 = createValidationExecution(WOMTOOL, validatorToolVersion2, false);

        // Submit metrics for two platforms
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(workflowExecutions).validationExecutions(List.of(validationExecution1)), platform1, id, versionId, "");
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(workflowExecutions).validationExecutions(List.of(validationExecution2)), platform2, id, versionId, "");
        int expectedNumberOfPlatforms = 3; // 2 for platform1 and platform2, and 1 for ALL platforms
        // Aggregate metrics
        MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        // Get workflow version to verify aggregated metrics
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertEquals(expectedNumberOfPlatforms, version.getMetricsByPlatform().size(), "There should be metrics for two platforms");
        assertAggregatedMetricsForPlatform(platform1, version, validationExecution1);
        assertAggregatedMetricsForPlatform(platform2, version, validationExecution2);

        Metrics platform1Metrics = version.getMetricsByPlatform().get(platform1);
        assertNotNull(platform1Metrics);

        ValidatorVersionInfo mostRecentValidationVersionInfo;
        ValidatorInfo validationInfo;

        // A failed run execution that ran for 1 second, requires 2 CPUs and 4.5 GBs of memory
        workflowExecutions = List.of(createRunExecution(FAILED_RUNTIME_INVALID, "PT1S", 4, 4.5, new Cost().value(2.00), "us-central1"));
        // A failed miniwdl validation for the same validator version
        List<ValidationExecution> validationExecutions = List.of(createValidationExecution(MINIWDL, "1.0", false));
        ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().runExecutions(workflowExecutions).validationExecutions(validationExecutions);
        // Submit metrics for the same workflow version for platform 1
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform1, id, versionId, "");
        // Aggregate metrics
        MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        // Get workflow version to verify aggregated metrics
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertEquals(expectedNumberOfPlatforms, version.getMetricsByPlatform().size());
        platform1Metrics = version.getMetricsByPlatform().get(platform1);

        // This version now has two execution metrics data for it. Verify that the aggregated metrics are correct
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(2, platform1Metrics.getExecutionStatusCount().getCount().get(ALL.name()).getExecutionStatusCount());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()).getExecutionStatusCount());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name()).getExecutionStatusCount());
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        // Check metrics for ALL executions statuses
        MetricsByStatus platform1AllStatusesMetrics = platform1Metrics.getExecutionStatusCount().getCount().get(ALL.name());
        assertEquals(2, platform1AllStatusesMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1AllStatusesMetrics.getCpu().getMinimum());
        assertEquals(4, platform1AllStatusesMetrics.getCpu().getMaximum());
        assertEquals(3, platform1AllStatusesMetrics.getCpu().getAverage());
        assertNull(platform1AllStatusesMetrics.getCpu().getUnit());

        assertEquals(2, platform1AllStatusesMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1AllStatusesMetrics.getMemory().getMinimum());
        assertEquals(4.5, platform1AllStatusesMetrics.getMemory().getMaximum());
        assertEquals(3.25, platform1AllStatusesMetrics.getMemory().getAverage());
        assertNotNull(platform1AllStatusesMetrics.getMemory().getUnit());

        assertEquals(2, platform1AllStatusesMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1AllStatusesMetrics.getCost().getMinimum());
        assertEquals(2, platform1AllStatusesMetrics.getCost().getMaximum());
        assertEquals(2, platform1AllStatusesMetrics.getCost().getAverage());
        assertNotNull(platform1AllStatusesMetrics.getCost().getUnit());

        assertEquals(2, platform1AllStatusesMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, platform1AllStatusesMetrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1AllStatusesMetrics.getExecutionTime().getMaximum());
        assertEquals(150.5, platform1AllStatusesMetrics.getExecutionTime().getAverage());
        assertNotNull(platform1AllStatusesMetrics.getExecutionTime().getUnit());

        // Check metrics for successful executions
        MetricsByStatus platform1SuccessfulMetrics = platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name());
        assertEquals(1, platform1SuccessfulMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1SuccessfulMetrics.getCpu().getMinimum());
        assertEquals(2, platform1SuccessfulMetrics.getCpu().getMaximum());
        assertEquals(2, platform1SuccessfulMetrics.getCpu().getAverage());
        assertNull(platform1SuccessfulMetrics.getCpu().getUnit());

        assertEquals(1, platform1SuccessfulMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1SuccessfulMetrics.getMemory().getMinimum());
        assertEquals(2, platform1SuccessfulMetrics.getMemory().getMaximum());
        assertEquals(2, platform1SuccessfulMetrics.getMemory().getAverage());
        assertNotNull(platform1SuccessfulMetrics.getMemory().getUnit());

        assertEquals(1, platform1SuccessfulMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1SuccessfulMetrics.getCost().getMinimum());
        assertEquals(2, platform1SuccessfulMetrics.getCost().getMaximum());
        assertEquals(2, platform1SuccessfulMetrics.getCost().getAverage());
        assertNotNull(platform1SuccessfulMetrics.getCost().getUnit());

        assertEquals(1, platform1SuccessfulMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(300, platform1SuccessfulMetrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1SuccessfulMetrics.getExecutionTime().getMaximum());
        assertEquals(300, platform1SuccessfulMetrics.getExecutionTime().getAverage());
        assertNotNull(platform1SuccessfulMetrics.getExecutionTime().getUnit());

        // Check metrics for failed runtime invalid executions
        MetricsByStatus platform1FailedRuntimeInvalidMetrics = platform1Metrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name());
        assertEquals(1, platform1FailedRuntimeInvalidMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(4, platform1FailedRuntimeInvalidMetrics.getCpu().getMinimum());
        assertEquals(4, platform1FailedRuntimeInvalidMetrics.getCpu().getMaximum());
        assertEquals(4, platform1FailedRuntimeInvalidMetrics.getCpu().getAverage());
        assertNull(platform1FailedRuntimeInvalidMetrics.getCpu().getUnit());

        assertEquals(1, platform1FailedRuntimeInvalidMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(4.5, platform1FailedRuntimeInvalidMetrics.getMemory().getMinimum());
        assertEquals(4.5, platform1FailedRuntimeInvalidMetrics.getMemory().getMaximum());
        assertEquals(4.5, platform1FailedRuntimeInvalidMetrics.getMemory().getAverage());
        assertNotNull(platform1FailedRuntimeInvalidMetrics.getMemory().getUnit());

        assertEquals(1, platform1FailedRuntimeInvalidMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1FailedRuntimeInvalidMetrics.getCost().getMinimum());
        assertEquals(2, platform1FailedRuntimeInvalidMetrics.getCost().getMaximum());
        assertEquals(2, platform1FailedRuntimeInvalidMetrics.getCost().getAverage());
        assertNotNull(platform1FailedRuntimeInvalidMetrics.getCost().getUnit());

        assertEquals(1, platform1FailedRuntimeInvalidMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, platform1FailedRuntimeInvalidMetrics.getExecutionTime().getMinimum());
        assertEquals(1, platform1FailedRuntimeInvalidMetrics.getExecutionTime().getMaximum());
        assertEquals(1, platform1FailedRuntimeInvalidMetrics.getExecutionTime().getAverage());
        assertNotNull(platform1FailedRuntimeInvalidMetrics.getExecutionTime().getUnit());

        assertEquals(1, platform1Metrics.getValidationStatus().getValidatorTools().size());
        validationInfo = platform1Metrics.getValidationStatus().getValidatorTools().get(MINIWDL.toString());
        assertNotNull(validationInfo);
        assertNotNull(validationInfo.getMostRecentVersionName());
        mostRecentValidationVersionInfo = validationInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion1.equals(validationVersion.getName())).findFirst().get();
        assertFalse(mostRecentValidationVersionInfo.isIsValid(), "miniwdl validation should be invalid");
        assertEquals(validatorToolVersion1, mostRecentValidationVersionInfo.getName());
        assertEquals(50d, mostRecentValidationVersionInfo.getPassingRate());
        assertEquals(2, mostRecentValidationVersionInfo.getNumberOfRuns());
        assertEquals(50d, validationInfo.getPassingRate());
        assertEquals(2, validationInfo.getNumberOfRuns());

        // Submit one TaskExecutions, representing the task metrics for a single workflow execution
        // A successful task execution that ran for 11 seconds, requires 6 CPUs and 5.5 GBs of memory. Signifies that this workflow execution only executed one task
        TaskExecutions taskExecutions = new TaskExecutions().taskExecutions(List.of(createRunExecution(SUCCESSFUL, "PT11S", 6, 5.5, new Cost().value(2.00), "us-central1")));
        taskExecutions.setDateExecuted(Instant.now().toString());
        taskExecutions.setExecutionId(generateExecutionId());
        executionsRequestBody = new ExecutionsRequestBody().taskExecutions(List.of(taskExecutions));
        // Submit metrics for the same workflow version for platform 1
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform1, id, versionId, "");
        // Aggregate metrics
        MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        // Get workflow version to verify aggregated metrics
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertEquals(expectedNumberOfPlatforms, version.getMetricsByPlatform().size());
        platform1Metrics = version.getMetricsByPlatform().get(platform1);

        // This version now has three submissions of execution metrics data. Verify that the aggregated metrics are correct
        assertEquals(2, platform1Metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(3, platform1Metrics.getExecutionStatusCount().getCount().get(ALL.name()).getExecutionStatusCount());
        assertEquals(2, platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()).getExecutionStatusCount());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name()).getExecutionStatusCount());
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        // Check metrics for ALL executions statuses
        platform1AllStatusesMetrics = platform1Metrics.getExecutionStatusCount().getCount().get(ALL.name());
        assertEquals(3, platform1AllStatusesMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1AllStatusesMetrics.getCpu().getMinimum());
        assertEquals(6, platform1AllStatusesMetrics.getCpu().getMaximum());
        assertEquals(4, platform1AllStatusesMetrics.getCpu().getAverage());
        assertNull(platform1AllStatusesMetrics.getCpu().getUnit());

        assertEquals(3, platform1AllStatusesMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1AllStatusesMetrics.getMemory().getMinimum());
        assertEquals(5.5, platform1AllStatusesMetrics.getMemory().getMaximum());
        assertEquals(4, platform1AllStatusesMetrics.getMemory().getAverage());
        assertNotNull(platform1AllStatusesMetrics.getMemory().getUnit());

        assertEquals(3, platform1AllStatusesMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1AllStatusesMetrics.getCost().getMinimum());
        assertEquals(2, platform1AllStatusesMetrics.getCost().getMaximum());
        assertEquals(2, platform1AllStatusesMetrics.getCost().getAverage());
        assertNotNull(platform1AllStatusesMetrics.getCost().getUnit());

        assertEquals(3, platform1AllStatusesMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, platform1AllStatusesMetrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1AllStatusesMetrics.getExecutionTime().getMaximum());
        assertEquals(104, platform1AllStatusesMetrics.getExecutionTime().getAverage());
        assertNotNull(platform1AllStatusesMetrics.getExecutionTime().getUnit());

        // Only checking the metrics for the successful status because that's the one we updated
        platform1SuccessfulMetrics = platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name());
        assertEquals(2, platform1SuccessfulMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1SuccessfulMetrics.getCpu().getMinimum());
        assertEquals(6, platform1SuccessfulMetrics.getCpu().getMaximum());
        assertEquals(4, platform1SuccessfulMetrics.getCpu().getAverage());
        assertNull(platform1SuccessfulMetrics.getCpu().getUnit());

        assertEquals(2, platform1SuccessfulMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1SuccessfulMetrics.getMemory().getMinimum());
        assertEquals(5.5, platform1SuccessfulMetrics.getMemory().getMaximum());
        assertEquals(3.75, platform1SuccessfulMetrics.getMemory().getAverage());
        assertNotNull(platform1SuccessfulMetrics.getMemory().getUnit());

        assertEquals(2, platform1SuccessfulMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1SuccessfulMetrics.getCost().getMinimum());
        assertEquals(2, platform1SuccessfulMetrics.getCost().getMaximum());
        assertEquals(2, platform1SuccessfulMetrics.getCost().getAverage());
        assertNotNull(platform1SuccessfulMetrics.getCost().getUnit());

        assertEquals(2, platform1SuccessfulMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(11, platform1SuccessfulMetrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1SuccessfulMetrics.getExecutionTime().getMaximum());
        assertEquals(155.5, platform1SuccessfulMetrics.getExecutionTime().getAverage());
        assertNotNull(platform1SuccessfulMetrics.getExecutionTime().getUnit());

        testOverallAggregatedMetrics(version, validatorToolVersion1, validatorToolVersion2, platform1Metrics);
    }

    private static void assertAggregatedMetricsForPlatform(String platform, WorkflowVersion version, ValidationExecution submittedValidationExecution) {
        Metrics platformMetrics = version.getMetricsByPlatform().get(platform);
        assertNotNull(platformMetrics);

        // Verify that the aggregated metrics are the same as the single execution for the platform
        assertEquals(1, platformMetrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(0, platformMetrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(1, platformMetrics.getExecutionStatusCount().getCount().get(ALL.name()).getExecutionStatusCount());
        assertEquals(1, platformMetrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()).getExecutionStatusCount());
        assertFalse(platformMetrics.getExecutionStatusCount().getCount().containsKey(FAILED_RUNTIME_INVALID.name()));
        assertFalse(platformMetrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        // Check for ALL statuses
        MetricsByStatus platformAllStatusesMetrics = platformMetrics.getExecutionStatusCount().getCount().get(ALL.name());
        assertEquals(1, platformAllStatusesMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platformAllStatusesMetrics.getCpu().getMinimum());
        assertEquals(2, platformAllStatusesMetrics.getCpu().getMaximum());
        assertEquals(2, platformAllStatusesMetrics.getCpu().getAverage());
        assertNull(platformAllStatusesMetrics.getCpu().getUnit());

        assertEquals(1, platformAllStatusesMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platformAllStatusesMetrics.getMemory().getMinimum());
        assertEquals(2, platformAllStatusesMetrics.getMemory().getMaximum());
        assertEquals(2, platformAllStatusesMetrics.getMemory().getAverage());
        assertNotNull(platformAllStatusesMetrics.getMemory().getUnit());

        assertEquals(1, platformAllStatusesMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platformAllStatusesMetrics.getCost().getMinimum());
        assertEquals(2, platformAllStatusesMetrics.getCost().getMaximum());
        assertEquals(2, platformAllStatusesMetrics.getCost().getAverage());
        assertNotNull(platformAllStatusesMetrics.getCost().getUnit());

        assertEquals(1, platformAllStatusesMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(300, platformAllStatusesMetrics.getExecutionTime().getMinimum());
        assertEquals(300, platformAllStatusesMetrics.getExecutionTime().getMaximum());
        assertEquals(300, platformAllStatusesMetrics.getExecutionTime().getAverage());
        assertNotNull(platformAllStatusesMetrics.getExecutionTime().getUnit());

        // Check SUCCESSFUL status metrics
        MetricsByStatus platformSuccessfulMetrics = platformMetrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name());
        assertEquals(1, platformSuccessfulMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platformSuccessfulMetrics.getCpu().getMinimum());
        assertEquals(2, platformSuccessfulMetrics.getCpu().getMaximum());
        assertEquals(2, platformSuccessfulMetrics.getCpu().getAverage());
        assertNull(platformSuccessfulMetrics.getCpu().getUnit());

        assertEquals(1, platformSuccessfulMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platformSuccessfulMetrics.getMemory().getMinimum());
        assertEquals(2, platformSuccessfulMetrics.getMemory().getMaximum());
        assertEquals(2, platformSuccessfulMetrics.getMemory().getAverage());
        assertNotNull(platformSuccessfulMetrics.getMemory().getUnit());

        assertEquals(1, platformSuccessfulMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platformSuccessfulMetrics.getCost().getMinimum());
        assertEquals(2, platformSuccessfulMetrics.getCost().getMaximum());
        assertEquals(2, platformSuccessfulMetrics.getCost().getAverage());
        assertNotNull(platformSuccessfulMetrics.getCost().getUnit());

        assertEquals(1, platformSuccessfulMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(300, platformSuccessfulMetrics.getExecutionTime().getMinimum());
        assertEquals(300, platformSuccessfulMetrics.getExecutionTime().getMaximum());
        assertEquals(300, platformSuccessfulMetrics.getExecutionTime().getAverage());
        assertNotNull(platformSuccessfulMetrics.getExecutionTime().getUnit());

        assertEquals(1, platformMetrics.getValidationStatus().getValidatorTools().size());
        final String expectedValidatorTool = submittedValidationExecution.getValidatorTool().toString();
        ValidatorInfo validationInfo = platformMetrics.getValidationStatus().getValidatorTools().get(expectedValidatorTool);
        assertNotNull(validationInfo);
        assertNotNull(validationInfo.getMostRecentVersionName());

        final String expectedMostRecentValidationVersionName = submittedValidationExecution.getValidatorToolVersion();
        ValidatorVersionInfo mostRecentValidationVersionInfo = validationInfo.getValidatorVersions().stream().filter(validationVersion -> expectedMostRecentValidationVersionName.equals(validationVersion.getName())).findFirst().get();
        assertEquals(submittedValidationExecution.isIsValid(), mostRecentValidationVersionInfo.isIsValid());
        assertEquals(expectedMostRecentValidationVersionName, mostRecentValidationVersionInfo.getName());
        assertEquals(1, mostRecentValidationVersionInfo.getNumberOfRuns());
        assertEquals(1, validationInfo.getNumberOfRuns());
        if (submittedValidationExecution.isIsValid()) {
            assertEquals(100d, mostRecentValidationVersionInfo.getPassingRate());
            assertEquals(100d, validationInfo.getPassingRate());
        } else {
            assertEquals(0d, mostRecentValidationVersionInfo.getPassingRate());
            assertEquals(0d, validationInfo.getPassingRate());
        }
    }

    private static void testOverallAggregatedMetrics(WorkflowVersion version, String validatorToolVersion1, String validatorToolVersion2, Metrics platform1Metrics) {
        ValidatorVersionInfo mostRecentValidationVersionInfo;
        ValidatorInfo validationInfo;
        // Verify that the metrics aggregated across ALL platforms are correct
        Metrics overallMetrics = version.getMetricsByPlatform().get(Partner.ALL.name());
        assertNotNull(overallMetrics);
        assertEquals(3, overallMetrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, overallMetrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(4, overallMetrics.getExecutionStatusCount().getCount().get(ALL.name()).getExecutionStatusCount());
        assertEquals(3, overallMetrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()).getExecutionStatusCount());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name()).getExecutionStatusCount());
        assertFalse(overallMetrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        // The CPU values submitted were:
        // SUCCESSFUL: 2, 2, 6
        // FAILED_RUNTIME_INVALID: 4
        MetricsByStatus overallAllStatusesMetrics = overallMetrics.getExecutionStatusCount().getCount().get(ALL.name());
        MetricsByStatus overallSuccessfulMetrics = overallMetrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name());
        MetricsByStatus overallFailedRuntimeInvalidMetrics = overallMetrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name());
        assertEquals(4, overallAllStatusesMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, overallAllStatusesMetrics.getCpu().getMinimum());
        assertEquals(6, overallAllStatusesMetrics.getCpu().getMaximum());
        assertEquals(3.5, overallAllStatusesMetrics.getCpu().getAverage());
        assertNull(overallAllStatusesMetrics.getCpu().getUnit());
        assertEquals(3, overallSuccessfulMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, overallSuccessfulMetrics.getCpu().getMinimum());
        assertEquals(6, overallSuccessfulMetrics.getCpu().getMaximum());
        assertEquals(3.333333333333333, overallSuccessfulMetrics.getCpu().getAverage());
        assertNull(overallSuccessfulMetrics.getCpu().getUnit());
        assertEquals(1, overallFailedRuntimeInvalidMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(4, overallFailedRuntimeInvalidMetrics.getCpu().getMinimum());
        assertEquals(4, overallFailedRuntimeInvalidMetrics.getCpu().getMaximum());
        assertEquals(4, overallFailedRuntimeInvalidMetrics.getCpu().getAverage());
        assertNull(overallFailedRuntimeInvalidMetrics.getCpu().getUnit());

        // The memory values submitted were:
        // SUCCESSFUL: 2, 2, 5.5
        // FAILED_RUNTIME_INVALID: 4.5
        assertEquals(4, overallAllStatusesMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, overallAllStatusesMetrics.getMemory().getMinimum());
        assertEquals(5.5, overallAllStatusesMetrics.getMemory().getMaximum());
        assertEquals(3.5, overallAllStatusesMetrics.getMemory().getAverage());
        assertNotNull(overallAllStatusesMetrics.getMemory().getUnit());
        assertEquals(3, overallSuccessfulMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, overallSuccessfulMetrics.getMemory().getMinimum());
        assertEquals(5.5, overallSuccessfulMetrics.getMemory().getMaximum());
        assertEquals(3.1666666666666665, overallSuccessfulMetrics.getMemory().getAverage());
        assertNotNull(overallSuccessfulMetrics.getMemory().getUnit());
        assertEquals(1, overallFailedRuntimeInvalidMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(4.5, overallFailedRuntimeInvalidMetrics.getMemory().getMinimum());
        assertEquals(4.5, overallFailedRuntimeInvalidMetrics.getMemory().getMaximum());
        assertEquals(4.5, overallFailedRuntimeInvalidMetrics.getMemory().getAverage());
        assertNotNull(overallFailedRuntimeInvalidMetrics.getMemory().getUnit());

        // The execution times submitted were:
        // SUCCESSFUL: PT5M, PT5M, PT11S
        // FAILED_RUNTIME_INVALID: PT1S
        assertEquals(4, overallAllStatusesMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, overallAllStatusesMetrics.getExecutionTime().getMinimum());
        assertEquals(300, overallAllStatusesMetrics.getExecutionTime().getMaximum());
        assertEquals(153, overallAllStatusesMetrics.getExecutionTime().getAverage());
        assertNotNull(overallAllStatusesMetrics.getExecutionTime().getUnit());
        assertEquals(3, overallSuccessfulMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(11, overallSuccessfulMetrics.getExecutionTime().getMinimum());
        assertEquals(300, overallSuccessfulMetrics.getExecutionTime().getMaximum());
        assertEquals(203.66666666666666, overallSuccessfulMetrics.getExecutionTime().getAverage());
        assertNotNull(overallSuccessfulMetrics.getExecutionTime().getUnit());
        assertEquals(1, overallFailedRuntimeInvalidMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, overallFailedRuntimeInvalidMetrics.getExecutionTime().getMinimum());
        assertEquals(1, overallFailedRuntimeInvalidMetrics.getExecutionTime().getMaximum());
        assertEquals(1, overallFailedRuntimeInvalidMetrics.getExecutionTime().getAverage());
        assertNotNull(overallFailedRuntimeInvalidMetrics.getExecutionTime().getUnit());

        assertEquals(2, overallMetrics.getValidationStatus().getValidatorTools().size());
        validationInfo = overallMetrics.getValidationStatus().getValidatorTools().get(MINIWDL.toString());
        assertNotNull(validationInfo);
        assertNotNull(validationInfo.getMostRecentVersionName());
        mostRecentValidationVersionInfo = validationInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion1.equals(validationVersion.getName())).findFirst().get();
        assertFalse(mostRecentValidationVersionInfo.isIsValid(), "Most recent miniwdl validation should be invalid");
        assertEquals(validatorToolVersion1, mostRecentValidationVersionInfo.getName());
        assertEquals(50d, mostRecentValidationVersionInfo.getPassingRate());
        assertEquals(2, mostRecentValidationVersionInfo.getNumberOfRuns());
        assertEquals(50d, validationInfo.getPassingRate());
        assertEquals(2, validationInfo.getNumberOfRuns());

        validationInfo = overallMetrics.getValidationStatus().getValidatorTools().get(WOMTOOL.toString());
        assertNotNull(validationInfo);
        assertNotNull(validationInfo.getMostRecentVersionName());
        // ValidatorToolVersion2 is the most recent because it was created last
        mostRecentValidationVersionInfo = validationInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion2.equals(validationVersion.getName())).findFirst().get();
        assertFalse(mostRecentValidationVersionInfo.isIsValid(), "Most recent miniwdl validation should be invalid");
        assertEquals(validatorToolVersion2, mostRecentValidationVersionInfo.getName());
        assertEquals(0d, mostRecentValidationVersionInfo.getPassingRate());
        assertEquals(1, mostRecentValidationVersionInfo.getNumberOfRuns());
        assertEquals(0d, validationInfo.getPassingRate());
        assertEquals(1, validationInfo.getNumberOfRuns());
    }

    @Test
    void testAggregateMetricsErrors() throws Exception {
        int exitCode = catchSystemExit(() -> MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", "thisdoesntexist"}));
        assertEquals(IO_ERROR, exitCode);
    }

    /**
     * Test that the metrics aggregator takes the newest execution if there are executions with duplicate IDs.
     */
    @Test
    void testAggregateExecutionsWithDuplicateIds() {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        String platform = Partner.TERRA.name();

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertTrue(version.getMetricsByPlatform().isEmpty());

        String id = "#workflow/" + workflow.getFullWorkflowPath();
        String versionId = version.getName();

        // Create a workflow execution, tasks execution, validation execution, and aggregated execution with the same execution ID
        final String executionId = generateExecutionId();
        // Check if metric is aggregated from workflow execution by checking executionTime metric
        RunExecution workflowExecution = createRunExecution(SUCCESSFUL, "PT5M", null, null, null, null);
        workflowExecution.setExecutionId(executionId);
        // Check if metric is aggregated from task executions by checking cpu requirement metric
        TaskExecutions taskExecutions = createTasksExecutions(SUCCESSFUL, null, 2, null, null, null);
        taskExecutions.setExecutionId(executionId);
        // Check if metric is aggregated from validation execution by checking if it has a validation status metric
        ValidationExecution validationExecution = createValidationExecution(MINIWDL, "v1", true);
        validationExecution.setExecutionId(executionId);

        // Try to send all of them in one POST. Should fail because the webservice validates that one submission does not include duplicate IDs
        assertThrows(ApiException.class, () -> extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody()
                .runExecutions(List.of(workflowExecution))
                .taskExecutions(List.of(taskExecutions))
                .validationExecutions(List.of(validationExecution)),
                platform, id, versionId, ""));

        // Send them one at a time. The last execution sent should be the one that the metrics aggregator aggregates
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(List.of(workflowExecution)), platform, id, versionId, "");
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().taskExecutions(List.of(taskExecutions)), platform, id, versionId, "");
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().validationExecutions(List.of(validationExecution)), platform, id, versionId, "");

        MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        // Get workflow version to verify aggregated metrics
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        Metrics metrics = version.getMetricsByPlatform().get(platform);
        assertNotNull(metrics);
        // Should be aggregated from validationExecution because it was submitted last
        MetricsByStatus successfulMetrics = metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name());
        assertNotNull(metrics.getValidationStatus()); // Verify that the metric from validation execution was used
        assertNull(successfulMetrics.getExecutionTime()); // Verify that the metric from workflow execution wasn't used
        assertNull(successfulMetrics.getCpu()); // Verify that the metric from task executions weren't used

        // Submit a workflow execution. The metric should be from the latest workflow execution.
        workflowExecution.setExecutionTime("PT0S"); // Change execution time so it's different from the first workflow execution
        extendedGa4GhApi.executionMetricsPost(new ExecutionsRequestBody().runExecutions(List.of(workflowExecution)), platform, id, versionId, "");
        MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        // Get workflow version to verify aggregated metrics
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        metrics = version.getMetricsByPlatform().get(platform);
        assertNotNull(metrics);
        // Should be aggregated from runExecution because it was submitted last
        successfulMetrics = metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name());
        assertNotNull(successfulMetrics.getExecutionTime());
        assertEquals(0, successfulMetrics.getExecutionTime().getMinimum()); // Verify that the execution time is from the second workflow execution
        assertNull(successfulMetrics.getCpu()); // Verify that the metric from task executions weren't used
        assertNull(metrics.getValidationStatus()); // Verify that the metric from validation execution wasn't used
    }

    @Test
    void testSubmitValidationData() throws IOException {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        final Partner platform = DNA_STACK;
        final ValidationExecution.ValidatorToolEnum validator = MINIWDL;
        final String validatorVersion = "1.0";
        final String executionId = "foobar";

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);

        String id = "#workflow/" + workflow.getFullWorkflowPath();
        String versionId = version.getName();
        // This file contains 1 valid CSV line and 2 invalid CSV lines (one doesn't have all the required columns, and the other has a non-boolean value for the isValid column)
        String successfulDataFilePath = ResourceHelpers.resourceFilePath("miniwdl-successful-validation-workflow-names.csv");

        // Submit validation data using a data file that contains workflow names of workflows that were successfully validated with miniwdl on DNAstack
        MetricsAggregatorClient.main(new String[] {"submit-validation-data", "--config", CONFIG_FILE_PATH, "--validator", validator.toString(), "--validatorVersion", validatorVersion, "--data", successfulDataFilePath, "--platform", platform.toString(), "--executionId", executionId});
        List<MetricsData> metricsDataList = metricsDataS3Client.getMetricsData(id, versionId);
        assertEquals(1, metricsDataList.size());
        MetricsData metricsData = metricsDataList.get(0);
        // Verify the ValidationExecution that was sent to S3
        String metricsDataContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        ExecutionsRequestBody executionsRequestBody = GSON.fromJson(metricsDataContent, ExecutionsRequestBody.class);
        assertTrue(executionsRequestBody.getRunExecutions().isEmpty(), "Should not have run executions");
        assertEquals(1, executionsRequestBody.getValidationExecutions().size(), "Should have 1 validation execution");
        ValidationExecution validationExecution = executionsRequestBody.getValidationExecutions().get(0);
        assertTrue(validationExecution.isIsValid());
        assertEquals(validator, validationExecution.getValidatorTool());
        assertEquals(executionId, validationExecution.getExecutionId());

        LocalStackTestUtilities.deleteBucketContents(s3Client, BUCKET_NAME); // Clear bucket contents to start from scratch

        // Submit validation data using a data file that contains workflow names of workflows that failed validation with miniwdl on DNAstack
        String failedDataFilePath = ResourceHelpers.resourceFilePath("miniwdl-failed-validation-workflow-names.csv");
        MetricsAggregatorClient.main(new String[] {"submit-validation-data", "--config", CONFIG_FILE_PATH, "--validator", validator.toString(), "--validatorVersion", validatorVersion, "--data", failedDataFilePath, "--platform", platform.toString(), "--executionId", executionId});
        metricsDataList = metricsDataS3Client.getMetricsData(id, versionId);
        assertEquals(1, metricsDataList.size());
        metricsData = metricsDataList.get(0);
        // Verify the ValidationExecution that was sent to S3
        metricsDataContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        executionsRequestBody = GSON.fromJson(metricsDataContent, ExecutionsRequestBody.class);
        assertTrue(executionsRequestBody.getRunExecutions().isEmpty(), "Should not have run executions");
        assertEquals(1, executionsRequestBody.getValidationExecutions().size(), "Should have 1 validation execution");
        validationExecution = executionsRequestBody.getValidationExecutions().get(0);
        assertFalse(validationExecution.isIsValid());
        assertEquals(validator, validationExecution.getValidatorTool());
    }

    @Test
    void testSubmitTerraMetrics() throws IOException {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);

        String id = "#workflow/" + workflow.getFullWorkflowPath();
        String versionId = version.getName();
        // This file contains 3 valid rows of executions: 1 failed, 1 successful, and 1 aborted.
        // It also contains 2 invalid rows of executions: 1 where workflow_start is missing, and one where the source_url is invalid
        String terraMetricsFilePath = ResourceHelpers.resourceFilePath("terra-metrics.csv");

        // Submit Terra metrics using a CSV file that metrics of workflows executed on Terra
        MetricsAggregatorClient.main(new String[] {"submit-terra-metrics", "--config", CONFIG_FILE_PATH, "--data", terraMetricsFilePath});
        List<MetricsData> metricsDataList = metricsDataS3Client.getMetricsData(id, versionId);
        assertEquals(1, metricsDataList.size()); // There should be 1 file in S3.
        MetricsData metricsData = metricsDataList.get(0);
        // Verify the metrics that were sent to S3
        String metricsDataContent = metricsDataS3Client.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        ExecutionsRequestBody executionsRequestBody = GSON.fromJson(metricsDataContent, ExecutionsRequestBody.class);
        assertTrue(executionsRequestBody.getValidationExecutions().isEmpty(), "Should not have validation executions");
        List<RunExecution> terraWorkflowExecutions = executionsRequestBody.getRunExecutions();
        assertEquals(3, terraWorkflowExecutions.size(), "Should have 3 workflow executions submitted");
        assertTrue(terraWorkflowExecutions.stream().anyMatch(execution -> execution.getExecutionStatus() == SUCCESSFUL));
        assertTrue(terraWorkflowExecutions.stream().anyMatch(execution -> execution.getExecutionStatus() == ABORTED));
        assertTrue(terraWorkflowExecutions.stream().anyMatch(execution -> execution.getExecutionStatus() == FAILED));
    }
}
