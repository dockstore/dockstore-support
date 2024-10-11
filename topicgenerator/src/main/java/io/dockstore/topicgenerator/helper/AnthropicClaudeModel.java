package io.dockstore.topicgenerator.helper;

import static io.dockstore.topicgenerator.client.cli.TopicGeneratorClient.removeSummaryTagsFromTopic;

import com.google.gson.Gson;
import io.dockstore.topicgenerator.helper.ClaudeRequest.Message;
import io.dockstore.topicgenerator.helper.ClaudeResponse.Content;
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
    public AIResponseInfo submitPrompt(String prompt) {
        if (estimateTokens(prompt) > getMaxContextLength()) {
            prompt = prompt.substring(0, getMaxContextLength());
        }

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

        return new AIResponseInfo(removeSummaryTagsFromTopic(aiResponse), false, inputTokens, outputTokens, this.calculatePrice(inputTokens, outputTokens), stopReason);
    }

    // Format the request payload using the model's native structure.
    private String createNativeClaudeRequest(String prompt) {
        // The amount of randomness injected into the response. Ranges from 0 to 1. Pick 0.5 as the middle ground between predictability and creativity.
        final double temperature = 0.5;
        // See https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html#model-parameters-anthropic-claude-messages-request-response for examples
        ClaudeRequest claudeRequest = new ClaudeRequest(ANTHROPIC_API_VERSION, MAX_RESPONSE_TOKENS, temperature, List.of(new Message("user", List.of(new Content("text", prompt)))));
        return GSON.toJson(claudeRequest);
    }
}
