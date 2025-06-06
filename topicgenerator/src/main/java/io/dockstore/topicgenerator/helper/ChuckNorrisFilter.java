package io.dockstore.topicgenerator.helper;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;

/**
 * List from <a href="https://github.com/chucknorris-io/swear-words/tree/master/">GitHub</a>
 * Has a bunch of language options
 */
public class ChuckNorrisFilter extends WordsFilter {

    // Words that are excluded from the filter because they are common in a biology context
    private static final Set<String> EXCLUDED_WORDS = Set.of("sex");

    private final Set<String> offensiveWords;

    public ChuckNorrisFilter(String language) {
        try {
            URL url = new URL("https://raw.githubusercontent.com/chucknorris-io/swear-words/master/" + language);
            Stream<String> lines = IOUtils.toString(url, StandardCharsets.UTF_8).lines().filter(word -> !EXCLUDED_WORDS.contains(word));
            this.offensiveWords = Set.copyOf(lines.collect(Collectors.toSet()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isSuspiciousTopic(String topic) {
        return  isSuspiciousTopic(topic, offensiveWords);
    }
}
