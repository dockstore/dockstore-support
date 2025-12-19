package io.dockstore.metricsaggregator.helper;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("checkstyle:magicnumber")
public final class Sequences {

    private Sequences() {
    }

    private static List<Double> get13FriendlyLogStepsTo10() {
        return List.of(1.2, 1 + 9 / 20., 1.75, 2 + 1 / 12., 2.5, 3., 3 + 7 / 12., 4.25, 5., 6., 7 + 1 / 12., 8 + 5 / 12., 10.);
    }

    private static List<Double> get10FriendlyLogStepsTo6() {
        return List.of(1.2, 1 + 9 / 20., 1.75, 2 + 1 / 12., 2.5, 3., 3 + 7 / 12., 4.25, 5., 6.);
    }

    private static List<Double> multiply(double multiplier, List<Double> sequence) {
        return sequence.stream().map(v -> v * multiplier).toList();
    }

    private static List<Double> round(List<Double> sequence) {
        return sequence.stream().map(v -> v.isInfinite() ? v : (double)Math.round(v)).toList();
    }

    /**
     * Generate a sequence of increasing numbers wherein successive values increase by approximately the same
     * multiplicative factor - in other words, they are separated by approximately the same amount in log space.
     * This sequence will be used to convert the run times of Dockstore workflows, in seconds, into a histogram.
     * The values at either end of the sequence may include wider "log ranges" and other deviations to accomodate
     * facts such as "the log of 0 is undefined (or negative infinity, depending on who you talk to)".  The values
     * that represent 10s to 10h should be very close to their exact exponential counterparts, but may deviate by
     * small amounts to allow the values to map to as many "simple" time intervals as possible (ex: 50s, 1m30s, etc).
     * Within the range of 1m to 10h, the multiplier between adjacent values ranges from 1.1764 to 1.2083.
     */
    public static List<Double> getFriendlyLogRunTimeSequence() {
        List<Double> values = new ArrayList<>();
        values.addAll(List.of(0., 1., 3., 5., 8., 10., 12., 14., 17., 21., 25., 30., 36., 42., 50., 60.));
        values.addAll(multiply(60, get13FriendlyLogStepsTo10()));
        values.addAll(multiply(600, get10FriendlyLogStepsTo6()));
        values.addAll(multiply(3600, get13FriendlyLogStepsTo10())); // <= 10h
        values.addAll(multiply(3600, List.of(12., 14.5, 18., 24.))); // <= 1d
        values.addAll(multiply(24 * 3600, List.of(2., 4., 10., 30., 100.)));
        values.add(Double.POSITIVE_INFINITY);
        return round(values);
    }
}
