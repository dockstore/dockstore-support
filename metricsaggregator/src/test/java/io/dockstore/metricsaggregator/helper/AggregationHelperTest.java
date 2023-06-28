package io.dockstore.metricsaggregator.helper;

import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_RUNTIME_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.FAILED_SEMANTIC_INVALID;
import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.openapi.client.model.CostMetric;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.ValidatorInfo;
import io.dockstore.openapi.client.model.ValidatorVersionInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AggregationHelperTest {

    @Test
    void testGetAggregatedExecutionStatus() {
        ExecutionsRequestBody allSubmissions = new ExecutionsRequestBody();
        Optional<ExecutionStatusMetric> executionStatusMetric = AggregationHelper.getAggregatedExecutionStatus(allSubmissions);
        assertTrue(executionStatusMetric.isEmpty());

        RunExecution submittedRunExecution = new RunExecution().executionStatus(SUCCESSFUL);
        allSubmissions = new ExecutionsRequestBody().runExecutions(List.of(submittedRunExecution));
        executionStatusMetric = AggregationHelper.getAggregatedExecutionStatus(allSubmissions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()));

        // Aggregate submissions containing run executions and aggregated metrics
        Metrics submittedAggregatedMetrics = new Metrics().executionStatusCount(
                new ExecutionStatusMetric().count(
                        Map.of(SUCCESSFUL.toString(), 10, FAILED_RUNTIME_INVALID.toString(), 1)));
        allSubmissions = new ExecutionsRequestBody().runExecutions(List.of(submittedRunExecution)).aggregatedExecutions(List.of(submittedAggregatedMetrics));
        executionStatusMetric = AggregationHelper.getAggregatedExecutionStatus(allSubmissions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(11, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()));
        assertEquals(1, executionStatusMetric.get().getCount().get(FAILED_RUNTIME_INVALID.toString()));
        assertNull(executionStatusMetric.get().getCount().get(FAILED_SEMANTIC_INVALID.toString()), "Should be null because the key doesn't exist in the count map");
    }

    @Test
    void testGetAggregatedExecutionTime() {
        List<RunExecution> badExecutions = new ArrayList<>();
        Optional<ExecutionTimeMetric> executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(new ExecutionsRequestBody().runExecutions(badExecutions));
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution that doesn't have execution time data
        badExecutions.add(new RunExecution().executionStatus(SUCCESSFUL));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(new ExecutionsRequestBody().runExecutions(badExecutions));
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution with malformed execution time data
        badExecutions.add(new RunExecution().executionStatus(SUCCESSFUL).executionTime("1 second"));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(new ExecutionsRequestBody().runExecutions(badExecutions));
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution with execution time
        final int timeInSeconds = 10;
        List<RunExecution> executions = List.of(new RunExecution().executionStatus(SUCCESSFUL).executionTime(String.format("PT%dS", timeInSeconds)));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(executionTimeMetric.isPresent());
        assertEquals(timeInSeconds, executionTimeMetric.get().getMinimum());
        assertEquals(timeInSeconds, executionTimeMetric.get().getMaximum());
        assertEquals(timeInSeconds, executionTimeMetric.get().getAverage());
        assertEquals(1, executionTimeMetric.get().getNumberOfDataPointsForAverage());

        // Aggregate submissions containing run executions and aggregated metrics
        Metrics submittedAggregatedMetrics = new Metrics()
                .executionTime(new ExecutionTimeMetric()
                        .minimum(2.0)
                        .maximum(6.0)
                        .average(4.0)
                        .numberOfDataPointsForAverage(2));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(new ExecutionsRequestBody().runExecutions(executions).aggregatedExecutions(List.of(submittedAggregatedMetrics)));
        assertTrue(executionTimeMetric.isPresent());
        assertEquals(2.0, executionTimeMetric.get().getMinimum());
        assertEquals(10.0, executionTimeMetric.get().getMaximum());
        assertEquals(6, executionTimeMetric.get().getAverage());
        assertEquals(3, executionTimeMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedCpu() {
        List<RunExecution> executions = new ArrayList<>();
        Optional<CpuMetric> cpuMetric = AggregationHelper.getAggregatedCpu(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(cpuMetric.isEmpty());

        // Add an execution that doesn't have cpu data
        executions.add(new RunExecution().executionStatus(SUCCESSFUL));
        cpuMetric = AggregationHelper.getAggregatedCpu(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(cpuMetric.isEmpty());

        // Add an execution with cpu data
        final int cpu = 1;
        executions.add(new RunExecution().executionStatus(SUCCESSFUL).cpuRequirements(cpu));
        cpuMetric = AggregationHelper.getAggregatedCpu(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(cpuMetric.isPresent());
        assertEquals(cpu, cpuMetric.get().getMinimum());
        assertEquals(cpu, cpuMetric.get().getMaximum());
        assertEquals(cpu, cpuMetric.get().getAverage());
        assertEquals(1, cpuMetric.get().getNumberOfDataPointsForAverage());

        // Aggregate submissions containing run executions and aggregated metrics
        Metrics submittedAggregatedMetrics = new Metrics()
                .cpu(new CpuMetric()
                        .minimum(2.0)
                        .maximum(6.0)
                        .average(4.0)
                        .numberOfDataPointsForAverage(2));
        cpuMetric = AggregationHelper.getAggregatedCpu(new ExecutionsRequestBody().runExecutions(executions).aggregatedExecutions(List.of(submittedAggregatedMetrics)));
        assertTrue(cpuMetric.isPresent());
        assertEquals(1.0, cpuMetric.get().getMinimum());
        assertEquals(6.0, cpuMetric.get().getMaximum());
        assertEquals(3, cpuMetric.get().getAverage());
        assertEquals(3, cpuMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedMemory() {
        List<RunExecution> executions = new ArrayList<>();
        Optional<MemoryMetric> memoryMetric = AggregationHelper.getAggregatedMemory(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(memoryMetric.isEmpty());

        // Add an execution that doesn't have memory data
        executions.add(new RunExecution().executionStatus(SUCCESSFUL));
        memoryMetric = AggregationHelper.getAggregatedMemory(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(memoryMetric.isEmpty());

        // Add an execution with memory data
        Double memoryInGB = 2.0;
        executions.add(new RunExecution().executionStatus(SUCCESSFUL).memoryRequirementsGB(memoryInGB));
        memoryMetric = AggregationHelper.getAggregatedMemory(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(memoryMetric.isPresent());
        assertEquals(memoryInGB, memoryMetric.get().getMinimum());
        assertEquals(memoryInGB, memoryMetric.get().getMaximum());
        assertEquals(memoryInGB, memoryMetric.get().getAverage());
        assertEquals(1, memoryMetric.get().getNumberOfDataPointsForAverage());

        // Aggregate submissions containing run executions and aggregated metrics
        Metrics submittedAggregatedMetrics = new Metrics()
                .memory(new MemoryMetric()
                        .minimum(2.0)
                        .maximum(6.0)
                        .average(4.0)
                        .numberOfDataPointsForAverage(2));
        memoryMetric = AggregationHelper.getAggregatedMemory(new ExecutionsRequestBody().runExecutions(executions).aggregatedExecutions(List.of(submittedAggregatedMetrics)));
        assertTrue(memoryMetric.isPresent());
        assertEquals(2.0, memoryMetric.get().getMinimum());
        assertEquals(6.0, memoryMetric.get().getMaximum());
        assertEquals(3.333333333333333, memoryMetric.get().getAverage());
        assertEquals(3, memoryMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedCost() {
        List<RunExecution> executions = new ArrayList<>();
        Optional<CostMetric> costMetric = AggregationHelper.getAggregatedCost(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(costMetric.isEmpty());

        // Add an execution that doesn't have cost data
        executions.add(new RunExecution().executionStatus(SUCCESSFUL));
        costMetric = AggregationHelper.getAggregatedCost(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(costMetric.isEmpty());

        // Add an execution with cost data
        Double costInUSD = 2.00;
        executions.add(new RunExecution().executionStatus(SUCCESSFUL).costUSD(costInUSD));
        costMetric = AggregationHelper.getAggregatedCost(new ExecutionsRequestBody().runExecutions(executions));
        assertTrue(costMetric.isPresent());
        assertEquals(costInUSD, costMetric.get().getMinimum());
        assertEquals(costInUSD, costMetric.get().getMaximum());
        assertEquals(costInUSD, costMetric.get().getAverage());
        assertEquals(1, costMetric.get().getNumberOfDataPointsForAverage());

        // Aggregate submissions containing run executions and aggregated metrics
        Metrics submittedAggregatedMetrics = new Metrics()
                .cost(new CostMetric()
                        .minimum(2.00)
                        .maximum(6.00)
                        .average(4.00)
                        .numberOfDataPointsForAverage(2));
        costMetric = AggregationHelper.getAggregatedCost(new ExecutionsRequestBody().runExecutions(executions).aggregatedExecutions(List.of(submittedAggregatedMetrics)));
        assertTrue(costMetric.isPresent());
        assertEquals(2.0, costMetric.get().getMinimum());
        assertEquals(6.0, costMetric.get().getMaximum());
        assertEquals(3.333333333333333, costMetric.get().getAverage());
        assertEquals(3, costMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedValidationStatus() {
        List<ValidationExecution> executions = new ArrayList<>();
        Optional<ValidationStatusMetric> validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isEmpty());

        // Add an execution with validation data
        final ValidationExecution.ValidatorToolEnum validatorTool = ValidationExecution.ValidatorToolEnum.MINIWDL;
        final String validatorToolVersion1 = "1.0";
        executions.add(new ValidationExecution()
                .validatorTool(validatorTool)
                .validatorToolVersion(validatorToolVersion1)
                .isValid(true)
                .dateExecuted(Instant.now().toString()));
        validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isPresent());
        ValidatorInfo validatorInfo = validationStatusMetric.get().getValidatorTools().get(validatorTool.toString());
        assertNotNull(validatorInfo);
        assertNotNull(validatorInfo.getMostRecentVersionName());
        ValidatorVersionInfo mostRecentValidatorVersion = validatorInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion1.equals(validationVersion.getName())).findFirst().get();
        assertTrue(mostRecentValidatorVersion.isIsValid());
        assertEquals(validatorToolVersion1, mostRecentValidatorVersion.getName());
        assertNull(mostRecentValidatorVersion.getErrorMessage());
        assertEquals(1, mostRecentValidatorVersion.getNumberOfRuns());
        assertEquals(100, mostRecentValidatorVersion.getPassingRate());
        assertEquals(1, validatorInfo.getValidatorVersions().size(), "There should be 2 ValidatorVersionInfo objects because 1 version was ran");
        assertEquals(1, validatorInfo.getNumberOfRuns());
        assertEquals(100, validatorInfo.getPassingRate());

        // Add an execution that isn't valid for the same validator
        final String validatorToolVersion2 = "2.0";
        executions.add(new ValidationExecution()
                .validatorTool(validatorTool)
                .validatorToolVersion(validatorToolVersion2)
                .isValid(false)
                .dateExecuted(Instant.now().toString())
                .errorMessage("This is an error message"));
        validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isPresent());
        validatorInfo = validationStatusMetric.get().getValidatorTools().get(validatorTool.toString());
        mostRecentValidatorVersion = validatorInfo.getValidatorVersions().stream().filter(validatorVersion -> validatorToolVersion2.equals(validatorVersion.getName())).findFirst().get();
        assertFalse(mostRecentValidatorVersion.isIsValid());
        assertEquals(validatorToolVersion2, mostRecentValidatorVersion.getName());
        assertEquals("This is an error message", mostRecentValidatorVersion.getErrorMessage());
        assertEquals(1, mostRecentValidatorVersion.getNumberOfRuns());
        assertEquals(0, mostRecentValidatorVersion.getPassingRate());
        assertEquals(2, validatorInfo.getValidatorVersions().size(), "There should be 2 ValidatorVersionInfo objects because 2 versions were ran");
        assertEquals(2, validatorInfo.getNumberOfRuns());
        assertEquals(50, validatorInfo.getPassingRate());

        // Add an execution that is valid for the same validator
        String expectedDateExecuted = Instant.now().toString();
        executions.add(new ValidationExecution()
                .validatorTool(validatorTool)
                .validatorToolVersion(validatorToolVersion1)
                .isValid(true)
                .dateExecuted(expectedDateExecuted));
        validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(new ExecutionsRequestBody().validationExecutions(executions));
        assertTrue(validationStatusMetric.isPresent());
        validatorInfo = validationStatusMetric.get().getValidatorTools().get(validatorTool.toString());
        mostRecentValidatorVersion = validatorInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion1.equals(validationVersion.getName())).findFirst().get();
        assertTrue(mostRecentValidatorVersion.isIsValid(), "Should be true because the latest validation is valid");
        assertEquals(validatorToolVersion1, mostRecentValidatorVersion.getName());
        assertNull(mostRecentValidatorVersion.getErrorMessage());
        assertEquals(2, mostRecentValidatorVersion.getNumberOfRuns());
        assertEquals(100, mostRecentValidatorVersion.getPassingRate());
        assertEquals(expectedDateExecuted, mostRecentValidatorVersion.getDateExecuted()); // Check that this is the most recent ValidatorVersionInfo for this version because it was executed twice
        assertEquals(2, validatorInfo.getValidatorVersions().size(), "There should be 2 ValidatorVersionInfo objects because 2 versions ran");
        assertEquals(3, validatorInfo.getNumberOfRuns());
        assertEquals(66.66666666666666, validatorInfo.getPassingRate());

        // Aggregate submissions containing run executions and aggregated metrics
        expectedDateExecuted = Instant.now().toString();
        ValidatorVersionInfo validationVersionInfo = new ValidatorVersionInfo()
                .name(validatorToolVersion1)
                .isValid(true)
                .passingRate(100d)
                .numberOfRuns(4)
                .dateExecuted(expectedDateExecuted);
        Metrics submittedAggregatedMetrics = new Metrics()
                .validationStatus(new ValidationStatusMetric().validatorTools(
                        Map.of(validatorTool.toString(), new ValidatorInfo()
                                .validatorVersions(List.of(validationVersionInfo))
                                .numberOfRuns(4)
                                .passingRate(100d))));

        validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(new ExecutionsRequestBody().validationExecutions(executions).aggregatedExecutions(List.of(submittedAggregatedMetrics)));
        assertTrue(validationStatusMetric.isPresent());
        validatorInfo = validationStatusMetric.get().getValidatorTools().get(validatorTool.toString());
        mostRecentValidatorVersion = validatorInfo.getValidatorVersions().stream().filter(validationVersion -> validatorToolVersion1.equals(validationVersion.getName())).findFirst().get();
        assertTrue(mostRecentValidatorVersion.isIsValid(), "Should be true because the latest validation is valid");
        assertEquals(validatorToolVersion1, mostRecentValidatorVersion.getName());
        assertNull(mostRecentValidatorVersion.getErrorMessage());
        assertEquals(4, mostRecentValidatorVersion.getNumberOfRuns());
        assertEquals(100, mostRecentValidatorVersion.getPassingRate());
        assertEquals(expectedDateExecuted, mostRecentValidatorVersion.getDateExecuted()); // Check that this is the most recent ValidatorVersionInfo for this version because it was executed more than once
        assertEquals(2, validatorInfo.getValidatorVersions().size(), "There should be 2 ValidatorVersionInfo objects because 2 versions were ran");
        assertEquals(7, validatorInfo.getNumberOfRuns());
        assertEquals(85.71428571428571, validatorInfo.getPassingRate());
    }
}
