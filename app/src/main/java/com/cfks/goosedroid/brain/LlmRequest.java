package com.cfks.goosedroid.brain;

/**
 * Pedido a un modelo de lenguaje. Inmutable.
 */
public final class LlmRequest {
    public static final int DEFAULT_MAX_TOKENS = 160;
    public static final float DEFAULT_TEMPERATURE = 0.9f;
    private static final float MAX_TEMPERATURE = 2f;

    public final String systemPrompt;
    public final String userPrompt;
    public final int maxTokens;
    public final float temperature;
    /** true si la respuesta debe ser un objeto JSON. */
    public final boolean isJsonExpected;

    private LlmRequest(Builder builder) {
        this.systemPrompt = builder.systemPrompt;
        this.userPrompt = builder.userPrompt;
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.isJsonExpected = builder.isJsonExpected;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String systemPrompt = "";
        private String userPrompt = "";
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private float temperature = DEFAULT_TEMPERATURE;
        private boolean isJsonExpected = true;

        public Builder systemPrompt(String value) {
            this.systemPrompt = value != null ? value : "";
            return this;
        }

        public Builder userPrompt(String value) {
            this.userPrompt = value != null ? value : "";
            return this;
        }

        public Builder maxTokens(int value) {
            this.maxTokens = Math.max(1, value);
            return this;
        }

        public Builder temperature(float value) {
            this.temperature = Math.max(0f, Math.min(value, MAX_TEMPERATURE));
            return this;
        }

        public Builder jsonExpected(boolean value) {
            this.isJsonExpected = value;
            return this;
        }

        public LlmRequest build() {
            if (userPrompt.isEmpty()) {
                throw new IllegalStateException("userPrompt es obligatorio");
            }
            return new LlmRequest(this);
        }
    }
}
