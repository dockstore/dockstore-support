package io.dockstore.topicgenerator.helper;

import static io.dockstore.topicgenerator.client.cli.TopicGeneratorClient.removeSummaryTagsFromTopic;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class OpenAIModel extends BaseAIModel {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAIModel.class);
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    private final OpenAiService openAiService;
    private final ModelType aiModel;
    private final Encoding encoding;


    public OpenAIModel(String openaiApiKey, AIModelType aiModelType) {
        super(aiModelType);
        openAiService = new OpenAiService(openaiApiKey);
        aiModel = ModelType.fromName(aiModelType.getModelId()).orElseThrow(() -> new RuntimeException("Invalid OpenAI model type " + aiModelType.getModelId()));
        encoding = REGISTRY.getEncodingForModel(aiModel);
    }

    @Override
    public AIResponseInfo submitPrompt(String prompt) {
        // Chat completion API calls include additional tokens for message-based formatting. Calculate how long the descriptor content can be and truncate if needed
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);
        boolean isPromptTruncated = false;
        if (estimateTokens(prompt) > getMaxContextLength()) {
            final EncodingResult encoded = encoding.encode(prompt, getMaxContextLength()); // Encodes the prompt up to the maximum number of tokens specified
            final String truncatedPrompt = encoding.decode(encoded.getTokens()); // Decode the tokens to get the truncated content string
            userMessage.setContent(truncatedPrompt);
            isPromptTruncated = encoded.isTruncated();
        }

        final List<ChatMessage> messages = List.of(userMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model(aiModel.getName())
                .messages(messages)
                .n(1)
                .maxTokens(MAX_RESPONSE_TOKENS)
                .logitBias(new HashMap<>())
                .build();
        final ChatCompletionResult chatCompletionResult = openAiService.createChatCompletion(chatCompletionRequest);

        if (chatCompletionResult.getChoices().isEmpty()) {
            // I don't think this should happen, but check anyway
            throw new RuntimeException("AI model failed to return a response");
        }

        final ChatCompletionChoice chatCompletionChoice = chatCompletionResult.getChoices().get(0);
        final String aiResponse = chatCompletionChoice.getMessage().getContent();
        final String finishReason = chatCompletionChoice.getFinishReason();
        final long promptTokens = chatCompletionResult.getUsage().getPromptTokens();
        final long completionTokens = chatCompletionResult.getUsage().getCompletionTokens();
        return new AIResponseInfo(removeSummaryTagsFromTopic(aiResponse), isPromptTruncated, promptTokens, completionTokens, this.calculatePrice(promptTokens, completionTokens), finishReason);
    }

    @Override
    public int estimateTokens(String prompt) {
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);
        return OpenAIHelper.countMessageTokens(REGISTRY, aiModel.getName(), List.of(userMessage));
    }
}
