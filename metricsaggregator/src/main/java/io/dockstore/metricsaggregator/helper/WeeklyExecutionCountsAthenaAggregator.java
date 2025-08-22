package io.dockstore.metricsaggregator.helper;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class WeeklyExecutionCountsAthenaAggregator extends ExecutionCountsAthenaAggregator {

    private static final int DAYS_PER_WEEK = 7;

    public WeeklyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Instant now) {
        super(metricsAggregatorAthenaClient, tableName, binCount, now);
    }

    protected TimeSeriesMetric.IntervalEnum getInterval() {
        return TimeSeriesMetric.IntervalEnum.WEEK;
    }

    protected ZonedDateTime pastBinStart(ZonedDateTime binStart, int delta) {
        return binStart.minusDays(delta * DAYS_PER_WEEK);
    }

    protected ZonedDateTime futureBinStart(ZonedDateTime binStart, int delta) {
        return binStart.plusDays(delta * DAYS_PER_WEEK);
    }

    protected ZonedDateTime overlappingBinStart(ZonedDateTime zonedDateTime) {
        return zonedDateTime.
            with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).
            truncatedTo(ChronoUnit.DAYS);
    }
}
