package io.dockstore.tooltester.jenkins;

/**
 * @author gluu
 * @since 10/02/17
 */
public class StageFlowNode {
    //@Checkstyle:off
    private Links _links;
    //@Checkstyle:on
    private String status;

    public String getStatus() {
        return status;
    }

    public Links getLinks() {
        return _links;
    }
}
