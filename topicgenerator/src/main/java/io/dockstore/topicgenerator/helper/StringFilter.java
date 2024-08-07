package io.dockstore.topicgenerator.helper;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public interface StringFilter {

    /**
     * @param topic
     * @return true on failure  (i.e. should be filtered out and is caught by the filter)
     */
    boolean assessTopic(String topic);


    /**
     * Utility method for screening topics against sets of offensive words.
     *
     * @param topic
     * @param offensiveWords
     * @return
     */
    default boolean assessTopic(String topic, Set<String> offensiveWords) {
        String[] split = StringUtils.split(topic);
        for (String str : split) {
            // strip punctuation
            if (offensiveWords.contains(str.replaceAll("\\p{Punct}", "").toLowerCase())) {
                return true;
            }
        }
        return false;
    }

}
