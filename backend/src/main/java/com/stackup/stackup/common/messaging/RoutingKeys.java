package com.stackup.stackup.common.messaging;

public final class RoutingKeys {

    public static final String CORE_TO_AI_EXCHANGE = "stackup.core-to-ai";
    public static final String AI_TO_CORE_EXCHANGE = "stackup.ai-to-core";

    public static final String ANALYZE_RESUME = "analyze.resume";
    public static final String ANALYZE_REPOSITORY = "analyze.repository";
    public static final String GENERATE_QUESTIONS = "generate.questions";
    public static final String GENERATE_FOLLOWUP = "generate.followup";
    public static final String CALLBACK_ANALYSIS = "callback.analysis";
    public static final String CALLBACK_QUESTIONS = "callback.questions";

    private RoutingKeys() {
    }
}
