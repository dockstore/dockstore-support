package io.dockstore.metricsaggregator.helper;

import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.cube;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.unnest;

import io.dockstore.common.Partner;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.AthenaTablePartition;
import io.dockstore.metricsaggregator.MetricsAggregatorAthenaClient.QueryResultRow;
import io.dockstore.openapi.client.model.Metric;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.SelectField;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

public abstract class RunExecutionAthenaAggregator<M extends Metric> extends AthenaAggregator<M> {
    // Fields for the SELECT clause
    private final Set<SelectField<?>> selectFields = new HashSet<>();
    // Fields for the GROUP BY clause
    private final Set<Field<?>> groupFields = new HashSet<>();

    protected RunExecutionAthenaAggregator() {
        // All queries will be grouped by platform at a minimum
        selectFields.add(coalesce(PLATFORM_FIELD, inline(Partner.ALL.name())).as(PLATFORM_FIELD.getName())); // Coalesce null platform values to "ALL"
        groupFields.add(PLATFORM_FIELD);
    }

    /**
     * Create a metric from a single query result row.
     * @param queryResultRow
     * @return
     */
    public abstract Optional<M> createMetricFromQueryResultRow(QueryResultRow queryResultRow);

    /**
     * Get the column name containing the metric.
     * Athena columns are lowercase, but if a non-lowercase column is provided (ex: camelCase), Athena automatically lowercases it.
     * @return
     */
    public abstract String getMetricColumnName();

    public Set<SelectField<?>> getSelectFields() {
        return this.selectFields;
    }

    public void addSelectFields(Set<SelectField<?>> newSelectFields) {
        this.selectFields.addAll(newSelectFields);
    }

    public Set<Field<?>> getGroupFields() {
        return this.groupFields;
    }

    public void addGroupFields(Set<Field<?>> newGroupFields) {
        this.groupFields.addAll(newGroupFields);
    }

    /**
     * Create the runexecutions query string using the SELECT and GROUP BY fields.
     * @param tableName
     * @return
     */
    @Override
    public String createQuery(String tableName, AthenaTablePartition partition) {
        return DSL.using(SQLDialect.DEFAULT, new Settings().withRenderFormatted(true))
                // Sub-query that flattens the runexecutions array for the partition
                .with("unnestedrunexecutions").as(
                        select(PLATFORM_FIELD, field("unnested.executionid"), field("unnested.dateexecuted"),
                                field("unnested.executionstatus"), field("unnested.executiontimeseconds"), field("unnested.memoryrequirementsgb"),
                                field("unnested.cpurequirements"), field("unnested.cost"), field("unnested.region"), field("unnested.additionalproperties"))
                                .from(table(tableName), unnest(field("runexecutions", String[].class)).as("t", "unnested"))
                                .where(ENTITY_FIELD.eq(inline(partition.entity()))
                                        .and(REGISTRY_FIELD.eq(inline(partition.registry())))
                                        .and(ORG_FIELD.eq(inline(partition.org())))
                                        .and(NAME_FIELD.eq(inline(partition.name())))
                                        .and(VERSION_FIELD.eq(inline(partition.version()))))
                )
                // Main query that uses the results of the subquery
                .select(this.selectFields)
                .from(table("unnestedrunexecutions"))
                .groupBy(cube(this.groupFields.toArray(Field[]::new))) // CUBE generates sub-totals for all combinations of the GROUP BY columns.
                .getSQL();
    }

    /**
     * Given a list of query result rows, creates a metric for each row and maps it to a platform
     * @param queryResultRows
     * @return
     */
    @Override
    public Map<String, M> createMetricByPlatform(List<QueryResultRow> queryResultRows) {
        Map<String, M> metricByPlatform = new HashMap<>();
        queryResultRows.forEach(queryResultRow -> {
            Optional<String> platform = getPlatformFromQueryResultRow(queryResultRow);
            if (platform.isPresent()) {
                Optional<M> metric = createMetricFromQueryResultRow(queryResultRow);
                metric.ifPresent(m -> metricByPlatform.put(platform.get(), m));
            }
        });
        return metricByPlatform;
    }

    /**
     * Returns a set of statistical SELECT fields: min, avg, max, count
     *
     * @return
     */
    public Set<SelectField<?>> getStatisticSelectFields() {
        return Set.of(min(field(getMetricColumnName())).as(getMinColumnName()),
                avg(field(getMetricColumnName(), Double.class)).as(getAvgColumnName()),
                max(field(getMetricColumnName())).as(getMaxColumnName()),
                count(field(getMetricColumnName())).as(getCountColumnName()));
    }

    public String substitutePeriodsForUnderscores(String columnName) {
        return columnName.replace(".", "_");
    }

    public String getMinColumnName() {
        return "min_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    public String getAvgColumnName() {
        return "avg_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    public String getMaxColumnName() {
        return "max_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    public String getCountColumnName() {
        return "count_" + substitutePeriodsForUnderscores(getMetricColumnName());
    }

    public Optional<Double> getMinColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getMinColumnName()).map(Double::valueOf);
    }

    public Optional<Double> getAvgColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getAvgColumnName()).map(Double::valueOf);
    }

    public Optional<Double> getMaxColumnValue(QueryResultRow queryResultRow) {
        return queryResultRow.getColumnValue(getMaxColumnName()).map(Double::valueOf);
    }

    public Optional<Integer> getCountColumnValue(QueryResultRow queryResultRow) {
        Optional<Integer> countColumnValue = queryResultRow.getColumnValue(getCountColumnName()).map(Integer::valueOf);
        if (countColumnValue.isPresent() && countColumnValue.get() == 0) { // There were 0 non-null column values
            return Optional.empty();
        }
        return countColumnValue;
    }
}
