package com.stackup.stackup.common.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange coreToAiExchange() {
        return new TopicExchange(RoutingKeys.CORE_TO_AI_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange aiToCoreExchange() {
        return new TopicExchange(RoutingKeys.AI_TO_CORE_EXCHANGE, true, false);
    }

    @Bean
    public Queue aiAnalyzeResumeQueue() {
        return new Queue("ai.analyze.resume", true);
    }

    @Bean
    public Queue aiAnalyzeRepositoryQueue() {
        return new Queue("ai.analyze.repository", true);
    }

    @Bean
    public Queue aiGenerateQuestionsQueue() {
        return new Queue("ai.generate.questions", true);
    }

    @Bean
    public Queue aiGenerateFollowupQueue() {
        return new Queue("ai.generate.followup", true);
    }

    @Bean
    public Queue coreCallbackAnalysisQueue() {
        return new Queue("core.callback.analysis", true);
    }

    @Bean
    public Queue coreCallbackQuestionsQueue() {
        return new Queue("core.callback.questions", true);
    }

    @Bean
    public Declarables rabbitDeclarables(
        TopicExchange coreToAiExchange,
        TopicExchange aiToCoreExchange,
        Queue aiAnalyzeResumeQueue,
        Queue aiAnalyzeRepositoryQueue,
        Queue aiGenerateQuestionsQueue,
        Queue aiGenerateFollowupQueue,
        Queue coreCallbackAnalysisQueue,
        Queue coreCallbackQuestionsQueue
    ) {
        return new Declarables(
            coreToAiExchange,
            aiToCoreExchange,
            aiAnalyzeResumeQueue,
            aiAnalyzeRepositoryQueue,
            aiGenerateQuestionsQueue,
            aiGenerateFollowupQueue,
            coreCallbackAnalysisQueue,
            coreCallbackQuestionsQueue,
            BindingBuilder.bind(aiAnalyzeResumeQueue).to(coreToAiExchange).with(RoutingKeys.ANALYZE_RESUME),
            BindingBuilder.bind(aiAnalyzeRepositoryQueue).to(coreToAiExchange).with(RoutingKeys.ANALYZE_REPOSITORY),
            BindingBuilder.bind(aiGenerateQuestionsQueue).to(coreToAiExchange).with(RoutingKeys.GENERATE_QUESTIONS),
            BindingBuilder.bind(aiGenerateFollowupQueue).to(coreToAiExchange).with(RoutingKeys.GENERATE_FOLLOWUP),
            BindingBuilder.bind(coreCallbackAnalysisQueue).to(aiToCoreExchange).with(RoutingKeys.CALLBACK_ANALYSIS),
            BindingBuilder.bind(coreCallbackQuestionsQueue).to(aiToCoreExchange).with(RoutingKeys.CALLBACK_QUESTIONS)
        );
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }
}
