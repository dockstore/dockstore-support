package io.dockstore.utils.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator that logs each prompt and its response text when delegating to another {@link AIModel}.
 */
public class LoggingAIModel extends DelegatingAIModel {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingAIModel.class);

    public LoggingAIModel(AIModel delegate) {
        super(delegate);
    }

    @Override
    public Response submitPrompt(AIModel.Prompt prompt) {
        LOG.info("PROMPT {}", prompt);
        Response responseInfo = super.submitPrompt(prompt);
        LOG.info("RESPONSE {}", responseInfo.text());
        return responseInfo;
    }
}
