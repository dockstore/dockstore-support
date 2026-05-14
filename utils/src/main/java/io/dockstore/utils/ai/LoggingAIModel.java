package io.dockstore.utils.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingAIModel implements AIModel {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingAIModel.class);

    private final AIModel delegate;

    public LoggingAIModel(AIModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public AIResponseInfo submitPrompt(AIModel.Prompt prompt) {
        LOG.info("PROMPT {}", prompt);
        AIResponseInfo responseInfo = delegate.submitPrompt(prompt);
        LOG.info("RESPONSE {}", responseInfo.aiResponse());
        return responseInfo;
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }

    @Override
    public double getPricePerInputToken() {
        return delegate.getPricePerInputToken();
    }

    @Override
    public double getPricePerOutputToken() {
        return delegate.getPricePerOutputToken();
    }

    @Override
    public int getMaxContextLength() {
        return delegate.getMaxContextLength();
    }
}
