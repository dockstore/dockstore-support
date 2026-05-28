package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;

public class InputDataOntologyHandler extends ThreeStageOntologyHandler {

    InputDataOntologyHandler() {
    }

    @Override
    public String getName() {
        return "Input data";
    }

    @Override
    protected String getRootId() {
        return "input-data";
    }

    @Override
    protected String getSingularPhrase() {
        return "input data";
    }

    @Override
    protected String getPluralPhrase(EntryData entryData) {
        return "%s's inputs".formatted(entryData.entryType());
    }

    @Override
    protected String getSummarizeCommand(EntryData entryData) {
        String entryType = entryData.entryType();
        return lines(
            "", "",
            "Describe the information content of the %s's inputs.".formatted(entryType),
            "Explain each inputs's meaning or purpose, rather than its concrete representation.",
            "Omit output information.",
            "Be terse."
        );
    }

    @Override
    protected String getSummaryDescription(EntryData entryData) {
        return "The %s supports the following inputs:".formatted(entryData.entryType());
    }

    @Override
    protected String getClassifyCommand(EntryData entryData) {
        return "List the inputs that the %s accepts.".formatted(entryData.entryType());
    }

    @Override
    protected String getVerifyQuestion(EntryData entryData, Ontology.Node node) {
        return "Does the %s support the following input data?".formatted(entryData.entryType());
    }

}
