package io.dockstore.metricsaggregator.helper;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LogSequenceTest {

    @Test
    void print() {
        print(new LogSequence().getRunTimeLogSequence());
        print(new LogSequence().getFriendlyRunTimeLogSequence());
    }

    void print(List<Double> sequence) {
        System.out.println("sequence");
        System.out.println(format(sequence));
        System.out.println(sequence.size());
        factors(sequence);
    }

    String format(List<Double> sequence) {
        return sequence.stream().map(this::format).collect(Collectors.joining(" "));
    }

    String format(double value) {
        int hours = ((int)value) / 3600;
        value -= hours * 3600;
        int minutes = ((int)value) / 60;
        value -= minutes * 60;
        double seconds = value;
        return "%2d:%02d:%02f".formatted(hours, minutes, seconds);
    }

    void factors(List<Double> sequence) {
        for (int i = 0; i < sequence.size() - 1; i++) {
            double a = sequence.get(i);
            double b = sequence.get(i + 1);
            System.out.println(a + " " + b + " " + b / a);
        }
    }
}
