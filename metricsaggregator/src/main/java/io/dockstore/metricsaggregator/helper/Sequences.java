package io.dockstore.metricsaggregator.helper;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"checkstyle:magicnumber", "checkstyle:whitespacearound"})
public class LogSequence {
    private List<Double> getNineStepsTo10() {
        return List.of(1.3, 1.65, 2.15, 2.8, 3.6, 4.7, 6., 7.8, 10.);
    }

    private List<Double> getSevenStepsTo6() {
        return List.of(1.3, 1.65, 2.15, 2.8, 3.6, 4.7, 6.);
    }

    /*
    public List<Double> get13StepsTo10() {
        return List.of(1.2, 1.45, 1.75, 2.1, 2.5, 3., 3.55, 4.2, 5., 6., 7.15, 8.4, 10.);

    }

    public List<Double> get10StepsTo6() {
        return List.of(1.2, 1.45, 1.75, 2.1, 2.5, 3., 3.55, 4.2, 5., 6.);
    }
    */
    private List<Double> get13StepsTo10() {
        return List.of(1.2, 1 + 9/20., 1.75, 2 + 1/12., 2.5, 3., 3 + 7/12., 4.25, 5., 6., 7 + 1/12., 8 + 5/12., 10.);

    }

    private List<Double> get10StepsTo6() {
        return List.of(1.2, 1 + 9/20., 1.75, 2 + 1/12., 2.5, 3., 3 + 7/12., 4.25, 5., 6.);
    }

    private List<Double> multiply(double multiplier, List<Double> sequence) {
        return sequence.stream().map(v -> v * multiplier).toList();
    }

    private List<Double> round(List<Double> sequence) {
        return sequence.stream().map(v -> v.isInfinite() ? v : (double)Math.round(v)).toList();
    }

    public List<Double> getRunTimeLogSequence() {
        List<Double> values = new ArrayList<>();
        values.add(0.);
        for (int i = 0; i < 40; i++) {
            double x0 = 0.5 * i;
            double x1 = x0 + 0.25;
            values.add(Math.pow(2, x0));
            values.add(Math.pow(2, x1));
        }
        return values;
    }

    /*
    public List<Double> getFriendlyRunTimeLogSequence() {
        List<Double> values = new ArrayList<>();
        values.addAll(List.of(0., 1., 3., 5., 8., 10., 13., 17., 22., 28., 36., 47., 60.)); // <= 60 sec
        values.addAll(multiply(60, getNineStepsTo10())); // <= 10m
        values.addAll(multiply(600, getSevenStepsTo6())); // <= 1h
        values.addAll(multiply(3600, getNineStepsTo10())); // <= 10h
        values.addAll(multiply(3600, List.of(13., 17., 24.))); // <= 1d
        values.addAll(multiply(24 * 3600, List.of(2., 4., 10., Double.MAX_VALUE)));
        return values;
    }
    */

    public List<Double> getFriendlyRunTimeLogSequence() {
        List<Double> values = new ArrayList<>();
        values.addAll(List.of(0., 1., 3., 5., 8., 10., 12., 14., 17., 21., 25., 30., 36., 42., 50., 60.));
        values.addAll(multiply(60, get13StepsTo10()));
        values.addAll(multiply(600, get10StepsTo6()));
        values.addAll(multiply(3600, get13StepsTo10())); // <= 10h
        values.addAll(multiply(3600, List.of(12., 14.5, 18., 24.))); // <= 1d
        values.addAll(multiply(24 * 3600, List.of(2., 4., 10., 30., 100.)));
        values.add(Double.POSITIVE_INFINITY);
        return round(values);
    }

    public List<Double> getLinearSequence(double start, double end, double step) {
        List<Double> values = new ArrayList<>();
        for (double value = start; value <= end; value += step) {
            values.add(value);
        }
        values.add(Double.POSITIVE_INFINITY);
        return values;    
    }
}
