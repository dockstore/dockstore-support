package io.dockstore.metricsaggregator.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.Execution;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import org.junit.jupiter.api.Test;

import static io.dockstore.openapi.client.model.Execution.ExecutionStatusEnum.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregationHelperTest {

    @Test
    void testGetAggregatedExecutionStatus() {
        List<Execution> executions = new ArrayList<>();
        Optional<ExecutionStatusMetric> executionStatusMetric = AggregationHelper.getAggregatedExecutionStatus(executions);
        assertTrue(executionStatusMetric.isEmpty());

        executions.add(new Execution().executionStatus(SUCCESSFUL));
        executionStatusMetric = AggregationHelper.getAggregatedExecutionStatus(executions);
        assertTrue(executionStatusMetric.isPresent());
    }

    @Test
    void testGetAggregatedExecutionTime() {
        List<Execution> badExecutions = new ArrayList<>();
        Optional<ExecutionTimeMetric> executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(badExecutions);
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution that doesn't have execution time data
        badExecutions.add(new Execution().executionStatus(SUCCESSFUL));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(badExecutions);
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution with malformed execution time data
        badExecutions.add(new Execution().executionStatus(SUCCESSFUL).executionTime("1 second"));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(badExecutions);
        assertTrue(executionTimeMetric.isEmpty());

        // Add an execution with execution time
        final int timeInSeconds = 10;
        List<Execution> executions = List.of(new Execution().executionStatus(SUCCESSFUL).executionTime(String.format("PT%dS", timeInSeconds)));
        executionTimeMetric = AggregationHelper.getAggregatedExecutionTime(executions);
        assertTrue(executionTimeMetric.isPresent());
        assertEquals(timeInSeconds, executionTimeMetric.get().getMinimum());
        assertEquals(timeInSeconds, executionTimeMetric.get().getMaximum());
        assertEquals(timeInSeconds, executionTimeMetric.get().getAverage());
        assertEquals(1, executionTimeMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedCpu() {
        List<Execution> executions = new ArrayList<>();
        Optional<CpuMetric> cpuMetric = AggregationHelper.getAggregatedCpu(executions);
        assertTrue(cpuMetric.isEmpty());

        // Add an execution that doesn't have cpu data
        executions.add(new Execution().executionStatus(SUCCESSFUL));
        cpuMetric = AggregationHelper.getAggregatedCpu(executions);
        assertTrue(cpuMetric.isEmpty());

        // Add an execution with cpu data
        final int cpu = 1;
        executions.add(new Execution().executionStatus(SUCCESSFUL).cpuRequirements(cpu));
        cpuMetric = AggregationHelper.getAggregatedCpu(executions);
        assertTrue(cpuMetric.isPresent());
        assertEquals(cpu, cpuMetric.get().getMinimum());
        assertEquals(cpu, cpuMetric.get().getMaximum());
        assertEquals(cpu, cpuMetric.get().getAverage());
        assertEquals(1, cpuMetric.get().getNumberOfDataPointsForAverage());
    }

    @Test
    void testGetAggregatedMemory() {
        List<Execution> executions = new ArrayList<>();
        Optional<MemoryMetric> memoryMetric = AggregationHelper.getAggregatedMemory(executions);
        assertTrue(memoryMetric.isEmpty());

        // Add an execution that doesn't have memory data
        executions.add(new Execution().executionStatus(SUCCESSFUL));
        memoryMetric = AggregationHelper.getAggregatedMemory(executions);
        assertTrue(memoryMetric.isEmpty());

        // Add an execution with memory data
        Double memoryInGB = 2.0;
        executions.add(new Execution().executionStatus(SUCCESSFUL).memoryRequirementsGB(memoryInGB));
        memoryMetric = AggregationHelper.getAggregatedMemory(executions);
        assertTrue(memoryMetric.isPresent());
        assertEquals(memoryInGB, memoryMetric.get().getMinimum());
        assertEquals(memoryInGB, memoryMetric.get().getMaximum());
        assertEquals(memoryInGB, memoryMetric.get().getAverage());
        assertEquals(1, memoryMetric.get().getNumberOfDataPointsForAverage());
    }
}
