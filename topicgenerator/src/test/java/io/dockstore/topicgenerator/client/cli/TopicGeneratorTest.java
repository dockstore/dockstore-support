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

        // Should not be suspicious because it includes a word that is common in biology
        assertFalse(isSuspiciousTopic("Performs a workflow that includes subsetting VCFs, merging and splitting VCFs, and calculating relatedness and sex using VCFTools and PLINK."));
    }
}
