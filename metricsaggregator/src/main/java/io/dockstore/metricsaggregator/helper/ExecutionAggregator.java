package io.dockstore.metricsaggregator.helper;

import io.dockstore.common.metrics.Execution;
import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.Metric;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A class defining the methods needed to aggregate workflow executions into aggregated metrics.
 * @param <T> The type of execution, example: RunExecution or ValidationExecution, that contains the metric to aggregate
 * @param <M> The aggregated metric from the Metrics class, a class containing multiple types of aggregated metrics
 * @param <E> The execution metric to aggregate from the Execution
 */
public abstract class ExecutionAggregator<T extends Execution, M extends Metric, E> {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * Get the metric to aggregate from a single workflow execution.
     * @param execution
     * @return
     */
    public abstract E getMetricFromExecution(T execution);

    /**
     * Returns a boolean indicating if the execution metric is valid.
     * @param executionMetric
     * @return
     */
    public abstract boolean validateExecutionMetric(E executionMetric);

    /**
     * Aggregates workflow executions into an aggregated metric.
     * @param executionMetrics
     * @return
     */
    protected abstract Optional<M> calculateAggregatedMetricFromExecutionMetrics(List<E> executionMetrics);

    /**
     * Aggregates a list of aggregated metrics into one aggregated metric.
     * @param aggregatedMetrics
     * @return
     */
    protected abstract Optional<M> calculateAggregatedMetricFromAggregatedMetrics(List<M> aggregatedMetrics);

    /**
     * Aggregate metrics from all submissions in the ExecutionsRequestBody.
     * @param allSubmissions
     * @return
     */
    public abstract Optional<M> getAggregatedMetricFromAllSubmissions(ExecutionsRequestBody allSubmissions);

    /**
     * Returns the property name of the metric in the Execution class to validate.
     * This is used to determine if a Java Bean validation violation is for this particular execution metric.
     * @return
     */
    public abstract String getPropertyPathToValidate();

    /**
     * Aggregates workflow executions into an aggregated metric and calculates the number of skipped executions.
     * @param executions
     * @return
     */

    public final Optional<M> getAggregatedMetricFromExecutions(List<T> executions) {
        final List<T> executionsWithNonNullMetric = executions.stream().filter(execution -> getMetricFromExecution(execution) != null).toList();
        final List<E> validExecutionMetrics = executionsWithNonNullMetric.stream().filter(this::isValid).map(this::getMetricFromExecution).toList();
        if (!validExecutionMetrics.isEmpty()) {
            Optional<M> calculatedMetric = calculateAggregatedMetricFromExecutionMetrics(validExecutionMetrics);
            final int numberOfSkippedExecutions = executionsWithNonNullMetric.size() - validExecutionMetrics.size();
            calculatedMetric.ifPresent(metric -> metric.setNumberOfSkippedExecutions(numberOfSkippedExecutions));
            return calculatedMetric;
        }
        return Optional.empty();
    }

    /**
     * Aggregates a list of aggregated metrics into one aggregated metric and calculates the number of skipped executions.
     * @param aggregatedMetrics
     * @return
     */
    public final Optional<M> getAggregatedMetricsFromAggregatedMetrics(List<M> aggregatedMetrics) {
        if (!aggregatedMetrics.isEmpty()) {
            Optional<M> calculatedMetric = calculateAggregatedMetricFromAggregatedMetrics(aggregatedMetrics.stream().filter(Objects::nonNull).toList());
            // Sum number of skipped executions from the aggregated metrics
            final int numberOfSkippedExecutions =  aggregatedMetrics.stream().map(Metric::getNumberOfSkippedExecutions).reduce(0, Integer::sum);
            calculatedMetric.ifPresent(metric -> metric.setNumberOfSkippedExecutions(numberOfSkippedExecutions));
            return calculatedMetric;
        }
        return Optional.empty();
    }

    /**
     * Validate executions using Java Bean Validation
     * @param execution
     * @return
     */
    public boolean isValid(T execution) {
        Set<ConstraintViolation<T>> violations = validator.validate(execution);
        return violations.stream().noneMatch(violation -> violation.getPropertyPath().toString().startsWith(getPropertyPathToValidate()));
    }
}
