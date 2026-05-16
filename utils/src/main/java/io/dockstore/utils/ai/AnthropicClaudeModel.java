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
    public AIResponseInfo submitPrompt(AIModel.Prompt prompt) {
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
        List<ClaudeRequest.Content> systemContents = toClaudeContents(prompt.systemMessages());
        ClaudeRequest.Message userMessage = toClaudeUserMessage(prompt.userMessages());
        ClaudeRequest claudeRequest = new ClaudeRequest(ANTHROPIC_API_VERSION, prompt.maxResponseTokens(), prompt.temperature(), nullIfEmpty(systemContents), List.of(userMessage));
        return GSON.toJson(claudeRequest);
    }

    private List<ClaudeRequest.Content> toClaudeContents(List<AIModel.Message> messages) {
        List<ClaudeRequest.Content> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof AIModel.Text text) {
                boolean cacheable = i + 1 < messages.size() && messages.get(i + 1) instanceof AIModel.CacheMarker;
                result.add(new ClaudeRequest.Content("text", text.text(), cacheable ? new ClaudeRequest.CacheControl("ephemeral") : null));
            }
        }
        return result;
    }

    private ClaudeRequest.Message toClaudeUserMessage(List<AIModel.Message> messages) {
        List<ClaudeRequest.Content> contents = toClaudeContents(messages);
        return new ClaudeRequest.Message("user", contents);
    }

    private <T> List<T> nullIfEmpty(List<T> values) {
        return values.isEmpty() ? null : values;
    }
}
