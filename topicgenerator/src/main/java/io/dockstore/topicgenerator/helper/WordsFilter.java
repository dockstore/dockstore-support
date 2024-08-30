package io.dockstore.topicgenerator.helper;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility class to help implement StringFilter when viewing a sentence as a set of words.
 */
public abstract class WordsFilter implements StringFilter {

    /**
     * Utility method for screening topics against sets of offensive words.
     *
     * @param topic
     * @param offensiveWords
     * @return
     */
    protected boolean isSuspiciousTopic(String topic, Set<String> offensiveWords) {
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
