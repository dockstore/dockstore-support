package io.dockstore.metricsaggregator;

import java.io.File;
import java.util.List;
import java.util.Optional;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.common.TestingPostgres;
import io.dockstore.metricsaggregator.client.cli.Client;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.Execution.ExecutionStatusEnum;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Partner;
import io.dockstore.webservice.core.metrics.ExecutionTimeStatisticMetric;
import io.dockstore.webservice.core.metrics.MemoryStatisticMetric;
import io.dockstore.webservice.helpers.S3ClientHelper;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.configuration2.INIConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import static io.dockstore.client.cli.BaseIT.ADMIN_USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(LocalstackDockerExtension.class)
@LocalstackDockerProperties(imageTag = LocalStackTestUtilities.IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
class MetricsS3ClientTest {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsS3ClientTest.class);
    private static final String CONFIG_FILE_PATH = ResourceHelpers.resourceFilePath("metrics-aggregator.config");
    private static final MetricsAggregatorConfig METRICS_AGGREGATOR_CONFIG = getMetricsConfig();
    private static final String BUCKET_NAME = METRICS_AGGREGATOR_CONFIG.getS3Bucket();
    private static final String ENDPOINT_OVERRIDE = METRICS_AGGREGATOR_CONFIG.getS3EndpointOverride();
    private static MetricsS3Client metricsS3Client;
    private static S3Client s3Client;
    private static TestingPostgres testingPostgres;

    public static DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @BeforeAll
    public static void setup() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);

        metricsS3Client = new MetricsS3Client(BUCKET_NAME, ENDPOINT_OVERRIDE);

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
    void testAggregateMetrics() {
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

        // A successful execution that ran for 5 minutes, requires 2 CPUs and 2 GBs of memory
        Execution execution = createExecution(ExecutionStatusEnum.SUCCESSFUL, "PT5M", 2, "2 GB");

        extendedGa4GhApi.executionMetricsPost(List.of(execution), platform, id, versionId, "");
        Client.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertEquals(1, version.getMetricsByPlatform().size());
        Metrics metrics = version.getMetricsByPlatform().get(platform);
        assertNotNull(metrics);

        // Verify that the aggregated metrics are the same as the single execution
        assertEquals(1, metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(0, metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertTrue(metrics.getExecutionStatusCount().isValid());

        assertEquals(1, metrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, metrics.getCpu().getMinimum());
        assertEquals(2, metrics.getCpu().getMaximum());
        assertEquals(2, metrics.getCpu().getAverage());
        assertNull(metrics.getCpu().getUnit());

        assertEquals(1, metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, metrics.getMemory().getMinimum());
        assertEquals(2, metrics.getMemory().getMaximum());
        assertEquals(2, metrics.getMemory().getAverage());
        assertEquals(MemoryStatisticMetric.UNIT, metrics.getMemory().getUnit());

        assertEquals(1, metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(300, metrics.getExecutionTime().getMinimum());
        assertEquals(300, metrics.getExecutionTime().getMaximum());
        assertEquals(300, metrics.getExecutionTime().getAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, metrics.getExecutionTime().getUnit());

        // A failed execution that ran for 1 second, requires 2 CPUs and 4.5 GBs of memory
        execution = createExecution(ExecutionStatusEnum.FAILED_RUNTIME_INVALID, "PT1S", 4, "4.5 GB");

        extendedGa4GhApi.executionMetricsPost(List.of(execution), platform, id, versionId, "");
        Client.main(new String[] {"aggregate-metrics", "--config", CONFIG_FILE_PATH});
        workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        assertEquals(1, version.getMetricsByPlatform().size());
        metrics = version.getMetricsByPlatform().get(platform);

        // This version now has two execution metrics data for it. Verify that the aggregated metrics are correct
        assertEquals(1, metrics.getExecutionStatusCount().getNumberOfSuccessfulExecutions());
        assertEquals(1, metrics.getExecutionStatusCount().getNumberOfFailedExecutions());
        assertFalse(metrics.getExecutionStatusCount().isValid());

        assertEquals(2, metrics.getCpu().getNumberOfDataPointsForAverage());
        assertEquals(2, metrics.getCpu().getMinimum());
        assertEquals(4, metrics.getCpu().getMaximum());
        assertEquals(3, metrics.getCpu().getAverage());
        assertNull(metrics.getCpu().getUnit());

        assertEquals(2, metrics.getMemory().getNumberOfDataPointsForAverage());
        assertEquals(2, metrics.getMemory().getMinimum());
        assertEquals(4.5, metrics.getMemory().getMaximum());
        assertEquals(3.25, metrics.getMemory().getAverage());
        assertEquals(MemoryStatisticMetric.UNIT, metrics.getMemory().getUnit());

        assertEquals(2, metrics.getExecutionTime().getNumberOfDataPointsForAverage());
        assertEquals(1, metrics.getExecutionTime().getMinimum());
        assertEquals(300, metrics.getExecutionTime().getMaximum());
        assertEquals(150.5, metrics.getExecutionTime().getAverage());
        assertEquals(ExecutionTimeStatisticMetric.UNIT, metrics.getExecutionTime().getUnit());
    }

    @Test
    void testGetDirectories() {
        final ApiClient apiClient = CommonTestUtilities.getOpenAPIWebClient(true, ADMIN_USERNAME, testingPostgres);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        String platform1 = Partner.TERRA.name();
        String platform2 = Partner.DNA_STACK.name();

        Workflow workflow = workflowsApi.getPublishedWorkflow(32L, "metrics");
        WorkflowVersion version = workflow.getWorkflowVersions().stream().filter(v -> "master".equals(v.getName())).findFirst().orElse(null);
        assertNotNull(version);
        final String toolId = "#workflow/" + workflow.getFullWorkflowPath();
        final String versionId = version.getName();

        // A successful execution that ran for 5 minutes, requires 2 CPUs and 2 GBs of memory
        Execution execution = createExecution(ExecutionStatusEnum.SUCCESSFUL, "PT5M", 2, "2 GB");
        extendedGa4GhApi.executionMetricsPost(List.of(execution), platform1, toolId, versionId, "");
        extendedGa4GhApi.executionMetricsPost(List.of(execution), platform2, toolId, versionId, "");

        List<String> directories = metricsS3Client.getDirectories();
        assertEquals(2, directories.size());
        assertTrue(directories.contains(String.join("/", S3ClientHelper.convertToolIdToPartialKey(toolId), versionId, platform1 + "/")));
        assertTrue(directories.contains(String.join("/", S3ClientHelper.convertToolIdToPartialKey(toolId), versionId, platform2 + "/")));
    }

    private Execution createExecution(Execution.ExecutionStatusEnum executionStatus, String executionTime, Integer cpuRequirements, String memoryRequirements) {
        return new Execution()
                .executionStatus(executionStatus)
                .executionTime(executionTime)
                .cpuRequirements(cpuRequirements)
                .memoryRequirements(memoryRequirements);
    }

    private static MetricsAggregatorConfig getMetricsConfig() {
        Optional<INIConfiguration> iniConfig = Client.getConfiguration(new File(ResourceHelpers.resourceFilePath("metrics-aggregator.config")));
        if (iniConfig.isEmpty()) {
            throw new RuntimeException("Unable to get config file");
        }

        MetricsAggregatorConfig config = new MetricsAggregatorConfig(iniConfig.get());
        return config;
    }
}