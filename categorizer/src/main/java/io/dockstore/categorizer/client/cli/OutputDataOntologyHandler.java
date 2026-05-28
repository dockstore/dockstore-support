package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;

public class OutputDataOntologyHandler extends ThreeStageOntologyHandler {

    OutputDataOntologyHandler() {
    }

    @Override
    public String getName() {
        return "Output data";
    }

    @Override
    protected String getRootId() {
        return "output-data";
    }

    @Override
    protected String getSingularPhrase() {
        return "output data";
    }

    @Override
    protected String getPluralPhrase(EntryData entryData) {
        return "%s's outputs".formatted(entryData.entryType());
    }

    @Override
    protected String getSummarizeCommand(EntryData entryData) {
        String entryType = entryData.entryType();
        return lines(
            "", "",
            "Describe the information content of the %s's outputs.".formatted(entryType),
            "Explain each output's meaning or purpose, rather than its concrete representation.",
            "Omit input information.",
            "Be terse."
        );
    }

    @Override
    protected String getSummaryDescription(EntryData entryData) {
        return "The %s supports the following outputs:".formatted(entryData.entryType());
    }

    @Override
    protected String getClassifyCommand(EntryData entryData) {
        return "List the data outputs that the %s produces.".formatted(entryData.entryType());
    }

    @Override
    protected String getVerifyQuestion(EntryData entryData, Ontology.Node node) {
        return "Does the %s produce the following output data?".formatted(entryData.entryType());
    }

}
