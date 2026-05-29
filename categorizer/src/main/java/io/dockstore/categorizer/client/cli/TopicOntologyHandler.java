package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class TopicOntologyHandler extends ThreeStageOntologyHandler {

    TopicOntologyHandler() {
        super("topic");
    }

    @Override
    protected List<String> saySummarizeInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "Describe the %s's field of study, area of application, scientific context, and similar.".formatted(entryType),
            "Omit information about the operations that the %s performs.".formatted(entryType)
        );
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "List the topics that the %s relates to.".formatted(entryType),
            "Prefer topics that describe the field of study, area of application, scientific context, or similar.",
            "Prefer topics that are more specific.",
            "List up to seven topics."
        );
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        String entryType = entryData.entryType();
        return List.of(
            "Does the following topic accurately describe the %s's field of study, area of application, scientific context, or similar?".formatted(entryType)
        );
    }

    @Override
    protected List<String> saySummaryIntro(EntryData entryData) {
        return List.of(
            "The %s relates to the following topics:".formatted(entryData.entryType())
        );
    }

    @Override
    protected String sayPluralPhrase(EntryData entryData) {
        return "%s's topics".formatted(entryData.entryType());
    }

}
