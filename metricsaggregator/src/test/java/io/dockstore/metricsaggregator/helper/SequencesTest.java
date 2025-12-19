package io.dockstore.metricsaggregator.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class SequencesTest {

    @Test
    void testFriendlyLogRunTimeSequence() {

        List<Double> sequence = Sequences.getFriendlyLogRunTimeSequence();
        int size = sequence.size();

        // Range should be from 0 to infinity.
        assertEquals(0, sequence.get(0));
        assertEquals(Double.POSITIVE_INFINITY, sequence.get(size - 1));

        // Each value (except the last) should be an integer.
        for (int i = 0; i < size - 1; i++) {
            double v = sequence.get(i);
            assertEquals(Math.round(v), v);
        }

        // Every successive value should be larger.
        for (int i = 0; i < size - 1; i++) {
            double a = sequence.get(i);
            double b = sequence.get(i + 1);
            assertTrue(a < b, () -> "%f %f".formatted(a, b));
        }

        // In the range of 1m (60s) to 10h (36000s), each successive value should increase by a factor of between 1.17 and 1.21.
        for (int i = 0; i < size - 1; i++) {
            double a = sequence.get(i);
            double b = sequence.get(i + 1);
            if (a >= 60 && a < 36000) {
                double factor = b / a;
                assertTrue(factor > 1.17, () -> Double.toString(factor));
                assertTrue(factor < 1.21, () -> Double.toString(factor));
            }
        }
    }

    @Test
    void print() {
        print(Sequences.getFriendlyLogRunTimeSequence());
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
