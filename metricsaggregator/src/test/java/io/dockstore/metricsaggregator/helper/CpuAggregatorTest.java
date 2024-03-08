package io.dockstore.metricsaggregator.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.openapi.client.model.CpuMetric;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CpuAggregatorTest {
    private static final CpuAggregator CPU_AGGREGATOR = new CpuAggregator();

    /**
     * Tests that the aggregator calculates the correct workflow RunExecution from a list of task RunExecutions.
     */
    @Test
    void testGetWorkflowExecutionFromTaskExecutions() {
        // Empty TaskExecutions should return Optional.empty()
        Optional<RunExecution> workflowExecution = CPU_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(new TaskExecutions());
        assertTrue(workflowExecution.isEmpty());

        // The workflow execution generated from a single task should have the same cpuRequirements as the one task
        TaskExecutions taskExecutions = new TaskExecutions();
        taskExecutions.setTaskExecutions(List.of(createRunExecution(1)));
        workflowExecution = CPU_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(1, workflowExecution.get().getCpuRequirements());

        // The workflow execution generated from multiple tasks should have the cpuRequirements from the highest cpuRequirements from the list of tasks
        taskExecutions.setTaskExecutions(List.of(
                createRunExecution(1),
                createRunExecution(2),
                createRunExecution(3)
        ));
        workflowExecution = CPU_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(3, workflowExecution.get().getCpuRequirements());
    }

    /**
     * Tests that the aggregator calculates the correct aggregated metric from a list of workflow RunExecutions
     */
    @Test
    void testGetAggregatedMetricFromWorkflowExecutions() {
        // Empty list should return Optional.empty()
        Optional<CpuMetric> cpuMetric = CPU_AGGREGATOR.getAggregatedMetricFromExecutions(List.of());
        assertTrue(cpuMetric.isEmpty());

        // Test the metric calculated from a single workflow execution. The min, max, and average should be the same value as the single execution
        List<RunExecution> workflowExecutions = List.of(createRunExecution(1));
        cpuMetric = CPU_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(cpuMetric.isPresent());
        assertEquals(1.0, cpuMetric.get().getMinimum());
        assertEquals(1.0, cpuMetric.get().getMaximum());
        assertEquals(1.0, cpuMetric.get().getAverage());
        assertEquals(1, cpuMetric.get().getNumberOfDataPointsForAverage());

        // Test the metric calculated from multiple workflow executions.
        workflowExecutions = List.of(
                createRunExecution(2),
                createRunExecution(4),
                createRunExecution(6)
        );
        cpuMetric = CPU_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(cpuMetric.isPresent());
        assertEquals(2.0, cpuMetric.get().getMinimum());
        assertEquals(6.0, cpuMetric.get().getMaximum());
        assertEquals(4.0, cpuMetric.get().getAverage());
        assertEquals(3, cpuMetric.get().getNumberOfDataPointsForAverage());
    }
    
    private RunExecution createRunExecution(Integer cpuRequirements) {
        RunExecution runExecution = new RunExecution();
        runExecution.setCpuRequirements(cpuRequirements);
        return runExecution;
    }
}
