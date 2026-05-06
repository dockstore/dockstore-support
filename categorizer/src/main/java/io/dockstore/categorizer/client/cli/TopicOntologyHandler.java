package io.dockstore.categorizer.client.cli;

import io.dockstore.categorizer.Ontology;
import java.util.List;

public class TopicOntologyHandler extends ThreeStageOntologyHandler {

    TopicOntologyHandler() {
        super("topic-");
    }

    @Override
    protected String createSummarizeInstruction(EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Summarize the following %s:".formatted(entryType),
            formatEntryData(entryData),
            "Describe the field of study, area of application, scientific context, and similar.",
            "Omit information about the operations performed by the %s.".formatted(entryType),
            "Be terse and use scientific terminology."
            /*
            "List the abstract data type of each user-specified input.",
            "Describe each abstract data type in a sentence or less.",
            "Explain the data's meaning or purpose, rather than the concrete representation.",
            */
        );
    }

    @Override
    protected String createClassifyInstruction(List<Ontology.Node> nodes, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Classify the %s into the following categories:".formatted(entryType),
            createTaggedOntologyCsv(nodes, "topic-"),
            "",
            "Use the following description of the %s's topics:".formatted(entryType),
            tag("topic-description", summary),
            "",
            "List the categories that describe the important topics.".formatted(entryType),
            "Be as specific as possible.",
            "List up to seven topics.",
            "Prefer topics that describe the field of study, area of application, scientific context, or similar.", entryType,
            "Output one topic ID per line and no other text."
        );
    }

    @Override
    protected String createValidateInstruction(Ontology.Node node, String summary, EntryData entryData) {
        String entryType = entryData.entryType();
        return joinLines(
            "Use the following %s description:".formatted(entryType),
            tag("topic-description", summary),
            "",
            "Does the following topic accurately describe the %s's field of study, area of application, scientific context, or similar?".formatted(entryType),
            tag("topic-name", node.label()),
            tag("topic-definition", node.definition()),
            "Answer \"yes\" or \"no\" with no other text."
        );
    }
}
