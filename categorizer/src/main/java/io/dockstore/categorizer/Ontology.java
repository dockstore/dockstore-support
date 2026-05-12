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

    public record Node(String id, String label, String definition, List<String> parentIds, String source, boolean recommendedForAnnotation, Ontology ontology) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Node other)) {
                return false;
            }
            return Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

    }
}
