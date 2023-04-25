package io.dockstore.metricsaggregator.helper;

import static io.dockstore.webservice.core.metrics.RunExecution.checkExecutionTimeISO8601Format;
import static io.dockstore.webservice.core.metrics.ValidationExecution.checkExecutionDateISO8601Format;
import static java.util.stream.Collectors.groupingBy;

import io.dockstore.metricsaggregator.Statistics;
import io.dockstore.openapi.client.model.CpuMetric;
import io.dockstore.openapi.client.model.ExecutionStatusMetric;
import io.dockstore.openapi.client.model.ExecutionTimeMetric;
import io.dockstore.openapi.client.model.ExecutionsRequestBody;
import io.dockstore.openapi.client.model.MemoryMetric;
import io.dockstore.openapi.client.model.Metrics;
import io.dockstore.openapi.client.model.RunExecution;
import io.dockstore.openapi.client.model.ValidationExecution;
import io.dockstore.openapi.client.model.ValidationInfo;
import io.dockstore.openapi.client.model.ValidationStatusMetric;
import io.dockstore.openapi.client.model.ValidationVersionInfo;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    private AggregationHelper() {}

    /**
     * Aggregate metrics from all submissions.
     *
     * @param allSubmissions
     * @return Metrics object containing aggregated metrics
     */
    public static Optional<Metrics> getAggregatedMetrics(ExecutionsRequestBody allSubmissions) {
        Metrics aggregatedMetrics = new Metrics();
        // Set run metrics
        Optional<ExecutionStatusMetric> aggregatedExecutionStatus = getAggregatedExecutionStatus(allSubmissions);
        boolean containsRunMetrics = aggregatedExecutionStatus.isPresent();
        if (aggregatedExecutionStatus.isPresent()) {
            aggregatedMetrics.setExecutionStatusCount(aggregatedExecutionStatus.get());
            getAggregatedExecutionTime(allSubmissions).ifPresent(aggregatedMetrics::setExecutionTime);
            getAggregatedCpu(allSubmissions).ifPresent(aggregatedMetrics::setCpu);
            getAggregatedMemory(allSubmissions).ifPresent(aggregatedMetrics::setMemory);
        }

        // Set validation metrics
        Optional<ValidationStatusMetric> aggregatedValidationStatus = getAggregatedValidationStatus(allSubmissions);
        boolean containsValidationMetrics = aggregatedValidationStatus.isPresent();
        aggregatedValidationStatus.ifPresent(aggregatedMetrics::setValidationStatus);

        // Only return aggregated metrics if it contains either run metrics or validation metrics
        if (containsRunMetrics || containsValidationMetrics) {
            return Optional.of(aggregatedMetrics);
        }

        return Optional.empty();
    }

    /**
     * Aggregate Execution Status metrics from all submissions by summing up the count of each Execution Status encountered in the run executions and aggregated metrics.
     * @param allSubmissions
     * @return
     */
    public static Optional<ExecutionStatusMetric> getAggregatedExecutionStatus(ExecutionsRequestBody allSubmissions) {
        // Calculate the status count from the run executions submitted
        Map<String, Integer> executionsStatusCount = allSubmissions.getRunExecutions().stream()
                .map(execution -> execution.getExecutionStatus().toString())
                .collect(groupingBy(Function.identity(), Collectors.reducing(0, e -> 1, Integer::sum)));

        // Calculate the status count from the aggregated metrics submitted
        Map<String, Integer> metricsStatusCount = allSubmissions.getAggregatedExecutions().stream()
                .map(m -> m.getExecutionStatusCount().getCount().entrySet())
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

        // Get the combined status count
        Map<String, Integer> statusCount = Stream.of(metricsStatusCount, executionsStatusCount)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));

        if (statusCount.isEmpty()) {
            return Optional.empty();
        }
        // Don't need to set the other fields because the setter for count will calculate and set the other fields
        return Optional.of(new ExecutionStatusMetric().count(statusCount));
    }

    /**
     * Aggregate Execution Time metrics from all submissions by calculating the minimum, maximum, and average.
     * @param allSubmissions
     * @return
     */
    public static Optional<ExecutionTimeMetric> getAggregatedExecutionTime(ExecutionsRequestBody allSubmissions) {
        // Get aggregated Execution Time metrics that were submitted to Dockstore
        List<ExecutionTimeMetric> executionTimeMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(Metrics::getExecutionTime)
                .collect(Collectors.toList());
        getAggregatedExecutionTimeFromExecutions(allSubmissions.getRunExecutions()).ifPresent(executionTimeMetrics::add);

        if (!executionTimeMetrics.isEmpty()) {
            List<Statistics> statistics = executionTimeMetrics.stream()
                    .map(metric -> new Statistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            Statistics newStatistic = Statistics.createFromStatistics(statistics);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(newStatistic.min())
                    .maximum(newStatistic.max())
                    .average(newStatistic.average())
                    .numberOfDataPointsForAverage(newStatistic.numberOfDataPoints()));
        }

        return Optional.empty();
    }

    /**
     * Calculate the aggregated Execution Time metric from individual run executions by calculating the minimum, maximum, and average
     * @param runExecutions
     * @return
     */
    public static Optional<ExecutionTimeMetric> getAggregatedExecutionTimeFromExecutions(List<RunExecution> runExecutions) {
        List<String> executionTimes = runExecutions.stream()
                .map(RunExecution::getExecutionTime)
                .filter(Objects::nonNull)
                .toList();

        boolean containsMalformedExecutionTimes = executionTimes.stream().anyMatch(executionTime -> checkExecutionTimeISO8601Format(executionTime).isEmpty());
        // This really shouldn't happen because the webservice validates that the ExecutionTime is in the correct format
        if (containsMalformedExecutionTimes) {
            return Optional.empty(); // Don't aggregate if there's malformed data
        }

        List<Double> executionTimesInSeconds = executionTimes.stream()
                .map(executionTime -> {
                    // Convert executionTime in ISO 8601 duration format to seconds
                    Duration parsedISO8601ExecutionTime = checkExecutionTimeISO8601Format(executionTime).get();
                    return Long.valueOf(parsedISO8601ExecutionTime.toSeconds()).doubleValue();
                })
                .toList();

        if (!executionTimesInSeconds.isEmpty()) {
            Statistics statistics = new Statistics(executionTimesInSeconds);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate CPU metrics from all submissions by calculating the minimum, maximum, and average.
     * @param allSubmissions
     * @return
     */
    public static Optional<CpuMetric> getAggregatedCpu(ExecutionsRequestBody allSubmissions) {
        // Get aggregated Execution Time metrics that were submitted to Dockstore
        List<CpuMetric> cpuMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(Metrics::getCpu)
                .collect(Collectors.toList());
        getAggregatedCpuFromExecutions(allSubmissions.getRunExecutions()).ifPresent(cpuMetrics::add);

        if (!cpuMetrics.isEmpty()) {
            List<Statistics> statistics = cpuMetrics.stream()
                    .map(metric -> new Statistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            Statistics newStatistic = Statistics.createFromStatistics(statistics);
            return Optional.of(new CpuMetric()
                    .minimum(newStatistic.min())
                    .maximum(newStatistic.max())
                    .average(newStatistic.average())
                    .numberOfDataPointsForAverage(newStatistic.numberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate CPU metrics from the list of run executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<CpuMetric> getAggregatedCpuFromExecutions(List<RunExecution> executions) {
        List<Double> cpuRequirements = executions.stream()
                .map(RunExecution::getCpuRequirements)
                .filter(Objects::nonNull)
                .map(Integer::doubleValue)
                .toList();
        if (!cpuRequirements.isEmpty()) {
            Statistics statistics = new Statistics(cpuRequirements);
            return Optional.of(new CpuMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate CPU metrics from all submissions by calculating the minimum, maximum, and average.
     * @param allSubmissions
     * @return
     */
    public static Optional<MemoryMetric> getAggregatedMemory(ExecutionsRequestBody allSubmissions) {
        // Get aggregated Execution Time metrics that were submitted to Dockstore
        List<MemoryMetric> memoryMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(Metrics::getMemory)
                .collect(Collectors.toList());
        getAggregatedMemoryFromExecutions(allSubmissions.getRunExecutions()).ifPresent(memoryMetrics::add);

        if (!memoryMetrics.isEmpty()) {
            List<Statistics> statistics = memoryMetrics.stream()
                    .map(metric -> new Statistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            Statistics newStatistic = Statistics.createFromStatistics(statistics);
            return Optional.of(new MemoryMetric()
                    .minimum(newStatistic.min())
                    .maximum(newStatistic.max())
                    .average(newStatistic.average())
                    .numberOfDataPointsForAverage(newStatistic.numberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate Memory metrics from the list of run executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<MemoryMetric> getAggregatedMemoryFromExecutions(List<RunExecution> executions) {
        List<Double> memoryRequirements = executions.stream()
                .map(RunExecution::getMemoryRequirementsGB)
                .filter(Objects::nonNull)
                .toList();
        if (!memoryRequirements.isEmpty()) {
            Statistics statistics = new Statistics(memoryRequirements);
            return Optional.of(new MemoryMetric()
                    .minimum(statistics.min())
                    .maximum(statistics.max())
                    .average(statistics.average())
                    .numberOfDataPointsForAverage(statistics.numberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate Validation metrics from the list of validation executions by retrieving the validation information for the most recent execution of
     * each validator tool version.
     * @param allSubmissions
     * @return
     */
    public static Optional<ValidationStatusMetric> getAggregatedValidationStatus(ExecutionsRequestBody allSubmissions) {
        // Get aggregated ValidationStatus metrics that were submitted to Dockstore
        List<ValidationStatusMetric> validationStatusMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(Metrics::getValidationStatus)
                .collect(Collectors.toList());
        getAggregatedValidationStatusFromExecutions(allSubmissions.getValidationExecutions()).ifPresent(validationStatusMetrics::add);

        Map<String, ValidationInfo> newValidatorToolToValidationInfo = new HashMap<>();
        if (!validationStatusMetrics.isEmpty()) {
            // Go through all the ValidationStatusMetrics and group the ValidationVersionInfos by validator tool
            Map<String, List<ValidationVersionInfo>> validatorToolToValidationVersionInfos = validationStatusMetrics.stream()
                    .map(ValidationStatusMetric::getValidatorToolToValidationInfo)
                    .flatMap(validatorToolToValidationInfoMap -> validatorToolToValidationInfoMap.entrySet().stream())
                    .collect(groupingBy(Map.Entry::getKey, Collectors.flatMapping(entry -> {
                        ValidationInfo validationInfoForValidatorTool = entry.getValue();
                        return validationInfoForValidatorTool.getValidationVersions().stream();
                    }, Collectors.toList())));

            // For each validator tool, find the most recent ValidationVersionInfo for each version
            validatorToolToValidationVersionInfos.forEach((validatorTool, validationVersionInfosByValidatorTool) -> {
                // Number of runs across all versions
                final int numberOfRuns = validationVersionInfosByValidatorTool.stream().map(ValidationVersionInfo::getNumberOfRuns)
                        .mapToInt(Integer::intValue)
                        .sum();
                final double passingRate = Statistics.getWeightedAverage(validationVersionInfosByValidatorTool.stream()
                        .map(validationVersionInfo -> new Statistics(validationVersionInfo.getPassingRate(), validationVersionInfo.getNumberOfRuns()))
                        .toList());
                final Optional<ValidationVersionInfo> mostRecentValidationVersion = getLatestValidationVersionInfo(validationVersionInfosByValidatorTool);

                if (mostRecentValidationVersion.isPresent()) {
                    // Group ValidationVersionInfo by version name
                    Map<String, List<ValidationVersionInfo>> versionNameToValidationVersionInfos = validationVersionInfosByValidatorTool.stream()
                            .collect(Collectors.groupingBy(ValidationVersionInfo::getName));

                    // Get a list of the most recent ValidationVersionInfo for each version
                    List<ValidationVersionInfo> mostRecentValidationVersionInfos = versionNameToValidationVersionInfos.values().stream().map(AggregationHelper::getLatestValidationVersionInfo).filter(Optional::isPresent).map(Optional::get).toList();

                    // Set validation info for the validator tool
                    ValidationInfo validationInfo = new ValidationInfo()
                            .mostRecentVersionName(mostRecentValidationVersion.get().getName())
                            .validationVersions(mostRecentValidationVersionInfos)
                            .numberOfRuns(numberOfRuns)
                            .passingRate(passingRate);

                    newValidatorToolToValidationInfo.put(validatorTool, validationInfo);
                }
            });
        }

        if (newValidatorToolToValidationInfo.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ValidationStatusMetric().validatorToolToValidationInfo(newValidatorToolToValidationInfo));
    }

    /**
     * Aggregate Validation metrics from the list of validation executions by retrieving the validation information for the most recent execution of
     * each validator tool version.
     * @param executions
     * @return
     */
    public static Optional<ValidationStatusMetric> getAggregatedValidationStatusFromExecutions(List<ValidationExecution> executions) {
        if (executions.isEmpty()) {
            return Optional.empty();
        }

        // Group executions by validator tool
        Map<ValidationExecution.ValidatorToolEnum, List<ValidationExecution>> validatorToolToValidations = executions.stream()
                .collect(groupingBy(ValidationExecution::getValidatorTool));

        // For each validator tool, aggregate validation metrics for it
        Map<String, ValidationInfo> validatorToolToValidationInfo = new HashMap<>();
        validatorToolToValidations.forEach((validatorTool, validatorToolExecutions) -> {
            Optional<ValidationExecution> latestValidationExecution = getLatestValidationExecution(validatorToolExecutions);

            if (latestValidationExecution.isPresent()) {
                // Group the validation executions for the validator tool by version
                Map<String, List<ValidationExecution>> validatorVersionNameToValidationExecutions = validatorToolExecutions.stream()
                        .collect(groupingBy(ValidationExecution::getValidatorToolVersion));

                // Get the validation information for the most recent execution for each validator tool version
                Map<String, ValidationVersionInfo> validatorVersionNameToVersionInfo = new HashMap<>();
                validatorVersionNameToValidationExecutions.forEach((validatorVersionName, validatorVersionExecutions) -> {
                    Optional<ValidationExecution> latestValidationExecutionForVersion = getLatestValidationExecution(validatorVersionExecutions);

                    latestValidationExecutionForVersion.ifPresent(validationExecution -> {
                        ValidationVersionInfo validationVersionInfo = new ValidationVersionInfo()
                                .name(validatorVersionName)
                                .isValid(validationExecution.isIsValid())
                                .dateExecuted(validationExecution.getDateExecuted())
                                .numberOfRuns(validatorVersionExecutions.size())
                                .passingRate(getPassingRate(validatorVersionExecutions));

                        if (!validationExecution.isIsValid() && StringUtils.isNotBlank(validationExecution.getErrorMessage())) {
                            validationVersionInfo.errorMessage(validationExecution.getErrorMessage());
                        }

                        validatorVersionNameToVersionInfo.put(validatorVersionName, validationVersionInfo);
                    });
                });

                // Set validation info for the validator tool
                ValidationInfo validationInfo = new ValidationInfo()
                        .mostRecentVersionName(validatorVersionNameToVersionInfo.get(latestValidationExecution.get().getValidatorToolVersion()).getName())
                        .validationVersions(validatorVersionNameToVersionInfo.values().stream().toList())
                        .numberOfRuns(validatorToolExecutions.size())
                        .passingRate(getPassingRate(validatorToolExecutions));

                validatorToolToValidationInfo.put(validatorTool.toString(), validationInfo);
            }
        });

        // This shouldn't happen because all validation executions should the required fields, but check anyway
        if (validatorToolToValidationInfo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ValidationStatusMetric().validatorToolToValidationInfo(validatorToolToValidationInfo));
    }

    static Optional<ValidationExecution> getLatestValidationExecution(List<ValidationExecution> executions) {
        if (executions.isEmpty()) {
            return Optional.empty();
        }

        boolean containsInvalidDate = executions.stream().anyMatch(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).isEmpty());
        if (containsInvalidDate) {
            return Optional.empty();
        }

        return executions.stream()
                .max(Comparator.comparing(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).get(), Date::compareTo));
    }

    static Optional<ValidationVersionInfo> getLatestValidationVersionInfo(List<ValidationVersionInfo> validationVersionInfos) {
        if (validationVersionInfos.isEmpty()) {
            return Optional.empty();
        }

        boolean containsInvalidDate = validationVersionInfos.stream().anyMatch(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).isEmpty());
        if (containsInvalidDate) {
            return Optional.empty();
        }

        return validationVersionInfos.stream()
                .max(Comparator.comparing(validationVersionInfo -> checkExecutionDateISO8601Format(validationVersionInfo.getDateExecuted()).get(), Date::compareTo));
    }

    /**
     * Gets the percentage of executions that passed validation
     * @param executions
     * @return
     */
    @SuppressWarnings("checkstyle:magicnumber")
    static double getPassingRate(List<ValidationExecution> executions) {
        final double numberOfPassingExecutions = executions.stream()
                .filter(ValidationExecution::isIsValid)
                .count();

        return (numberOfPassingExecutions / executions.size()) * 100;
    }
}
