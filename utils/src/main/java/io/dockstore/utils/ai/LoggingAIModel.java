package io.dockstore.utils.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingAIModel extends DelegatingAIModel {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingAIModel.class);

    public LoggingAIModel(AIModel delegate) {
        super(delegate);
    }

    @Override
    public AIResponseInfo submitPrompt(AIModel.Prompt prompt) {
        LOG.info("PROMPT {}", prompt);
        AIResponseInfo responseInfo = super.submitPrompt(prompt);
        LOG.info("RESPONSE {}", responseInfo.aiResponse());
        return responseInfo;
    }
}
