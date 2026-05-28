package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;

public class InputFormatOntologyHandler extends ThreeStageOntologyHandler {

    InputFormatOntologyHandler() {
    }

    @Override
    public String getName() {
        return "Input formats";
    }

    @Override
    protected String getRootId() {
        return "input-format";
    }

    @Override
    protected String getSingularPhrase() {
        return "input format";
    }

    @Override
    protected String getPluralPhrase(EntryData entryData) {
        return "%s's input formats".formatted(entryData.entryType());
    }

    @Override
    protected String getSummarizeCommand(EntryData entryData) {
        return lines(
            "", "",
            "List the input file formats.",
            "Detail the format variants and format versions.",
            "Omit output formats.",
            "Be terse."
        );
    }

    @Override
    protected String getSummaryDescription(EntryData entryData) {
        return "The %s accepts the following inputs:".formatted(entryData.entryType());
    }

    @Override
    protected String getClassifyCommand(EntryData entryData) {
        return "List the input formats that the %s accepts.".formatted(entryData.entryType());
    }

    @Override
    protected String getVerifyQuestion(EntryData entryData, Ontology.Node node) {
        return "Does the %s accept the following input format?".formatted(entryData.entryType());
    }

}
