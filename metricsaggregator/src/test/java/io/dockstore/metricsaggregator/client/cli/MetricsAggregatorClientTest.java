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

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.gson.Gson;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationInfo;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import io.dockstore.webservice.core.metrics.MetricsData;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
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

import static io.dockstore.client.cli.BaseIT.ADMIN_USERNAME;
import static io.dockstore.metricsaggregator.common.TestUtilities.BUCKET_NAME;
import static io.dockstore.metricsaggregator.common.TestUtilities.CONFIG_FILE_PATH;
import static io.dockstore.metricsaggregator.common.TestUtilities.ENDPOINT_OVERRIDE;
import static io.dockstore.metricsaggregator.common.TestUtilities.createRunExecution;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_RUNTIME_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_SEMANTIC_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.SUCCESSFUL;
import static io.dockstore.openapi.client.model.ValidationExecution.ValidatorToolEnum.MINIWDL;
import static io.dockstore.webservice.core.Partner.DNA_STACK;
import static org.junit.jupiter.api.Assertions.*;
import static uk.org.webcompere.systemstubs.SystemStubs.catchSystemExit;

@ExtendWith({LocalstackDockerExtension.class, SystemStubsExtension.class})
@LocalstackDockerProperties(imageTag = LocalStackTestUtilities.IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
class MetricsAggregatorClientTest {
    private static S3Client s3Client;
    private static TestingPostgres testingPostgres;
    private static MetricsDataS3Client metricsDataS3Client;
    private static Gson GSON = new Gson();

    public static DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
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

        // A successful run execution that ran for 5 minutes, requires 2 CPUs and 2 GBs of memory
        List<RunExecution> runExecutions = List.of(createRunExecution(SUCCESSFUL, "PT5M", 2, 2.0));
        // A successful miniwdl validation
        List<ValidationExecution> validationExecutions = List.of(new ValidationExecution()
                .validatorTool(MINIWDL)
                .validatorToolVersion("1.0")
                .isValid(true)
                .dateExecuted(Instant.now().toString()));

        // Submit metrics for two platforms
        ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody().runExecutions(runExecutions).validationExecutions(validationExecutions);
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform1, id, versionId, "");
        extendedGa4GhApi.executionMetricsPost(executionsRequestBody, platform2, id, versionId, "");
        int expectedNumberOfPlatforms = 2;
        // Aggregate metrics
        MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        // Get workflow version to verify aggregated metrics
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertEquals(expectedNumberOfPlatforms, version.getMetricsByPlatform().size(), "There should be metrics for two platforms");
        Metrics platform1Metrics = version.getMetricsByPlatform().get(platform1);
        assertNotNull(platform1Metrics);

        // Verify that the aggregated metrics are the same as the single execution for platform1
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(0, platform1Metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(1, platform1Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()));
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_RUNTIME_INVALID.name()));
        assertFalse(platform1Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));
        assertTrue(platform1Metrics.getExecutionStatusCount().isValid());

        assertEquals(1, platform1Metrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getCpu().getMinimum());
        assertEquals(2, platform1Metrics.getCpu().getMaximum());
        assertEquals(2, platform1Metrics.getCpu().getAverage());
        assertNull(platform1Metrics.getCpu().getUnit());

        assertEquals(1, platform1Metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getMemory().getMinimum());
        assertEquals(2, platform1Metrics.getMemory().getMaximum());
        assertEquals(2, platform1Metrics.getMemory().getAverage());
        assertEquals(MemoryStatisticMetric.UNIT, platform1Metrics.getMemory().getUnit());

        assertEquals(1, platform1Metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(300, platform1Metrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1Metrics.getExecutionTime().getMaximum());
        assertEquals(300, platform1Metrics.getExecutionTime().getAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, platform1Metrics.getExecutionTime().getUnit());

        assertEquals(1, platform1Metrics.getValidationStatus().getValidatorToolToIsValid().size());
        ValidationInfo validationInfo = platform1Metrics.getValidationStatus().getValidatorToolToIsValid().get(MINIWDL.toString());
        assertNotNull(validationInfo);
        assertTrue(validationInfo.isMostRecentIsValid(), "miniwdl validation should be valid");
        assertEquals("1.0", validationInfo.getMostRecentVersion());
        assertEquals(List.of("1.0"), validationInfo.getSuccessfulValidationVersions());
        assertTrue(validationInfo.getFailedValidationVersions().isEmpty());
        assertEquals(100d, validationInfo.getPassingRate());
        assertEquals(1, validationInfo.getNumberOfRuns());

        Metrics platform2Metrics = version.getMetricsByPlatform().get(platform2);
        assertNotNull(platform1Metrics);

        // Verify that the aggregated metrics are the same as the single execution for platform2
        assertEquals(1, platform2Metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(0, platform2Metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertEquals(1, platform2Metrics.getExecutionStatusCount().getCount().get(SUCCESSFUL.name()));
        assertFalse(platform2Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_RUNTIME_INVALID.name()));
        assertFalse(platform2Metrics.getExecutionStatusCount().getCount().containsKey(FAILED_SEMANTIC_INVALID.name()));
        assertTrue(platform2Metrics.getExecutionStatusCount().isValid());

        assertEquals(1, platform2Metrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform2Metrics.getCpu().getMinimum());
        assertEquals(2, platform2Metrics.getCpu().getMaximum());
        assertEquals(2, platform2Metrics.getCpu().getAverage());
        assertNull(platform2Metrics.getCpu().getUnit());

        assertEquals(1, platform2Metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform2Metrics.getMemory().getMinimum());
        assertEquals(2, platform2Metrics.getMemory().getMaximum());
        assertEquals(2, platform2Metrics.getMemory().getAverage());
        assertEquals(MemoryStatisticMetric.UNIT, platform2Metrics.getMemory().getUnit());

        assertEquals(1, platform2Metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(300, platform2Metrics.getExecutionTime().getMinimum());
        assertEquals(300, platform2Metrics.getExecutionTime().getMaximum());
        assertEquals(300, platform2Metrics.getExecutionTime().getAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, platform2Metrics.getExecutionTime().getUnit());

        assertEquals(1, platform2Metrics.getValidationStatus().getValidatorToolToIsValid().size());
        validationInfo = platform2Metrics.getValidationStatus().getValidatorToolToIsValid().get(MINIWDL.toString());
        assertNotNull(validationInfo);
        assertTrue(validationInfo.isMostRecentIsValid(), "miniwdl validation should be valid");
        assertEquals("1.0", validationInfo.getMostRecentVersion());
        assertEquals(List.of("1.0"), validationInfo.getSuccessfulValidationVersions());
        assertTrue(validationInfo.getFailedValidationVersions().isEmpty());
        assertEquals(100d, validationInfo.getPassingRate());
        assertEquals(1, validationInfo.getNumberOfRuns());

        // A failed run execution that ran for 1 second, requires 2 CPUs and 4.5 GBs of memory
        runExecutions = List.of(createRunExecution(FAILED_RUNTIME_INVALID, "PT1S", 4, 4.5));
        // A failed miniwdl validation for the same validator version
        validationExecutions = List.of(new ValidationExecution().validatorTool(MINIWDL).validatorToolVersion("1.0").isValid(false).dateExecuted(Instant.now().toString()));
        executionsRequestBody = new ExecutionsRequestBody().runExecutions(runExecutions).validationExecutions(validationExecutions);
        // Submit metrics for the same workflow version for platform 2
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
        assertFalse(platform1Metrics.getExecutionStatusCount().isValid());

        assertEquals(2, platform1Metrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getCpu().getMinimum());
        assertEquals(4, platform1Metrics.getCpu().getMaximum());
        assertEquals(3, platform1Metrics.getCpu().getAverage());
        assertNull(platform1Metrics.getCpu().getUnit());

        assertEquals(2, platform1Metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, platform1Metrics.getMemory().getMinimum());
        assertEquals(4.5, platform1Metrics.getMemory().getMaximum());
        assertEquals(3.25, platform1Metrics.getMemory().getAverage());
        assertEquals(MemoryStatisticMetric.UNIT, platform1Metrics.getMemory().getUnit());

        assertEquals(2, platform1Metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, platform1Metrics.getExecutionTime().getMinimum());
        assertEquals(300, platform1Metrics.getExecutionTime().getMaximum());
        assertEquals(150.5, platform1Metrics.getExecutionTime().getAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, platform1Metrics.getExecutionTime().getUnit());

        assertEquals(1, platform1Metrics.getValidationStatus().getValidatorToolToIsValid().size());
        validationInfo = platform1Metrics.getValidationStatus().getValidatorToolToIsValid().get(MINIWDL.toString());
        assertNotNull(validationInfo);
        assertFalse(validationInfo.isMostRecentIsValid(), "miniwdl validation should be invalid");
        assertEquals("1.0", validationInfo.getMostRecentVersion());
        assertEquals(List.of("1.0"), validationInfo.getFailedValidationVersions());
        assertTrue(validationInfo.getSuccessfulValidationVersions().isEmpty());
        assertEquals(50d, validationInfo.getPassingRate());
        assertEquals(2, validationInfo.getNumberOfRuns());
    }

    @Test
    void testAggregateMetricsErrors() throws Exception {
        int exitCode = catchSystemExit(() -> MetricsAggregatorClient.main(new String[] {"aggregate-metrics", "--config", "thisdoesntexist"}));
        assertEquals(MetricsAggregatorClient.FAILURE_EXIT_CODE, exitCode);
    }

    @Test
    void testSubmitValidationData() throws IOException {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        final Partner platform = DNA_STACK;
        final ValidationExecution.ValidatorToolEnum validator = MINIWDL;
        final String validatorVersion = "1.0";
        final String dateExecuted = Instant.now().toString();

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);

        String id = "#workflow/" + workflow.getFullWorkflowPath();
        String versionId = version.getName();
        String dataFilePath = ResourceHelpers.resourceFilePath("miniwdl-validation-workflow-names.txt");

        // Submit validation data using a data file that contains workflow names of workflows that were successfully validated with miniwdl on DNAstack
        MetricsAggregatorClient.main(new String[] {"submit-validation-data", "--config", CONFIG_FILE_PATH, "--validator", validator.toString(), "--validatorVersion", validatorVersion, "--data", dataFilePath,
            "--successful", "--platform", platform.toString(), "--dateExecuted", dateExecuted});
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
        MetricsAggregatorClient.main(new String[] {"submit-validation-data", "--config", CONFIG_FILE_PATH, "--validator", validator.toString(), "--validatorVersion", validatorVersion, "--data", dataFilePath,
                "--platform", platform.toString(), "--dateExecuted", dateExecuted});
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
    void testSubmitValidationDataErrors() throws Exception {
        final String dataFilePath = ResourceHelpers.resourceFilePath("miniwdl-validation-workflow-names.txt");
        // Verify that providing a date that is not in ISO 8601 UTC date format fails
        int exitCode = catchSystemExit(() -> MetricsAggregatorClient.main(new String[] {"submit-validation-data", "--config", CONFIG_FILE_PATH, "--validator", MINIWDL.toString(), "--validatorVersion", "1.0", "--data", dataFilePath,
                "--successful", "--platform", DNA_STACK.toString(), "--dateExecuted", "January 1, 2023"}));
        assertEquals(MetricsAggregatorClient.FAILURE_EXIT_CODE, exitCode);
    }
}
