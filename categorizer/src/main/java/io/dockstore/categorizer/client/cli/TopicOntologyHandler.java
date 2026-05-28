package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class TopicOntologyHandler extends ThreeStageOntologyHandler {

    TopicOntologyHandler() {
    }

    @Override
    public String getName() {
        return "Topics";
    }

    @Override
    protected String getRootId() {
        return "topic";
    }

    @Override
    protected String getSingularPhrase() {
        return "topic";
    }

    @Override
    protected String getPluralPhrase(EntryData entryData) {
        return entryData.entryType();
    }

    @Override
    protected List<String> saySummarizeInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "", "",
            "Describe the %s's field of study, area of application, scientific context, and similar.",
            "Omit information about the operations performed by the %s.".formatted(entryType),
            "Be terse and use scientific terminology."
        );
    }

    @Override
    protected List<String> saySummaryIntro(EntryData entryData) {
        return List.of("Use the following description of the %s's topics:".formatted(entryData.entryType()));
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "List the topics that relate to the %s.".formatted(entryType),
            "Be as specific as possible.",
            "List up to seven topics.",
            "Prefer topics that describe the field of study, area of application, scientific context, or similar.", entryType
        );
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        return List.of("Does the following topic accurately describe the %s's field of study, area of application, scientific context, or similar?".formatted(entryData.entryType()));
    }

}
