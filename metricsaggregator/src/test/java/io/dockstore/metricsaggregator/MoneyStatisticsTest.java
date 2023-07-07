package io.dockstore.metricsaggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;
import javax.money.Monetary;
import javax.money.MonetaryRounding;
import javax.money.RoundingQueryBuilder;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;

class MoneyStatisticsTest {
    @Test
    void testStatistic() {
        List<Money> fibonacci = Stream.of(0.0, 1.0, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 34.0, 55.0, 89.0, 144.0)
                .map(value -> Money.of(value, "USD"))
                .toList();
        MoneyStatistics statistics = new MoneyStatistics(fibonacci);
        assertEquals(Money.of(0, "USD"), statistics.getMinimum());
        assertEquals(Money.of(144, "USD"), statistics.getMaximum());
        MonetaryRounding rounding = Monetary.getRounding(
                RoundingQueryBuilder.of().setScale(2).set(RoundingMode.HALF_UP).build());
        assertEquals(Money.of(28.92, "USD"), statistics.getAverage().with(rounding));
        assertEquals(13, statistics.getNumberOfDataPoints());
    }
}
