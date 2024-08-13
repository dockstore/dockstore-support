package io.dockstore.topicgenerator.helper;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * List from <a href="https://www.frontgatemedia.com/a-list-of-723-bad-words-to-blacklist-and-how-to-use-facebooks-moderation-tool/">Christian blog</a>
 * Coded it up before realizing the POV, but may as well have it as an example and see what happens.
 * Blocks a bit too much. e.g. "breast" in "breast cancer"
 */
public class ChristianFilter extends WordsFilter {

    private final Set<String> offensiveWords = new HashSet<>();

    public ChristianFilter() {
        try {
            URL url = new URL("https://raw.githubusercontent.com/jesobreira/offensive-words-filter/master/terms-to-block.json");
            InputStreamReader reader = new InputStreamReader(url.openStream());
            String[] strings = new Gson().fromJson(reader, String[].class);
            offensiveWords.addAll(Arrays.stream(strings).toList());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isSuspiciousTopic(String topic) {
        return isSuspiciousTopic(topic, offensiveWords);
    }
}
