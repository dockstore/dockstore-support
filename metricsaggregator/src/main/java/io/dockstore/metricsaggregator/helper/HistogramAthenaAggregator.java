package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.case_;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.inline;

import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.HistogramMetric;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SelectField;

/**
 * Aggregate a specified database field into a histogram with the specified edge values.  To do so, create a SelectField for each histogram "bin", consisting of a SQL `count()` statement that calculates each bin's frequency (the number of values between the edge values of the bin).  These SelectFields are submitted with the Athena query, and the resulting frequencies and specified edge values are assembled into a Histogram object.
 */
public class HistogramAthenaAggregator extends RunExecutionAthenaAggregator<HistogramMetric> {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private final Field<Double> field;
    private final List<Double> edges;
    private final int id;

    /**
     * Create an aggregator that computes a histogram of a specified database Field, using the specified list of edge values,
     * where the frequency of bin[i] is the count of the field values that are between edges[i] inclusive and edges[i + 1] exclusive.
     * @param edges the edge values of the histogram, as specified above
     */
    public HistogramAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, Field<Double> field, List<Double> edges) {
        super(metricsAggregatorAthenaClient, tableName);
        this.field = field;
        this.edges = edges;
        this.id = ID_COUNTER.getAndIncrement(); // Retrieve an ID that's unique to this run.
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return getBinIndexes().stream().map(this::getSelectField).collect(Collectors.toSet());
    }

    private SelectField<?> getSelectField(int binIndex) {
        Field<Double> loValue = inline(edges.get(binIndex));
        Field<Double> hiValue = inline(edges.get(binIndex + 1));
        Condition withinBin = and(field.greaterOrEqual(loValue), field.lessThan(hiValue));
        String aggregateColumnName = getAggregateColumnName(binIndex);
        return count(case_().when(withinBin, 1)).as(aggregateColumnName);
    }

    private List<Integer> getBinIndexes() {
        return IntStream.range(0, edges.size() - 1).boxed().toList();
    }

    private String getAggregateColumnName(int binIndex) {
        // Generate a column name that includes the bin index and unique ID.
        // The unique ID must be included to avoid duplicate column names, should we aggregate multiple histograms.
        return "histogram_%d_%d".formatted(id, binIndex);
    }

    @Override
    String getMetricColumnName() {
        return field.getName();
    }

    @Override
    Optional<HistogramMetric> createMetricFromQueryResultRow(QueryResultRow queryResultRow) {
        // Create the list of "frequencies" consisting of the frequency for each histogram "bin".
        List<Double> frequencies = new ArrayList<>();
        for (int binIndex: getBinIndexes()) {
            Optional<String> count = queryResultRow.getColumnValue(getAggregateColumnName(binIndex));
            if (count.isPresent()) {
                frequencies.add(count.map(Double::valueOf).get());
            } else {
                return Optional.empty();
            }
        }

        // Construct, populate, and return the TimeSeriesMetric object.
        HistogramMetric histogram = new HistogramMetric();
        histogram.setEdges(edges);
        histogram.setFrequencies(frequencies);
        return Optional.of(histogram);
    }
}
