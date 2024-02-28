package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.ExecutionStatus.FAILED_RUNTIME_INVALID;
import static io.dockstore.common.metrics.ExecutionStatus.FAILED_SEMANTIC_INVALID;
import static io.dockstore.common.metrics.ExecutionStatus.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.metrics.Cost;
import io.dockstore.common.metrics.ExecutionStatus;
import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.common.metrics.RunExecution;
import io.dockstore.common.metrics.TaskExecutions;
import io.dockstore.openapi.client.model.CostMetric;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.MemoryMetric;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionStatusAggregatorTest {
    private static final ExecutionStatusAggregator EXECUTION_STATUS_AGGREGATOR = new ExecutionStatusAggregator();

    /**
     * Tests that the aggregator calculates the correct workflow RunExecution from a list of task RunExecutions.
     */
    @Test
    void testGetWorkflowExecutionFromTaskExecutions() {
        // Empty TaskExecutions should return Optional.empty()
        Optional<RunExecution> workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(new TaskExecutions());
        assertTrue(workflowExecution.isEmpty());

        // The workflow execution generated from a single task should have the same status as the one task
        TaskExecutions taskExecutions = createTaskExecutions(new RunExecution(SUCCESSFUL));
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(SUCCESSFUL, workflowExecution.get().getExecutionStatus());

        // The workflow execution generated from tasks that were all successful should have a successful status
        taskExecutions = createTaskExecutions(
                new RunExecution(SUCCESSFUL),
                new RunExecution(SUCCESSFUL),
                new RunExecution(SUCCESSFUL)
        );
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertEquals(SUCCESSFUL, workflowExecution.get().getExecutionStatus());

        // The workflow execution generated from tasks that where there are failures should have the most frequent failed status
        taskExecutions = createTaskExecutions(
                new RunExecution(SUCCESSFUL),
                new RunExecution(FAILED_SEMANTIC_INVALID),
                new RunExecution(FAILED_RUNTIME_INVALID),
                new RunExecution(FAILED_RUNTIME_INVALID)
        );
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        // Should be FAILED_RUNTIME_INVALID because it's the most frequent failed status
        assertEquals(FAILED_RUNTIME_INVALID, workflowExecution.get().getExecutionStatus());
        // When there are equal counts of all failed statuses, the workflow execution status should be one of them
        taskExecutions = createTaskExecutions(
                new RunExecution(SUCCESSFUL),
                new RunExecution(FAILED_SEMANTIC_INVALID),
                new RunExecution(FAILED_RUNTIME_INVALID)
        );
        workflowExecution = EXECUTION_STATUS_AGGREGATOR.getWorkflowExecutionFromTaskExecutions(taskExecutions);
        assertTrue(workflowExecution.isPresent());
        assertTrue(workflowExecution.get().getExecutionStatus() == FAILED_RUNTIME_INVALID
                || workflowExecution.get().getExecutionStatus() == FAILED_SEMANTIC_INVALID);
    }

    /**
     * Tests that the aggregator calculates the correct aggregated metric from a list of workflow RunExecutions
     */
    @Test
    void testGetAggregatedMetricFromWorkflowExecutions() {
        // Empty list should return Optional.empty()
        Optional<ExecutionStatusMetric> executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromExecutions(List.of());
        assertTrue(executionStatusMetric.isEmpty());

        // Test the metric calculated from a single workflow execution.
        List<RunExecution> workflowExecutions = List.of(new RunExecution(SUCCESSFUL));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()).getExecutionStatusCount());
        assertFalse(executionStatusMetric.get().getCount().containsKey(FAILED_SEMANTIC_INVALID.toString()));
        assertFalse(executionStatusMetric.get().getCount().containsKey(FAILED_RUNTIME_INVALID.toString()));

        // Test the metric calculated from multiple workflow executions.
        workflowExecutions = List.of(
                new RunExecution(SUCCESSFUL),
                new RunExecution(FAILED_SEMANTIC_INVALID),
                new RunExecution(FAILED_RUNTIME_INVALID)
        );
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromExecutions(workflowExecutions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()).getExecutionStatusCount());
        assertEquals(1, executionStatusMetric.get().getCount().get(FAILED_SEMANTIC_INVALID.toString()).getExecutionStatusCount());
        assertEquals(1, executionStatusMetric.get().getCount().get(FAILED_RUNTIME_INVALID.toString()).getExecutionStatusCount());
    }

    @Test
    void testGetAggregatedExecutionStatus() {
        ExecutionStatusAggregator executionStatusAggregator = new ExecutionStatusAggregator();
        ExecutionsRequestBody allSubmissions = new ExecutionsRequestBody();
        Optional<ExecutionStatusMetric> executionStatusMetric = executionStatusAggregator.getAggregatedMetricFromAllSubmissions(allSubmissions);
        assertTrue(executionStatusMetric.isEmpty());

        RunExecution submittedRunExecution = new RunExecution(SUCCESSFUL);
        allSubmissions = createExecutionsRequestBody(List.of(submittedRunExecution));
        executionStatusMetric = executionStatusAggregator.getAggregatedMetricFromAllSubmissions(allSubmissions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()).getExecutionStatusCount());

        // Aggregate submissions containing workflow run executions and task executions
        submittedRunExecution = new RunExecution(SUCCESSFUL);
        TaskExecutions taskExecutionsForOneWorkflowRun = createTaskExecutions(submittedRunExecution);
        allSubmissions = createExecutionsRequestBody(List.of(submittedRunExecution), List.of(taskExecutionsForOneWorkflowRun));
        executionStatusMetric = executionStatusAggregator.getAggregatedMetricFromAllSubmissions(allSubmissions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(2, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()).getExecutionStatusCount());
        // Submit one successful workflow execution and a list of task executions where the single task failed
        taskExecutionsForOneWorkflowRun = createTaskExecutions(new RunExecution(FAILED_RUNTIME_INVALID));
        allSubmissions = createExecutionsRequestBody(List.of(submittedRunExecution), List.of(taskExecutionsForOneWorkflowRun));
        executionStatusMetric = executionStatusAggregator.getAggregatedMetricFromAllSubmissions(allSubmissions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()).getExecutionStatusCount());
        assertEquals(1, executionStatusMetric.get().getCount().get(FAILED_RUNTIME_INVALID.toString()).getExecutionStatusCount());
        // Submit one successful workflow execution and a list of task executions where there are two tasks that failed due to different reasons
        taskExecutionsForOneWorkflowRun = createTaskExecutions(new RunExecution(FAILED_RUNTIME_INVALID), new RunExecution(FAILED_SEMANTIC_INVALID));
        allSubmissions = createExecutionsRequestBody(List.of(submittedRunExecution), List.of(taskExecutionsForOneWorkflowRun));
        executionStatusMetric = executionStatusAggregator.getAggregatedMetricFromAllSubmissions(allSubmissions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()).getExecutionStatusCount());
        // There should be 1 of either FAILED_RUNTIME_INVALID or FAILED_SEMANTIC_INVALID.
        assertTrue(executionStatusMetric.get().getCount().containsKey(FAILED_RUNTIME_INVALID.toString()) || executionStatusMetric.get().getCount().containsKey(FAILED_SEMANTIC_INVALID.toString()));
        // Submit one successful workflow execution and a list of task executions where there are 3 tasks that failed due to different reasons
        taskExecutionsForOneWorkflowRun.setTaskExecutions(
                List.of(new RunExecution(FAILED_RUNTIME_INVALID),
                        new RunExecution(FAILED_RUNTIME_INVALID),
                        new RunExecution(FAILED_SEMANTIC_INVALID)));
        allSubmissions = createExecutionsRequestBody(List.of(submittedRunExecution), List.of(taskExecutionsForOneWorkflowRun));
        executionStatusMetric = executionStatusAggregator.getAggregatedMetricFromAllSubmissions(allSubmissions);
        assertTrue(executionStatusMetric.isPresent());
        assertEquals(1, executionStatusMetric.get().getCount().get(SUCCESSFUL.toString()).getExecutionStatusCount());
        // There should be one count of FAILED_RUNTIME_INVALID because it's the most frequent failed status in the list of task executions
        assertEquals(1, executionStatusMetric.get().getCount().get(FAILED_RUNTIME_INVALID.toString()).getExecutionStatusCount());
    }

    @Test
    void testGetAggregatedExecutionTime() {
        List<RunExecution> badExecutions = new ArrayList<>();

        // Add an execution that doesn't have execution time data
        badExecutions.add(new RunExecution(SUCCESSFUL));
        Optional<ExecutionStatusMetric> executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(badExecutions));
        assertTrue(executionStatusMetric.isPresent());
        ExecutionTimeMetric executionTimeMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getExecutionTime();
        assertNull(executionTimeMetric);

        // Add an execution with malformed execution time data
        badExecutions.add(createRunExecutionWithExecutionTime(SUCCESSFUL, "1 second"));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(badExecutions));
        executionTimeMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getExecutionTime();
        assertNull(executionTimeMetric);

        // Add an execution with execution time
        final int timeInSeconds = 10;
        List<RunExecution> executions = List.of(createRunExecutionWithExecutionTime(SUCCESSFUL, String.format("PT%dS", timeInSeconds)));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions));
        executionTimeMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getExecutionTime();
        assertNotNull(executionTimeMetric);
        assertEquals(timeInSeconds, executionTimeMetric.getMinimum());
        assertEquals(timeInSeconds, executionTimeMetric.getMaximum());
        assertEquals(timeInSeconds, executionTimeMetric.getAverage());
        assertEquals(1, executionTimeMetric.getNumberOfDataPointsForAverage());

        // Aggregate submissions containing workflow run executions and task executions
        // Submit a single workflow execution that took 10s and a single task that took 10s
        executions = List.of(createRunExecutionWithExecutionTime(SUCCESSFUL, String.format("PT%dS", timeInSeconds)));
        TaskExecutions taskExecutions = new TaskExecutions();
        taskExecutions.setTaskExecutions(executions);
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions, List.of(taskExecutions)));
        executionTimeMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getExecutionTime();
        assertNotNull(executionTimeMetric);
        assertEquals(timeInSeconds, executionTimeMetric.getMinimum());
        assertEquals(timeInSeconds, executionTimeMetric.getMaximum());
        assertEquals(timeInSeconds, executionTimeMetric.getAverage());
        assertEquals(2, executionTimeMetric.getNumberOfDataPointsForAverage()); // There should be 2 data points: 1 for the workflow execution and 1 for the list of tasks
        // Submit a single workflow execution that took 10s and two tasks that took 10 seconds. This time, dateExecuted is provided
        RunExecution task1 = createRunExecutionWithExecutionTime(SUCCESSFUL, String.format("PT%dS", timeInSeconds));
        RunExecution task2 = createRunExecutionWithExecutionTime(SUCCESSFUL, String.format("PT%dS", timeInSeconds));
        // The time difference between these two tasks is 10 seconds. When there is more than one task, the duration will be calculated from the dates executed, plus the duration of the last task, which is 10s
        task1.setDateExecuted("2023-11-09T21:54:10.571285905Z");
        task2.setDateExecuted("2023-11-09T21:54:20.571285905Z");
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions, List.of(createTaskExecutions(task1, task2))));
        executionTimeMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getExecutionTime();
        assertNotNull(executionTimeMetric);
        assertEquals(10, executionTimeMetric.getMinimum(), "The minimum is from the workflow execution");
        assertEquals(20, executionTimeMetric.getMaximum(), "The maximum is from the workflow execution calculated from the two tasks");
        assertEquals(15, executionTimeMetric.getAverage());
        assertEquals(2, executionTimeMetric.getNumberOfDataPointsForAverage()); // There should be 2 data points: 1 for the workflow execution and 1 for the list of tasks
    }
    
    @Test
    void testGetAggregatedCpu() {
        List<RunExecution> executions = new ArrayList<>();

        // Add an execution that doesn't have cpu data
        executions.add(new RunExecution(SUCCESSFUL));
        Optional<ExecutionStatusMetric> executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions));
        CpuMetric cpuMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getCpu();
        assertNull(cpuMetric);

        // Add an execution with cpu data
        final int cpu = 1;
        executions.add(createRunExecutionWithCpu(SUCCESSFUL, cpu));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions));
        cpuMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getCpu();
        assertNotNull(cpuMetric);
        assertEquals(cpu, cpuMetric.getMinimum());
        assertEquals(cpu, cpuMetric.getMaximum());
        assertEquals(cpu, cpuMetric.getAverage());
        assertEquals(1, cpuMetric.getNumberOfDataPointsForAverage());

        // Aggregate submissions containing workflow run executions and task executions
        executions = List.of(createRunExecutionWithCpu(SUCCESSFUL, cpu));
        // Two task executions with different CPU requirements. The workflow execution calculated from these tasks should take the highest cpuRequirement from the tasks
        TaskExecutions taskExecutions = createTaskExecutions(
                createRunExecutionWithCpu(SUCCESSFUL, 1),
                createRunExecutionWithCpu(SUCCESSFUL, 4));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions, List.of(taskExecutions)));
        cpuMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getCpu();
        assertNotNull(cpuMetric);
        assertEquals(1.0, cpuMetric.getMinimum());
        assertEquals(4.0, cpuMetric.getMaximum());
        assertEquals(2.5, cpuMetric.getAverage());
        assertEquals(2, cpuMetric.getNumberOfDataPointsForAverage()); // Two data points: 1 from the workflow execution and 1 for the list of tasks
    }
    
    @Test
    void testGetAggregatedMemory() {
        List<RunExecution> executions = new ArrayList<>();

        // Add an execution that doesn't have memory data
        executions.add(new RunExecution(SUCCESSFUL));
        Optional<ExecutionStatusMetric> executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions));
        MemoryMetric memoryMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getMemory();
        assertNull(memoryMetric);

        // Add an execution with memory data
        Double memoryInGB = 2.0;
        executions.add(createRunExecutionWithMemory(SUCCESSFUL, memoryInGB));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions));
        memoryMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getMemory();
        assertNotNull(memoryMetric);
        assertEquals(memoryInGB, memoryMetric.getMinimum());
        assertEquals(memoryInGB, memoryMetric.getMaximum());
        assertEquals(memoryInGB, memoryMetric.getAverage());
        assertEquals(1, memoryMetric.getNumberOfDataPointsForAverage());

        // Aggregate submissions containing workflow run executions and task executions
        executions = List.of(createRunExecutionWithMemory(SUCCESSFUL, 2.0));
        // Two task executions with different memory requirements. The workflow execution calculated from these tasks should take the highest memoryRequirementsGB from the tasks
        TaskExecutions taskExecutions = createTaskExecutions(
                createRunExecutionWithMemory(SUCCESSFUL, 2.0),
                createRunExecutionWithMemory(SUCCESSFUL, 4.0));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions, List.of(taskExecutions)));
        memoryMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getMemory();
        assertNotNull(memoryMetric);
        assertEquals(2.0, memoryMetric.getMinimum());
        assertEquals(4.0, memoryMetric.getMaximum());
        assertEquals(3.0, memoryMetric.getAverage());
        assertEquals(2, memoryMetric.getNumberOfDataPointsForAverage()); // Two data points: 1 from the workflow execution and 1 for the list of tasks
    }
    
    @Test
    void testGetAggregatedCost() {
        List<RunExecution> executions = new ArrayList<>();

        // Add an execution that doesn't have cost data
        executions.add(new RunExecution(SUCCESSFUL));
        Optional<ExecutionStatusMetric> executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions));
        assertTrue(executionStatusMetric.isPresent());
        CostMetric costMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getCost();
        assertNull(costMetric);

        // Add an execution with cost data
        Double costInUSD = 2.00;
        executions.add(createRunExecutionWithCost(SUCCESSFUL, costInUSD));
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions));
        costMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getCost();
        assertNotNull(costMetric);
        assertEquals(costInUSD, costMetric.getMinimum());
        assertEquals(costInUSD, costMetric.getMaximum());
        assertEquals(costInUSD, costMetric.getAverage());
        assertEquals(1, costMetric.getNumberOfDataPointsForAverage());

        // Aggregate submissions containing workflow run executions and task executions
        executions = List.of(createRunExecutionWithCost(SUCCESSFUL, 2.00));
        // Two task executions with different memory requirements. The workflow execution calculated from these tasks should have the sum of the cost of all tasks
        TaskExecutions taskExecutions = createTaskExecutions(
                createRunExecutionWithCost(SUCCESSFUL, 2.00),
                createRunExecutionWithCost(SUCCESSFUL, 4.00)
        );
        executionStatusMetric = EXECUTION_STATUS_AGGREGATOR.getAggregatedMetricFromAllSubmissions(createExecutionsRequestBody(executions, List.of(taskExecutions)));
        costMetric = executionStatusMetric.get().getCount().get(SUCCESSFUL.name()).getCost();
        assertNotNull(costMetric);
        assertEquals(2.0, costMetric.getMinimum());
        assertEquals(6.0, costMetric.getMaximum()); // The max is the cost of the two tasks summed together
        assertEquals(4.0, costMetric.getAverage());
        assertEquals(2, costMetric.getNumberOfDataPointsForAverage()); // Two data points: 1 from the workflow execution and 1 for the list of tasks
    }
    
    private RunExecution createRunExecutionWithExecutionTime(ExecutionStatus executionStatus, String executionTime) {
        RunExecution runExecution = new RunExecution(executionStatus);
        runExecution.setExecutionTime(executionTime);
        return runExecution;
    }

    private RunExecution createRunExecutionWithMemory(ExecutionStatus executionStatus, Double memoryRequirementsGB) {
        RunExecution runExecution = new RunExecution(executionStatus);
        runExecution.setMemoryRequirementsGB(memoryRequirementsGB);
        return runExecution;
    }

    private RunExecution createRunExecutionWithCpu(ExecutionStatus executionStatus, Integer cpuRequirements) {
        RunExecution runExecution = new RunExecution(executionStatus);
        runExecution.setCpuRequirements(cpuRequirements);
        return runExecution;
    }

    private RunExecution createRunExecutionWithCost(ExecutionStatus executionStatus, Double costValue) {
        RunExecution runExecution = new RunExecution(executionStatus);
        runExecution.setCost(new Cost(costValue));
        return runExecution;
    }

    private ExecutionsRequestBody createExecutionsRequestBody(List<RunExecution> runExecutions) {
        ExecutionsRequestBody executionsRequestBody = new ExecutionsRequestBody();
        executionsRequestBody.setRunExecutions(runExecutions);
        return executionsRequestBody;
    }

    private ExecutionsRequestBody createExecutionsRequestBody(List<RunExecution> runExecutions, List<TaskExecutions> taskExecutions) {
        ExecutionsRequestBody executionsRequestBody = createExecutionsRequestBody(runExecutions);
        executionsRequestBody.setTaskExecutions(taskExecutions);
        return executionsRequestBody;
    }

    private TaskExecutions createTaskExecutions(RunExecution... taskExecutions) {
        TaskExecutions taskExecutionsSet = new TaskExecutions();
        taskExecutionsSet.setTaskExecutions(List.of(taskExecutions));
        return taskExecutionsSet;
    }
}
