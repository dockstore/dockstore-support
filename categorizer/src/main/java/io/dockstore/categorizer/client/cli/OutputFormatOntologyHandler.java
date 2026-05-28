package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;

public class OutputFormatOntologyHandler extends ThreeStageOntologyHandler {

    OutputFormatOntologyHandler() {
    }

    @Override
    public String getName() {
        return "Output formats";
    }

    @Override
    protected String getRootId() {
        return "output-format";
    }

    @Override
    protected String getSingularPhrase() {
        return "output format";
    }

    @Override
    protected String getPluralPhrase(EntryData entryData) {
        return "%s's output formats".formatted(entryData.entryType());
    }

    @Override
    protected String getSummarizeCommand(EntryData entryData) {
        return lines(
            "", "",
            "List the output file formats.",
            "Detail the format variants and format versions.",
            "Omit input formats.",
            "Be terse."
        );
    }

    @Override
    protected String getSummaryDescription(EntryData entryData) {
        return "The %s produces the following outputs:".formatted(entryData.entryType());
    }

    @Override
    protected String getClassifyCommand(EntryData entryData) {
        return "List the output formats that the %s produces.".formatted(entryData.entryType());
    }

    @Override
    protected String getVerifyQuestion(EntryData entryData, Ontology.Node node) {
        return "Does the %s produce the following output format?".formatted(entryData.entryType());
    }

}
