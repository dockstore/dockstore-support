package io.dockstore.utils.ai;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record ClaudeRequest(@SerializedName(value = "anthropic_version") String anthropicVersion, @SerializedName(value = "max_tokens") int maxTokens, double temperature, String system, List<Message> messages) {
    public record Message(String role, List<Content> content) {

    }
    public record Content(String type, String text, @SerializedName(value = "cache_control") CacheControl cacheControl) {
    }
    public record CacheControl(String type) {
    }
}
