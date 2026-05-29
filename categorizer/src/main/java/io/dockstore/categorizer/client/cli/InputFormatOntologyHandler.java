package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class InputFormatOntologyHandler extends ThreeStageOntologyHandler {

    InputFormatOntologyHandler() {
        super("input-format");
    }

    @Override
    protected List<String> saySummarizeInstructions(EntryData entryData) {
        return List.of(
            "List the input file formats accepted by the %s.".formatted(entryData.entryType()),
            "Detail the format variants and format versions.",
            "Omit output formats."
        );
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        return List.of(
            "List the input formats that the %s accepts.".formatted(entryData.entryType())
        );
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        return List.of(
            "Does the %s accept the following input format?".formatted(entryData.entryType())
        );
    }

    @Override
    protected List<String> saySummaryIntro(EntryData entryData) {
        return List.of(
            "The %s accepts the following input formats:".formatted(entryData.entryType())
        );
    }

    @Override
    protected String sayPluralPhrase(EntryData entryData) {
        return "%s's input formats".formatted(entryData.entryType());
    }
}
