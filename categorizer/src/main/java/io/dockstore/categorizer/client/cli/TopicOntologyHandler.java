package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;

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
    protected String getSummarizeCommand(EntryData entryData) {
        String entryType = entryData.entryType();
        return lines(
            "", "",
            "Describe the %s's field of study, area of application, scientific context, and similar.",
            "Omit information about the operations performed by the %s.".formatted(entryType),
            "Be terse and use scientific terminology."
        );
    }

    @Override
    protected String getSummaryDescription(EntryData entryData) {
        return "Use the following description of the %s's topics:".formatted(entryData.entryType());
    }

    @Override
    protected String getClassifyCommand(EntryData entryData) {
        String entryType = entryData.entryType();
        return lines(
            "List the topics that relate to the %s.".formatted(entryType),
            "Be as specific as possible.",
            "List up to seven topics.",
            "Prefer topics that describe the field of study, area of application, scientific context, or similar.", entryType
        );
    }

    @Override
    protected String getVerifyQuestion(EntryData entryData, Ontology.Node node) {
        return "Does the following topic accurately describe the %s's field of study, area of application, scientific context, or similar?".formatted(entryData.entryType());
    }

}
