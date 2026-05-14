package io.dockstore.utils.ai;

import com.google.gson.annotations.SerializedName;
import io.dockstore.utils.ai.ClaudeResponse.Content;
import java.util.List;

public record ClaudeRequest(@SerializedName(value = "anthropic_version") String anthropicVersion, @SerializedName(value = "max_tokens") int maxTokens, double temperature, String system, List<Message> messages) {
    public record Message(String role, List<Content> content) {

    }
}
