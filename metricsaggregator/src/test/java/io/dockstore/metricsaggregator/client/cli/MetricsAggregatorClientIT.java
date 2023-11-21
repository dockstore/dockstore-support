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
import static io.dockstore.metricsaggregator.common.TestUtilities.createValidationExecution;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_RUNTIME_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_SEMANTIC_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.SUCCESSFUL;
import static io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum.MINIWDL;
import static io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum.WOMTOOL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Cost;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.TaskExecutions;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.utils.CLIConstants;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import java.io.IOException;
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
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()));
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name()));
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        assertEquals(2, platform1Metrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getCpu().getMinimum());
        assertEquals(4, platform1Metrics.getCpu().getMaximum());
        assertEquals(3, platform1Metrics.getCpu().getAverage());
        assertNull(platform1Metrics.getCpu().getUnit());

        assertEquals(2, platform1Metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getMemory().getMinimum());
        assertEquals(4.5, platform1Metrics.getMemory().getMaximum());
        assertEquals(3.25, platform1Metrics.getMemory().getAverage());
        assertNotNull(platform1Metrics.getMemory().getUnit());

        assertEquals(2, platform1Metrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getCost().getMinimum());
        assertEquals(2, platform1Metrics.getCost().getMaximum());
        assertEquals(2, platform1Metrics.getCost().getAverage());
        assertNotNull(platform1Metrics.getCost().getUnit());

        assertEquals(2, platform1Metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, platform1Metrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1Metrics.getExecutionTime().getMaximum());
        assertEquals(150.5, platform1Metrics.getExecutionTime().getAverage());
        assertNotNull(platform1Metrics.getExecutionTime().getUnit());

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

        // Submit two TaskExecutions, each one representing the task metrics for a single workflow execution
        // A successful task execution that ran for 11 seconds, requires 6 CPUs and 5.5 GBs of memory. Signifies that this workflow execution only executed one task
        TaskExecutions taskExecutions = new TaskExecutions().taskExecutions(List.of(createRunExecution(SUCCESSFUL, "PT11S", 6, 5.5, new Cost().value(2.00), "us-central1")));
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
        assertEquals(2, platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()));
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name()));
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        assertEquals(3, platform1Metrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getCpu().getMinimum());
        assertEquals(6, platform1Metrics.getCpu().getMaximum());
        assertEquals(4, platform1Metrics.getCpu().getAverage());
        assertNull(platform1Metrics.getCpu().getUnit());

        assertEquals(3, platform1Metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getMemory().getMinimum());
        assertEquals(5.5, platform1Metrics.getMemory().getMaximum());
        assertEquals(4, platform1Metrics.getMemory().getAverage());
        assertNotNull(platform1Metrics.getMemory().getUnit());

        assertEquals(3, platform1Metrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getCost().getMinimum());
        assertEquals(2, platform1Metrics.getCost().getMaximum());
        assertEquals(2, platform1Metrics.getCost().getAverage());
        assertNotNull(platform1Metrics.getCost().getUnit());

        assertEquals(3, platform1Metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, platform1Metrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1Metrics.getExecutionTime().getMaximum());
        assertEquals(104, platform1Metrics.getExecutionTime().getAverage());
        assertNotNull(platform1Metrics.getExecutionTime().getUnit());

        testOverallAggregatedMetrics(version, validatorToolVersion1, validatorToolVersion2, platform1Metrics);
    }

    private static void assertAggregatedMetricsForPlatform(String platform, WorkflowVersion version, ValidationExecution submittedValidationExecution) {
        Metrics platformMetrics = version.getMetricsByPlatform().get(platform);
        assertNotNull(platformMetrics);

        // Verify that the aggregated metrics are the same as the single execution for the platform
        assertEquals(1, platformMetrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(0, platformMetrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(1, platformMetrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()));
        assertFalse(platformMetrics.getExecutionStatusCount().getCount().containsKey(FAILED_RUNTIME_INVALID.name()));
        assertFalse(platformMetrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        assertEquals(1, platformMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platformMetrics.getCpu().getMinimum());
        assertEquals(2, platformMetrics.getCpu().getMaximum());
        assertEquals(2, platformMetrics.getCpu().getAverage());
        assertNull(platformMetrics.getCpu().getUnit());

        assertEquals(1, platformMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platformMetrics.getMemory().getMinimum());
        assertEquals(2, platformMetrics.getMemory().getMaximum());
        assertEquals(2, platformMetrics.getMemory().getAverage());
        assertNotNull(platformMetrics.getMemory().getUnit());

        assertEquals(1, platformMetrics.getCost().getNumberOfDataPointsForAverage());
        assertEquals(2, platformMetrics.getCost().getMinimum());
        assertEquals(2, platformMetrics.getCost().getMaximum());
        assertEquals(2, platformMetrics.getCost().getAverage());
        assertNotNull(platformMetrics.getCost().getUnit());

        assertEquals(1, platformMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(300, platformMetrics.getExecutionTime().getMinimum());
        assertEquals(300, platformMetrics.getExecutionTime().getMaximum());
        assertEquals(300, platformMetrics.getExecutionTime().getAverage());
        assertNotNull(platformMetrics.getExecutionTime().getUnit());

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
        assertEquals(3, overallMetrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()));
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(FAILED_RUNTIME_INVALID.name()));
        assertFalse(overallMetrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));

        // The CPU values submitted were 2, 2, 4, 6
        assertEquals(4, overallMetrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, overallMetrics.getCpu().getMinimum());
        assertEquals(6, overallMetrics.getCpu().getMaximum());
        assertEquals(3.5, overallMetrics.getCpu().getAverage());
        assertNull(overallMetrics.getCpu().getUnit());

        // The memory values submitted were 2, 2, 4.5, 5.5
        assertEquals(4, overallMetrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, overallMetrics.getMemory().getMinimum());
        assertEquals(5.5, overallMetrics.getMemory().getMaximum());
        assertEquals(3.5, overallMetrics.getMemory().getAverage());
        assertNotNull(overallMetrics.getMemory().getUnit());

        // The execution times submitted were PT5M, PT5M, PT1S, PT11S
        assertEquals(4, overallMetrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, overallMetrics.getExecutionTime().getMinimum());
        assertEquals(300, overallMetrics.getExecutionTime().getMaximum());
        assertEquals(153, overallMetrics.getExecutionTime().getAverage());
        assertNotNull(overallMetrics.getExecutionTime().getUnit());

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
        assertEquals(CLIConstants.FAILURE_EXIT_CODE, exitCode);
    }

    @Test
    void testSubmitValidationData() throws IOException {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        final Partner platform = DNA_STACK;
        final ValidationExecution.ValidatorToolEnum validator = MINIWDL;
        final String validatorVersion = "1.0";

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);

        String id = "#workflow/" + workflow.getFullWorkflowPath();
        String versionId = version.getName();
        // This file contains 1 valid CSV line and 2 invalid CSV lines (one doesn't have all the required columns, and the other has a non-boolean value for the isValid column)
        String successfulDataFilePath = ResourceHelpers.resourceFilePath("miniwdl-successful-validation-workflow-names.csv");

        // Submit validation data using a data file that contains workflow names of workflows that were successfully validated with miniwdl on DNAstack
        MetricsAggregatorClient.main(new String[] {"submit-validation-data", "--config", CONFIG_FILE_PATH, "--validator", validator.toString(), "--validatorVersion", validatorVersion, "--data", successfulDataFilePath, "--platform", platform.toString()});
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

        LocalStackTestUtilities.deleteBucketContents(s3Client, BUCKET_NAME); // Clear bucket contents to start from scratch

        // Submit validation data using a data file that contains workflow names of workflows that failed validation with miniwdl on DNAstack
        String failedDataFilePath = ResourceHelpers.resourceFilePath("miniwdl-failed-validation-workflow-names.csv");
        MetricsAggregatorClient.main(new String[] {"submit-validation-data", "--config", CONFIG_FILE_PATH, "--validator", validator.toString(), "--validatorVersion", validatorVersion, "--data", failedDataFilePath, "--platform", platform.toString()});
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
}
