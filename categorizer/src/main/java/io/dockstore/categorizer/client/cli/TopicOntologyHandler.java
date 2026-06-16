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
            "Describe the %s's research field, application area, and scientific context.".formatted(entryType),
            "Omit information about the operations that the %s performs.".formatted(entryType)
        );
    }

    @Override
    protected List<String> sayClassifyInstructions(EntryData entryData) {
        String entryType = entryData.entryType();
        return List.of(
            "List the topics that the %s relates to.".formatted(entryType),
            "Include topics that describe the %s's research field, application area, or scientific context.".formatted(entryType),
            "Include topics that describe the %s's overall role.".formatted(entryType),
            "Prefer topics that differentiate this %s from other %ss.".formatted(entryType, entryType),
            "Prefer topics that are more specific."
        );
    }

    @Override
    protected List<String> sayVerifyInstructions(EntryData entryData, Ontology.Node node) {
        String entryType = entryData.entryType();
        return List.of(isGenericNode(node)
            ? "Does the following topic accurately describe the %s's field of study, area of application, scientific context, or similar?".formatted(entryType)
            : "Does the following topic accurately describe the %s's field of study, area of application, scientific context, or similar?  Answer 'no' if the topic does not strongly relate to the %s's primary purpose.".formatted(entryType, entryType)
        );
    }

    private boolean isGenericNode(Ontology.Node node) {
        String id = node.id();
        return node.ontology().getAncestors(id).stream().anyMatch(ancestor -> "topic-data-management".equals(ancestor.id()));
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
