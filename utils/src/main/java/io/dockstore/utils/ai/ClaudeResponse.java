package io.dockstore.utils.ai;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record ClaudeResponse(String id, String type, String role, String model, List<Content> content, @SerializedName(value = "stop_reason") String stopReason, @SerializedName(value = "stop_sequence") String stopSequence, Usage usage) {

    public record Content(String type, String text) {
    }

    public record Usage(@SerializedName(value = "input_tokens") long inputTokens, @SerializedName(value = "cache_creation_input_tokens") long cacheWriteTokens, @SerializedName(value = "cache_read_input_tokens") long cacheReadTokens, @SerializedName(value = "output_tokens") long outputTokens) {
    }
}
