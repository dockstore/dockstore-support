package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.case_;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.function;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.TimeSeriesMetric;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.Condition;
import org.jooq.Context;
import org.jooq.Field;
import org.jooq.SelectField;
import org.jooq.impl.CustomField;
import org.jooq.impl.SQLDataType;

/**
 * Aggregate a time series of execution counts.  This aggregator creates a SelectField for each time series "bin" (consisting of a SQL `count()` statement on a corresponding date range).  These SelectFields are submitted with the Athena query, and the resulting counts are assembled into a TimeSeriesMetric object.
 */
public class DailyExecutionCountsAthenaAggregator extends RunExecutionAthenaAggregator<TimeSeriesMetric> {

    private static final int ZONE_HOUR_OFFSET = -4;  // Always aggregate with an Eastern DST time offset, so that all bin boundaries align with Eastern DST midnight and don't shift depending upon where the aggregator is run and/or whether Daylight Savings Time is currently in effect (or not).
    private static final ZoneId ZONE_ID = ZoneOffset.ofHours(ZONE_HOUR_OFFSET);
    private static final DateTimeFormatter ATHENA_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final int binCount;
    private final Instant now;

    /**
     * Create an aggregator that computes a time series with the specified number of bins (representing a consecutive series of days), with the youngest (last) bin overlapping the specified date.
     * @param binCount the number of bins (days) to aggregate
     * @param now the instant which is included in the youngest bin
     */
    public DailyExecutionCountsAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, int binCount, Instant now) {
        super(metricsAggregatorAthenaClient, tableName);
        this.binCount = binCount;
        this.now = now;
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return getBinAges().stream().map(this::getSelectField).collect(Collectors.toSet());
    }

    private SelectField<?> getSelectField(int binAge) {
        Field<Timestamp> start = utcTimestamp(getBinStart(binAge));
        Field<Timestamp> end = utcTimestamp(getBinEnd(binAge));
        Field<Timestamp> whenExecuted = cast(function("from_iso8601_timestamp", Instant.class, DATE_EXECUTED_FIELD), Timestamp.class);
        Condition withinBin = and(whenExecuted.greaterOrEqual(start), whenExecuted.lessThan(end));
        String aggregateColumnName = getAggregateColumnName(binAge);
        return count(case_().when(withinBin, 1)).as(aggregateColumnName);
    }

    private TimeSeriesMetric.IntervalEnum getInterval() {
        return TimeSeriesMetric.IntervalEnum.DAY;
    }

    private ZonedDateTime next(ZonedDateTime binStart, int binDelta) {
        return binStart.plusDays(binDelta);
    }

    private ZonedDateTime previous(ZonedDateTime binStart, int binDelta) {
        return binStart.minusDays(binDelta);
    }

    private ZonedDateTime getBinStart(ZonedDateTime zonedDateTime) {
        return zonedDateTime.truncatedTo(ChronoUnit.DAYS);
    }

    private ZonedDateTime getBinStart(int binAge) {
        ZonedDateTime zonedNow = ZonedDateTime.ofInstant(now, ZONE_ID);
        ZonedDateTime binZeroStart = getBinStart(zonedNow);
        return previous(binZeroStart, binAge);
    }

    private ZonedDateTime getBinEnd(int binAge) {
        ZonedDateTime binStart = getBinStart(binAge);
        return next(binStart, 1);
    }

    private ZonedDateTime getBinMidpoint(int binAge) {
        ZonedDateTime start = getBinStart(binAge);
        ZonedDateTime end = getBinEnd(binAge);
        return start.plus(Duration.between(start, end).dividedBy(2));
    }

    private List<Integer> getBinAges() {
        return IntStream.range(0, binCount).boxed().toList();
    }

    /**
     * Convert the specified date/time to a UTC timestamp in the prescribed Athena-supported format.
     * https://docs.aws.amazon.com/athena/latest/ug/data-types-timestamps.html#data-types-timestamps-writing-to-s3-objects
     * We don't use Jooq's timestamp() method, because it applies some weird local time zone logic, messing things up.
     */
    private Field<Timestamp> utcTimestamp(ZonedDateTime zonedDateTime) {
        return new CustomField("utc_timestamp", SQLDataType.TIMESTAMP) {
            @Override
            public void accept(Context context) {
                ZonedDateTime utcDateTime = zonedDateTime.withZoneSameInstant(ZoneOffset.UTC);
                context.sql("timestamp '%s'".formatted(utcDateTime.format(ATHENA_TIMESTAMP_FORMAT)));
            }
        };
    }

    private Date toDate(ZonedDateTime zonedDateTime) {
        return Date.from(zonedDateTime.toInstant());
    }

    private String getAggregateColumnName(int binAge) {
        return getInterval() + "_" + binAge + "_" + getMetricColumnName();
    }

    @Override
    String getMetricColumnName() {
        return DATE_EXECUTED_FIELD.getName();
    }

    @Override
    Optional<TimeSeriesMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        // Create the list of "values", consisting of the execution count for each time series "bin", ordered oldest to newest.
        List<Double> values = new ArrayList<>();
        for (int binAge: getBinAges()) {
            Optional<String> count = queryResultRow.getColumnValue(getAggregateColumnName(binAge));
            if (count.isPresent()) {
                values.add(count.map(Double::valueOf).get());
            } else {
                return Optional.empty();
            }
        }
        Collections.reverse(values);

        // Construct, populate, and return the TimeSeriesMetric object.
        TimeSeriesMetric metric = new TimeSeriesMetric();
        metric.setValues(values);
        metric.setInterval(getInterval());
        metric.setBegins(toDate(getBinMidpoint(binCount - 1)));
        return Optional.of(metric);
    }
}
