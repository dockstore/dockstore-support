package io.dockstore.topicgenerator.helper;

import static io.dockstore.topicgenerator.client.cli.TopicGeneratorClient.removeSummaryTagsFromTopic;

import com.google.gson.Gson;
import io.dockstore.topicgenerator.helper.ClaudeRequest.Message;
import io.dockstore.topicgenerator.helper.ClaudeResponse.Content;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

public class AnthropicClaudeModel extends AIModel {
    private static final Logger LOG = LoggerFactory.getLogger(AnthropicClaudeModel.class);
    private static final Gson GSON = new Gson();

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public AnthropicClaudeModel(AIModelType anthropicModel) {
        super(anthropicModel);
        bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public Optional<AIResponseInfo> generateTopic(String prompt) {
        if (estimateTokens(prompt) > getMaxContextLength()) {
            prompt = prompt.substring(0, getMaxContextLength());
        }

        final String nativeRequest = createClaudeRequest(prompt);

        try {
            // Encode and send the request to the Bedrock Runtime.
            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request -> request
                    .body(SdkBytes.fromUtf8String(nativeRequest))
                    .modelId(this.getModelName())
            );

            ClaudeResponse claudeResponse = GSON.fromJson(response.body().asUtf8String(), ClaudeResponse.class);

            final String aiGeneratedTopic = claudeResponse.content().get(0).text();
            final String stopReason = claudeResponse.stopReason();
            final long inputTokens = claudeResponse.usage().inputTokens();
            final long outputTokens = claudeResponse.usage().outputTokens();

            return Optional.of(new AIResponseInfo(removeSummaryTagsFromTopic(aiGeneratedTopic), false, inputTokens, outputTokens, this.calculatePrice(inputTokens, outputTokens), stopReason));
        } catch (SdkClientException e) {
            LOG.error("Could not invoke model {}", this.getModelName(), e);
        }
        return Optional.empty();
    }

    private String createClaudeRequest(String prompt) {
        final double temperature = 0.5;
        final String anthropicVersion = "bedrock-2023-05-31"; // Must be this value
        ClaudeRequest claudeRequest = new ClaudeRequest(anthropicVersion, MAX_RESPONSE_TOKENS, temperature, List.of(new Message("user", List.of(new Content("text", prompt)))));
        return GSON.toJson(claudeRequest);
    }
}
