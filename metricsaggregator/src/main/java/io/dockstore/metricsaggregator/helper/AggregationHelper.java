package io.dockstore.metricsaggregator.helper;

import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionDateISO8601Format;
import static io.dockstore.common.metrics.FormatCheckHelper.checkExecutionTimeISO8601Format;
import static io.dockstore.common.metrics.FormatCheckHelper.isValidCurrencyCode;
import static java.util.stream.Collectors.groupingBy;

import io.dockstore.metricsaggregator.DoubleStatistics;
import io.dockstore.metricsaggregator.MoneyStatistics;
import io.dockstore.openapi.client.model.Cost;
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
import java.time.Duration;
import java.util.ArrayList;
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
import org.javamoney.moneta.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AggregationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationHelper.class);

    private AggregationHelper() {
    }

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
            getAggregatedCost(allSubmissions).ifPresent(aggregatedMetrics::setCost);
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
                .map(Metrics::getExecutionStatusCount)
                .filter(Objects::nonNull)
                .map(executionStatusMetric -> executionStatusMetric.getCount().entrySet())
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
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        getAggregatedExecutionTimeFromExecutions(allSubmissions.getRunExecutions()).ifPresent(executionTimeMetrics::add);

        if (!executionTimeMetrics.isEmpty()) {
            List<DoubleStatistics> statistics = executionTimeMetrics.stream()
                    .map(metric -> new DoubleStatistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage()))
                    .toList();

            DoubleStatistics newStatistic = DoubleStatistics.createFromStatistics(statistics);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(newStatistic.getMinimum())
                    .maximum(newStatistic.getMaximum())
                    .average(newStatistic.getAverage())
                    .numberOfDataPointsForAverage(newStatistic.getNumberOfDataPoints()));
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
            DoubleStatistics statistics = new DoubleStatistics(executionTimesInSeconds);
            return Optional.of(new ExecutionTimeMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
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
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        getAggregatedCpuFromExecutions(allSubmissions.getRunExecutions()).ifPresent(cpuMetrics::add);

        if (!cpuMetrics.isEmpty()) {
            List<DoubleStatistics> statistics = cpuMetrics.stream()
                    .map(metric -> new DoubleStatistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            DoubleStatistics newStatistic = DoubleStatistics.createFromStatistics(statistics);
            return Optional.of(new CpuMetric()
                    .minimum(newStatistic.getMinimum())
                    .maximum(newStatistic.getMaximum())
                    .average(newStatistic.getAverage())
                    .numberOfDataPointsForAverage(newStatistic.getNumberOfDataPoints()));
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
            DoubleStatistics statistics = new DoubleStatistics(cpuRequirements);
            return Optional.of(new CpuMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
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
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        getAggregatedMemoryFromExecutions(allSubmissions.getRunExecutions()).ifPresent(memoryMetrics::add);

        if (!memoryMetrics.isEmpty()) {
            List<DoubleStatistics> statistics = memoryMetrics.stream()
                    .map(metric -> new DoubleStatistics(metric.getMinimum(), metric.getMaximum(), metric.getAverage(), metric.getNumberOfDataPointsForAverage())).toList();
            DoubleStatistics newStatistic = DoubleStatistics.createFromStatistics(statistics);
            return Optional.of(new MemoryMetric()
                    .minimum(newStatistic.getMinimum())
                    .maximum(newStatistic.getMaximum())
                    .average(newStatistic.getAverage())
                    .numberOfDataPointsForAverage(newStatistic.getNumberOfDataPoints()));
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
            DoubleStatistics statistics = new DoubleStatistics(memoryRequirements);
            return Optional.of(new MemoryMetric()
                    .minimum(statistics.getMinimum())
                    .maximum(statistics.getMaximum())
                    .average(statistics.getAverage())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate Cost metrics from all submissions by calculating the minimum, maximum, and average.
     * @param allSubmissions
     * @return
     */
    public static Optional<CostMetric> getAggregatedCost(ExecutionsRequestBody allSubmissions) {
        // Get aggregated cost metrics that were submitted to Dockstore
        List<CostMetric> costMetrics = allSubmissions.getAggregatedExecutions().stream()
                .map(Metrics::getCost)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        getAggregatedCostFromExecutions(allSubmissions.getRunExecutions()).ifPresent(costMetrics::add);

        if (!costMetrics.isEmpty()) {
            List<MoneyStatistics> statistics = costMetrics.stream()
                    .map(metric -> new MoneyStatistics(Money.of(metric.getMinimum(), metric.getUnit()), Money.of(metric.getMaximum(), metric.getUnit()), Money.of(metric.getAverage(),
                            metric.getUnit()), metric.getNumberOfDataPointsForAverage()))
                    .toList();
            MoneyStatistics moneyStatistics = MoneyStatistics.createFromStatistics(statistics);
            return Optional.of(new CostMetric()
                    .minimum(moneyStatistics.getMinimum().getNumberStripped().doubleValue())
                    .maximum(moneyStatistics.getMaximum().getNumberStripped().doubleValue())
                    .average(moneyStatistics.getAverage().getNumberStripped().doubleValue())
                    .numberOfDataPointsForAverage(moneyStatistics.getNumberOfDataPoints()));
        }
        return Optional.empty();
    }

    /**
     * Aggregate Cost metrics from the list of run executions by calculating the minimum, maximum, and average.
     * @param executions
     * @return
     */
    public static Optional<CostMetric> getAggregatedCostFromExecutions(List<RunExecution> executions) {
        List<Cost> submittedCosts = executions.stream()
                .map(RunExecution::getCost)
                .filter(Objects::nonNull)
                .toList();

        boolean containsMalformedCurrencies = submittedCosts.stream().anyMatch(cost -> !isValidCurrencyCode(cost.getCurrency()));
        // This shouldn't happen until we allow users to submit any currency they want
        if (containsMalformedCurrencies) {
            return Optional.empty(); // Don't aggregate if there's malformed data
        }

        if (!submittedCosts.isEmpty()) {
            List<Money> costs = submittedCosts.stream()
                    .map(cost -> Money.of(cost.getValue(), cost.getCurrency()))
                    .toList();
            MoneyStatistics statistics = new MoneyStatistics(costs);
            return Optional.of(new CostMetric()
                    .minimum(statistics.getMinimum().getNumberStripped().doubleValue())
                    .maximum(statistics.getMaximum().getNumberStripped().doubleValue())
                    .average(statistics.getAverage().getNumberStripped().doubleValue())
                    .numberOfDataPointsForAverage(statistics.getNumberOfDataPoints()));
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
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        getAggregatedValidationStatusFromExecutions(allSubmissions.getValidationExecutions()).ifPresent(validationStatusMetrics::add);

        Map<String, ValidatorInfo> newValidatorToolToValidatorInfo = new HashMap<>();
        if (!validationStatusMetrics.isEmpty()) {
            // Go through all the ValidationStatusMetrics and group the ValidationVersionInfos by validator tool
            Map<String, List<ValidatorVersionInfo>> validatorToolToValidationVersionInfos = validationStatusMetrics.stream()
                    .map(ValidationStatusMetric::getValidatorTools)
                    .flatMap(validatorToolToValidatorInfoMap -> validatorToolToValidatorInfoMap.entrySet().stream())
                    .collect(groupingBy(Map.Entry::getKey, Collectors.flatMapping(entry -> {
                        ValidatorInfo validationInfoForValidatorTool = entry.getValue();
                        return validationInfoForValidatorTool.getValidatorVersions().stream();
                    }, Collectors.toList())));

            // For each validator tool, find the most recent ValidatorVersionInfo for each version
            validatorToolToValidationVersionInfos.forEach((validatorTool, validationVersionInfosByValidatorTool) -> {
                // Number of runs across all versions
                final int numberOfRuns = validationVersionInfosByValidatorTool.stream().map(ValidatorVersionInfo::getNumberOfRuns)
                        .mapToInt(Integer::intValue)
                        .sum();
                final List<DoubleStatistics> validationRunsStatistics = validationVersionInfosByValidatorTool.stream()
                        .map(validatorVersionInfo -> new DoubleStatistics(validatorVersionInfo.getPassingRate(), validatorVersionInfo.getNumberOfRuns()))
                        .toList();

                final double passingRate = DoubleStatistics.createFromStatistics(validationRunsStatistics).getAverage();
                final Optional<ValidatorVersionInfo> mostRecentValidationVersion = getLatestValidationVersionInfo(validationVersionInfosByValidatorTool);

                if (mostRecentValidationVersion.isPresent()) {
                    // Group ValidatorVersionInfo by version name
                    Map<String, List<ValidatorVersionInfo>> versionNameToValidationVersionInfos = validationVersionInfosByValidatorTool.stream()
                            .collect(Collectors.groupingBy(ValidatorVersionInfo::getName));

                    // Get a list of the most recent ValidatorVersionInfo for each version
                    List<ValidatorVersionInfo> mostRecentValidationVersionInfos = versionNameToValidationVersionInfos.values().stream().map(AggregationHelper::getLatestValidationVersionInfo).filter(Optional::isPresent).map(Optional::get).toList();

                    // Set validation info for the validator tool
                    ValidatorInfo validatorInfo = new ValidatorInfo()
                            .mostRecentVersionName(mostRecentValidationVersion.get().getName())
                            .validatorVersions(mostRecentValidationVersionInfos)
                            .numberOfRuns(numberOfRuns)
                            .passingRate(passingRate);

                    newValidatorToolToValidatorInfo.put(validatorTool, validatorInfo);
                }
            });
        }

        if (newValidatorToolToValidatorInfo.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ValidationStatusMetric().validatorTools(newValidatorToolToValidatorInfo));
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
        Map<String, ValidatorInfo> validatorToolToValidationInfo = new HashMap<>();
        validatorToolToValidations.forEach((validatorTool, validatorToolExecutions) -> {
            Optional<ValidationExecution> latestValidationExecution = getLatestValidationExecution(validatorToolExecutions);

            if (latestValidationExecution.isPresent()) {
                // Group the validation executions for the validator tool by version
                Map<String, List<ValidationExecution>> validatorVersionNameToValidationExecutions = validatorToolExecutions.stream()
                        .collect(groupingBy(ValidationExecution::getValidatorToolVersion));

                // Get the validation information for the most recent execution for each validator tool version
                Map<String, ValidatorVersionInfo> validatorVersionNameToVersionInfo = new HashMap<>();
                validatorVersionNameToValidationExecutions.forEach((validatorVersionName, validatorVersionExecutions) -> {
                    Optional<ValidationExecution> latestValidationExecutionForVersion = getLatestValidationExecution(validatorVersionExecutions);

                    latestValidationExecutionForVersion.ifPresent(validationExecution -> {
                        ValidatorVersionInfo validatorVersionInfo = new ValidatorVersionInfo()
                                .name(validatorVersionName)
                                .isValid(validationExecution.isIsValid())
                                .dateExecuted(validationExecution.getDateExecuted())
                                .numberOfRuns(validatorVersionExecutions.size())
                                .passingRate(getPassingRate(validatorVersionExecutions));

                        if (!validationExecution.isIsValid() && StringUtils.isNotBlank(validationExecution.getErrorMessage())) {
                            validatorVersionInfo.errorMessage(validationExecution.getErrorMessage());
                        }

                        validatorVersionNameToVersionInfo.put(validatorVersionName, validatorVersionInfo);
                    });
                });

                // Set validation info for the validator tool
                ValidatorInfo validatorInfo = new ValidatorInfo()
                        .mostRecentVersionName(validatorVersionNameToVersionInfo.get(latestValidationExecution.get().getValidatorToolVersion()).getName())
                        .validatorVersions(validatorVersionNameToVersionInfo.values().stream().toList())
                        .numberOfRuns(validatorToolExecutions.size())
                        .passingRate(getPassingRate(validatorToolExecutions));

                validatorToolToValidationInfo.put(validatorTool.toString(), validatorInfo);
            }
        });

        // This shouldn't happen because all validation executions should the required fields, but check anyway
        if (validatorToolToValidationInfo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ValidationStatusMetric().validatorTools(validatorToolToValidationInfo));
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

    static Optional<ValidatorVersionInfo> getLatestValidationVersionInfo(List<ValidatorVersionInfo> validationVersionInfos) {
        if (validationVersionInfos.isEmpty()) {
            return Optional.empty();
        }

        boolean containsInvalidDate = validationVersionInfos.stream().anyMatch(execution -> checkExecutionDateISO8601Format(execution.getDateExecuted()).isEmpty());
        if (containsInvalidDate) {
            return Optional.empty();
        }

        return validationVersionInfos.stream()
                .max(Comparator.comparing(validatorVersionInfo -> checkExecutionDateISO8601Format(validatorVersionInfo.getDateExecuted()).get(), Date::compareTo));
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
