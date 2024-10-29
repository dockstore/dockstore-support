package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.topicgenerator.client.cli.TopicGeneratorClient.isSuspiciousTopic;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TopicGeneratorTest {

    @Test
    void testSuspiciousTopic() {
        assertTrue(isSuspiciousTopic("The workflow starts by inputting Illumina-sequenced ARTIC data and involves a voyeur."));
        assertFalse(isSuspiciousTopic("The workflow starts by inputting Illumina-sequenced ARTIC data."));
    }
}
