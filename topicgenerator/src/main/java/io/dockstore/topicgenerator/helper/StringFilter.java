package io.dockstore.topicgenerator.helper;

/**
 * Simple interface for checking if a topic sentence is suspicious.
 */
public interface StringFilter {

    /**
     * @param topic
     * @return true on failure  (i.e. should be filtered out and is caught by the filter)
     */
    boolean isSuspiciousTopic(String topic);
}
