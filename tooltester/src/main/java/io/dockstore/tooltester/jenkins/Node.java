package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 10/02/17
 */
public class Node {
    //@Checkstyle:off
    private JenkinsLink _links;
    //@Checkstyle:on
    private String id;
    private String name;
    private String execNode;
    private JenkinsError error;
    private Long startTimeMillis;
    private Long durationMillis;
    private Long pauseDurationMillis;
    private StageFlowNode[] stageFlowNodes;

    public Node() {
    }

    public StageFlowNode[] getStageFlowNodes() {
        return stageFlowNodes;
    }

    public JenkinsError getError() {
        return error;
    }

    public JenkinsLink getLinks() {
        return _links;
    }
}
