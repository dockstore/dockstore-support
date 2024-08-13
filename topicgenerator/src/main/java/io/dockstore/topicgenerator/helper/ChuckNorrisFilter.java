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

    private final Set<String> offensiveWords;

    public ChuckNorrisFilter(String language) {
        try {
            URL url = new URL("https://raw.githubusercontent.com/chucknorris-io/swear-words/master/" + language);
            Stream<String> lines = IOUtils.toString(url, StandardCharsets.UTF_8).lines();
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
