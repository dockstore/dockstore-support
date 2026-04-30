package io.dockstore.categorizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Ontology {

    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, Node> idToNode = new HashMap<>();

    public Ontology() {
    }

    public void addNode(String id, String label, String definition, List<String> parentIds, String source, boolean recommendedForAnnotation) {
        Node node = new Node(id, label, definition, parentIds, source, recommendedForAnnotation, this);
        nodes.add(node);
        idToNode.put(id, node);
    }

    public Node getNodeById(String id) {
        return idToNode.get(id);
    }

    public List<Node> getChildren(String id) {
        return nodes.stream().filter(node -> node.parentIds().contains(id)).toList();
    }

    public List<Node> getParents(String id) {
        return getNodeById(id).parentIds().stream().map(this::getNodeById).toList();
    }

    public List<Node> getAncestors(String id) {
        List<Node> parents = getParents(id);
        Set<Node> ancestors = new HashSet<>(parents);
        for (Node parent : parents) {
            ancestors.addAll(getAncestors(parent.id()));
        }
        return new ArrayList<>(ancestors);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public static class Node {
        private final String id;
        private final String label;
        private final String definition;
        private final List<String> parentIds;
        private final String source;
        private final boolean recommendedForAnnotation;
        private final Ontology ontology;

        Node(String id, String label, String definition, List<String> parentIds, String source, boolean recommendedForAnnotation, Ontology ontology) {
            this.id = id;
            this.label = label;
            this.definition = definition;
            this.parentIds = parentIds;
            this.source = source;
            this.recommendedForAnnotation = recommendedForAnnotation;
            this.ontology = ontology;
        }

        public String id() { return id; }
        public String label() { return label; }
        public String definition() { return definition; }
        public List<String> parentIds() { return parentIds; }
        public String source() { return source; }
        public boolean recommendedForAnnotation() { return recommendedForAnnotation; }
        public Ontology ontology() { return ontology; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node other)) return false;
            return Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
