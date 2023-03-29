package io.dockstore.metricsaggregator.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import org.junit.jupiter.api.Test;

import static io.dockstore.openapi.client.model.RunExecution.ExecutionStatusEnum.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregationHelperTest {

    @Test
    void testGetAggregatedExecutionStatus() {
        List<RunExecution> executions = new ArrayList<>();
        Optional<ExecutionStatusMetric> executionStatusMetric = AggregationHelper.getAggregatedExecutionStatus(executions);
        assertTrue(executionStatusMetric.isEmpty());

        executions.add(new RunExecution().executionStatus(SUCCESSFUL));
        executionStatusMetric = AggregationHelper.getAggregatedExecutionStatus(executions);
        assertTrue(executionStatusMetric.isPresent());
    }

    @Test
    void testGetAggregatedExecutionTime() {
        List<RunExecution> badExecutions = new ArrayList<>();
        Optional<ExecutionTimeMetric> executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(badExecutions);
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution that doesn't have execution time data
        badExecutions.add(new RunExecution().executionStatus(SUCCESSFUL));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(badExecutions);
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution with malformed execution time data
        badExecutions.add(new RunExecution().executionStatus(SUCCESSFUL).executionTime("1 second"));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(badExecutions);
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution with execution time
        final int timeInSeconds = 10;
        List<RunExecution> executions = List.of(new RunExecution().executionStatus(SUCCESSFUL).executionTime(String.format("PT%dS", timeInSeconds)));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(executions);
        assertTrue(executionTimeMetric.isPresent());
        assertEquals(timeInSeconds, executionTimeMetric.get().getMinimum());
        assertEquals(timeInSeconds, executionTimeMetric.get().getMaximum());
        assertEquals(timeInSeconds, executionTimeMetric.get().getAverage());
        assertEquals(1, executionTimeMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedCpu() {
        List<RunExecution> executions = new ArrayList<>();
        Optional<CpuMetric> cpuMetric = AggregationHelper.getAggregatedCpu(executions);
        assertTrue(cpuMetric.isEmpty());

        // Add an execution that doesn't have cpu data
        executions.add(new RunExecution().executionStatus(SUCCESSFUL));
        cpuMetric = AggregationHelper.getAggregatedCpu(executions);
        assertTrue(cpuMetric.isEmpty());

        // Add an execution with cpu data
        final int cpu = 1;
        executions.add(new RunExecution().executionStatus(SUCCESSFUL).cpuRequirements(cpu));
        cpuMetric = AggregationHelper.getAggregatedCpu(executions);
        assertTrue(cpuMetric.isPresent());
        assertEquals(cpu, cpuMetric.get().getMinimum());
        assertEquals(cpu, cpuMetric.get().getMaximum());
        assertEquals(cpu, cpuMetric.get().getAverage());
        assertEquals(1, cpuMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedMemory() {
        List<RunExecution> executions = new ArrayList<>();
        Optional<MemoryMetric> memoryMetric = AggregationHelper.getAggregatedMemory(executions);
        assertTrue(memoryMetric.isEmpty());

        // Add an execution that doesn't have memory data
        executions.add(new RunExecution().executionStatus(SUCCESSFUL));
        memoryMetric = AggregationHelper.getAggregatedMemory(executions);
        assertTrue(memoryMetric.isEmpty());

        // Add an execution with memory data
        Double memoryInGB = 2.0;
        executions.add(new RunExecution().executionStatus(SUCCESSFUL).memoryRequirementsGB(memoryInGB));
        memoryMetric = AggregationHelper.getAggregatedMemory(executions);
        assertTrue(memoryMetric.isPresent());
        assertEquals(memoryInGB, memoryMetric.get().getMinimum());
        assertEquals(memoryInGB, memoryMetric.get().getMaximum());
        assertEquals(memoryInGB, memoryMetric.get().getAverage());
        assertEquals(1, memoryMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedValidationStatus() {
        List<ValidationExecution> executions = new ArrayList<>();
        Optional<ValidationStatusMetric> validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(executions);
        assertTrue(validationStatusMetric.isEmpty());

        // Add an execution with validation data
        final ValidationExecution.ValidatorToolEnum validatorTool = ValidationExecution.ValidatorToolEnum.MINIWDL;
        executions.add(new ValidationExecution().validatorTool(validatorTool).valid(true));
        validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(executions);
        assertTrue(validationStatusMetric.isPresent());
        assertTrue(validationStatusMetric.get().getValidatorToolToIsValid().get(validatorTool.toString()));

        // Add an execution that isn't valid for the same validator
        executions.add(new ValidationExecution().validatorTool(validatorTool).valid(false));
        validationStatusMetric = AggregationHelper.getAggregatedValidationStatus(executions);
        assertTrue(validationStatusMetric.isPresent());
        assertFalse(validationStatusMetric.get().getValidatorToolToIsValid().get(validatorTool.toString()));
    }
}
