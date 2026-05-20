package com.stackup.stackup.common.messaging;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    private final RabbitMqProperties properties;

    public RabbitMqConfig(RabbitMqProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TopicExchange coreToAiExchange() {
        return new TopicExchange(
            properties.exchanges().names().coreToAi(),
            properties.exchanges().durable(),
            properties.exchanges().autoDelete()
        );
    }

    @Bean
    public TopicExchange aiToCoreExchange() {
        return new TopicExchange(
            properties.exchanges().names().aiToCore(),
            properties.exchanges().durable(),
            properties.exchanges().autoDelete()
        );
    }

    @Bean
    public TopicExchange realtimeExchange() {
        return new TopicExchange(
            properties.exchanges().names().realtime(),
            properties.exchanges().durable(),
            properties.exchanges().autoDelete()
        );
    }

    @Bean
    public Queue aiAnalyzeResumeQueue() {
        return new Queue(properties.queues().names().aiAnalyzeResume(), properties.queues().durable());
    }

    @Bean
    public Queue aiAnalyzeRepositoryQueue() {
        return new Queue(properties.queues().names().aiAnalyzeRepository(), properties.queues().durable());
    }

    @Bean
    public Queue aiGenerateQuestionsQueue() {
        return new Queue(properties.queues().names().aiGenerateQuestions(), properties.queues().durable());
    }

    @Bean
    public Queue aiGenerateFollowupQueue() {
        return new Queue(properties.queues().names().aiGenerateFollowup(), properties.queues().durable());
    }

    @Bean
    public Queue coreCallbackAnalysisQueue() {
        return new Queue(properties.queues().names().coreCallbackAnalysis(), properties.queues().durable());
    }

    @Bean
    public Queue coreCallbackQuestionsQueue() {
        return new Queue(properties.queues().names().coreCallbackQuestions(), properties.queues().durable());
    }

    @Bean
    public Declarables rabbitDeclarables(
        TopicExchange coreToAiExchange,
        TopicExchange aiToCoreExchange,
        TopicExchange realtimeExchange,
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
            realtimeExchange,
            aiAnalyzeResumeQueue,
            aiAnalyzeRepositoryQueue,
            aiGenerateQuestionsQueue,
            aiGenerateFollowupQueue,
            coreCallbackAnalysisQueue,
            coreCallbackQuestionsQueue,
            BindingBuilder.bind(aiAnalyzeResumeQueue).to(coreToAiExchange).with(properties.routingKeys().analyzeResume()),
            BindingBuilder.bind(aiAnalyzeRepositoryQueue).to(coreToAiExchange).with(properties.routingKeys().analyzeRepository()),
            BindingBuilder.bind(aiGenerateQuestionsQueue).to(coreToAiExchange).with(properties.routingKeys().generateQuestions()),
            BindingBuilder.bind(aiGenerateFollowupQueue).to(coreToAiExchange).with(properties.routingKeys().generateFollowup()),
            BindingBuilder.bind(coreCallbackAnalysisQueue).to(aiToCoreExchange).with(properties.routingKeys().callbackAnalysis()),
            BindingBuilder.bind(coreCallbackQuestionsQueue).to(aiToCoreExchange).with(properties.routingKeys().callbackQuestions())
            // 주의: q.realtime.session.notify 큐 + binding 은 RealTime 서버가 자체 declare
            // (또는 infra/rabbitmq/definitions.json). Core 는 exchange 만 declare.
        );
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(properties.template().mandatory());
        return rabbitTemplate;
    }
}
