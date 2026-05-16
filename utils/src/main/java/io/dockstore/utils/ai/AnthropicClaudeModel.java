package io.dockstore.utils.ai;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

public class AnthropicClaudeModel extends BaseAIModel {
    private static final Logger LOG = LoggerFactory.getLogger(AnthropicClaudeModel.class);
    private static final Gson GSON = new Gson();
    // Anthropic API version must be the value below.
    // See https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html#model-parameters-anthropic-claude-messages-request-response
    private static final String ANTHROPIC_API_VERSION = "bedrock-2023-05-31";

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public AnthropicClaudeModel(AIModelType anthropicModel) {
        super(anthropicModel);
        bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public AIResponseInfo submitPrompt(Prompt prompt) {
        final String nativeRequest = createNativeClaudeRequest(prompt);

        // Encode and send the request to the Bedrock Runtime.
        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request -> request
                .body(SdkBytes.fromUtf8String(nativeRequest))
                .modelId(this.getModelName())
        );

        ClaudeResponse claudeResponse = GSON.fromJson(response.body().asUtf8String(), ClaudeResponse.class);

        final String aiResponse = claudeResponse.content().get(0).text();
        final String stopReason = claudeResponse.stopReason();
        final long inputTokens = claudeResponse.usage().inputTokens();
        final long outputTokens = claudeResponse.usage().outputTokens();

        return new AIResponseInfo(aiResponse, false, inputTokens, outputTokens, this.calculatePrice(inputTokens, outputTokens), stopReason);
    }

    // Format the request payload using the model's native structure.
    // See https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html#model-parameters-anthropic-claude-messages-request-response for examples
    private String createNativeClaudeRequest(Prompt prompt) {
        List<ClaudeRequest.Content> systemContent = nullIfEmpty(toClaudeContent(prompt.systemContent()));
        List<ClaudeRequest.Message> userMessages = List.of(toClaudeUserMessage(prompt.userContent()));
        ClaudeRequest claudeRequest = new ClaudeRequest(
            ANTHROPIC_API_VERSION,
            prompt.maxResponseTokens(),
            prompt.temperature(),
            systemContent,
            userMessages);
        return GSON.toJson(claudeRequest);
    }

    private List<ClaudeRequest.Content> toClaudeContent(List<AIModel.Content> content) {
        List<ClaudeRequest.Content> result = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i) instanceof Textable textable) {
                boolean cacheable = i + 1 < content.size() && content.get(i + 1) instanceof AIModel.CacheMarker;
                result.add(new ClaudeRequest.Content("text", textable.toText(), cacheable ? new ClaudeRequest.CacheControl("ephemeral") : null));
            }
        }
        return result;
    }

    private ClaudeRequest.Message toClaudeUserMessage(List<AIModel.Content> content) {
        List<ClaudeRequest.Content> contents = toClaudeContent(content);
        return new ClaudeRequest.Message("user", contents);
    }

    private <T> List<T> nullIfEmpty(List<T> values) {
        return values.isEmpty() ? null : values;
    }
}
