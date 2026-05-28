package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class OutputFormatOntologyHandler extends ThreeStageOntologyHandler {

    OutputFormatOntologyHandler() {
        super("output-format");
    }

    @Override
    protected String sayPluralPhrase(EntryData entryData) {
        return "%s's output formats".formatted(entryData.entryType());
    }

    @Override
    protected List<String> saySummarizeInstructions(EntryData entryData) {
        return List.of(
            "", "",
            "List the output file formats.",
            "Detail the format variants and format versions.",
            "Omit input formats.",
            "Be terse."
        );
    }

    @Override
    protected List<String> saySummaryIntro(EntryData entryData) {
        return List.of("The %s produces the following outputs:".formatted(entryData.entryType()));
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        return List.of("List the output formats that the %s produces.".formatted(entryData.entryType()));
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        return List.of("Does the %s produce the following output format?".formatted(entryData.entryType()));
    }

}
