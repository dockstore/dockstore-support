package io.dockstore.utils.ai;

/**
 * Abstract decorator that forwards all {@link AIModel} calls to an inner delegate.
 */
public abstract class DelegatingAIModel implements AIModel {

    private final AIModel delegate;

    protected DelegatingAIModel(AIModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response submitPrompt(Prompt prompt) {
        return delegate.submitPrompt(prompt);
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
