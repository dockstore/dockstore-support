package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class DailyExecutionCountsAthenaAggregator extends ExecutionCountsAthenaAggregator {

    public DailyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Instant now) {
        super(metricsAggregatorAthenaClient, tableName, binCount, now);
    }

    protected TimeSeriesMetric.IntervalEnum getInterval() {
        return TimeSeriesMetric.IntervalEnum.DAY;
    }

    protected ZonedDateTime pastBinStart(ZonedDateTime binStart, int delta) {
        return binStart.minusDays(delta);
    }

    protected ZonedDateTime futureBinStart(ZonedDateTime binStart, int delta) {
        return binStart.plusDays(delta);
    }

    protected ZonedDateTime overlappingBinStart(ZonedDateTime zonedDateTime) {
        return zonedDateTime.truncatedTo(ChronoUnit.DAYS);
    }
}
