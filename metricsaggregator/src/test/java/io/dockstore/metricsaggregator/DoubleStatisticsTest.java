/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.metricsaggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DoubleStatisticsTest {

    @Test
    void testStatistic() {
        List<Double> fibonacci = List.of(0.0, 1.0, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 34.0, 55.0, 89.0, 144.0);
        DoubleStatistics statistics = new DoubleStatistics(fibonacci);
        assertEquals(0, statistics.getMinimum());
        assertEquals(144, statistics.getMaximum());
        assertEquals(29, Math.round(statistics.getAverage()));
        assertEquals(13, statistics.getNumberOfDataPoints());
    }
}
