package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class MonthlyExecutionCountsAthenaAggregator extends ExecutionCountsAthenaAggregator {

    public MonthlyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Instant now) {
        super(metricsAggregatorAthenaClient, tableName, binCount, now);
    }

    protected TimeSeriesMetric.IntervalEnum getInterval() {
        return TimeSeriesMetric.IntervalEnum.MONTH;
    }

    protected ZonedDateTime pastBinStart(ZonedDateTime binStart, int delta) {
        return binStart.minusMonths(delta);
    }

    protected ZonedDateTime futureBinStart(ZonedDateTime binStart, int delta) {
        return binStart.plusMonths(delta);
    }

    protected ZonedDateTime overlappingBinStart(ZonedDateTime zonedDateTime) {
        return zonedDateTime.
            with(TemporalAdjusters.firstDayOfMonth()).
            truncatedTo(ChronoUnit.DAYS);
    }
}
