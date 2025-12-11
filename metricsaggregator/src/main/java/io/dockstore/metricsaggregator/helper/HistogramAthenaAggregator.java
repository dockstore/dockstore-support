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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aggregate a specified database field into a histogram with the specified edge values.  To do so, create a SelectField for each histogram "bin", consisting of a SQL `count()` statement that calculates a frequency for each bin (the number of values between the edge values of the bin).  These SelectFields are submitted with the Athena query, and the resulting frequencies and specified edge values are assembled into a Histogram object.
 */
// TODO Generalize this to any field
public class HistogramAthenaAggregator extends RunExecutionAthenaAggregator<HistogramMetric> {

    private static final Logger LOG = LoggerFactory.getLogger(HistogramAthenaAggregator.class);
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private final Field<Double> field;
    private final List<Double> edges;
    private final int id;

    /**
     * Create an aggregator that computes a histogram of a specified database Field, using the specified list of edge values,
     * where the frequency of bin i is the count of the field values that are between edges[i] inclusive and edges[i + 1] exclusive.
     * @param edges the edge values of the histogram, as specified above
     */
    public HistogramAthenaAggregator(MetricsAggregatorAthenaClient metricsAggregatorAthenaClient, String tableName, Field<Double> field, List<Double> edges) {
        super(metricsAggregatorAthenaClient, tableName);
        this.field = field;
        this.edges = edges;
        this.id = ID_COUNTER.getAndIncrement();
    }

    @Override
    public Set<SelectField<?>> getSelectFields() {
        return getBinIndexes().stream().map(this::getSelectField).collect(Collectors.toSet());
    }

    private SelectField<?> getSelectField(int binIndex) {
        Field<Double> start = inline(edges.get(binIndex));
        Field<Double> end = inline(edges.get(binIndex + 1));
        Condition withinBin = and(field.greaterOrEqual(start), field.lessThan(end));
        String aggregateColumnName = getAggregateColumnName(binIndex);
        return count(case_().when(withinBin, 1)).as(aggregateColumnName);
    }

    private List<Integer> getBinIndexes() {
        return IntStream.range(0, edges.size() - 1).boxed().toList();
    }

    private String getAggregateColumnName(int binIndex) {
        return "%s_freq_%d_%d".formatted(getMetricColumnName(), binIndex, id);
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

        //
        LOG.error("HISTOGRAM");
        LOG.error("EDGES {}", edges);
        LOG.error("FREQUENCIES {}", frequencies);
        // Construct, populate, and return the TimeSeriesMetric object.
        HistogramMetric histogram = new HistogramMetric();
        histogram.setEdges(edges);
        histogram.setFrequencies(frequencies);
        return Optional.of(histogram);
    }
}
