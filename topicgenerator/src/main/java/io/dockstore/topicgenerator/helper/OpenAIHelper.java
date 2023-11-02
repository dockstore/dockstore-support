package io.dockstore.topicgenerator.helper;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import java.util.List;

public final class OpenAIHelper {
    private OpenAIHelper() {
    }

    /**
     * Adapted from <a href="https://jtokkit.knuddels.de/docs/getting-started/recipes/chatml">OpenAI cookbook example</a>.
     * Counts the number of tokens in a list of messages, accounting for additional tokens that are added to the input text.
     *
     * @param registry
     * @param model
     * @param messages consists of role, content and an optional name
     * @return
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public static int countMessageTokens(EncodingRegistry registry, String model, List<ChatMessage> messages) {
        Encoding encoding = registry.getEncodingForModel(model).orElseThrow();
        int tokensPerMessage;
        int tokensPerName;
        if (model.startsWith("gpt-4")) {
            tokensPerMessage = 3;
            tokensPerName = 1;
        } else if (model.startsWith("gpt-3.5-turbo")) {
            tokensPerMessage = 4; // every message follows <|start|>{role/name}\n{content}<|end|>\n
            tokensPerName = -1; // if there's a name, the role is omitted
        } else {
            throw new IllegalArgumentException("Unsupported model: " + model);
        }

        int sum = 0;
        for (ChatMessage message : messages) {
            sum += tokensPerMessage;
            sum += encoding.countTokens(message.getContent());
            sum += encoding.countTokens(message.getRole());
            if (message.getName() != null) {
                sum += encoding.countTokens(message.getName());
                sum += tokensPerName;
            }
        }
        sum += 3; // every reply is primed with <|start|>assistant<|message|>
        return sum;
    }

    /**
     * Returns the maximum amount of tokens that the user message content can contain. Assumes that there is one system message and one user message.
     * @param systemMessage
     * @param maxResponseToken
     * @return
     */
    public static int getMaximumAmountOfTokensForUserMessageContent(EncodingRegistry registry, ModelType aiModel, ChatMessage systemMessage, int maxResponseToken) {
        ChatMessage userMessageWithoutContent = new ChatMessage(ChatMessageRole.USER.value());
        List<ChatMessage> messages = List.of(systemMessage, userMessageWithoutContent);

        final int tokenCountWithoutUserContent = countMessageTokens(registry, aiModel.getName(), messages);
        return aiModel.getMaxContextLength() - maxResponseToken - tokenCountWithoutUserContent;
    }
}
