package com.stackup.stackup.common.messaging;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import java.util.Map;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    static final String LISTENER_CONTAINER_FACTORY = "rabbitListenerContainerFactory";

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
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(
            properties.deadLetter().exchange(),
            properties.exchanges().durable(),
            properties.exchanges().autoDelete()
        );
    }

    @Bean
    public Queue aiAnalyzeResumeQueue() {
        return workQueue(properties.queues().names().aiAnalyzeResume());
    }

    @Bean
    public Queue aiAnalyzeRepositoryQueue() {
        return workQueue(properties.queues().names().aiAnalyzeRepository());
    }

    @Bean
    public Queue aiGenerateQuestionsQueue() {
        return workQueue(properties.queues().names().aiGenerateQuestions());
    }

    @Bean
    public Queue aiGenerateFollowupQueue() {
        return workQueue(properties.queues().names().aiGenerateFollowup());
    }

    @Bean
    public Queue aiGenerateFeedbackQueue() {
        return workQueue(properties.queues().names().aiGenerateFeedback());
    }

    @Bean
    public Queue aiAnalyzeVoiceQueue() {
        return workQueue(properties.queues().names().aiAnalyzeVoice());
    }

    @Bean
    public Queue coreCallbackAnalysisQueue() {
        return workQueue(properties.queues().names().coreCallbackAnalysis());
    }

    @Bean
    public Queue coreCallbackQuestionsQueue() {
        return workQueue(properties.queues().names().coreCallbackQuestions());
    }

    @Bean
    public Queue coreCallbackFeedbackQueue() {
        return workQueue(properties.queues().names().coreCallbackFeedback());
    }

    @Bean
    public Queue coreCallbackVoiceQueue() {
        return workQueue(properties.queues().names().coreCallbackVoice());
    }

    @Bean
    public Queue coreCallbackTtsQueue() {
        return workQueue(properties.queues().names().coreCallbackTts());
    }

    @Bean
    public Declarables rabbitDeclarables(
        TopicExchange coreToAiExchange,
        TopicExchange aiToCoreExchange,
        TopicExchange realtimeExchange,
        DirectExchange deadLetterExchange,
        Queue aiAnalyzeResumeQueue,
        Queue aiAnalyzeRepositoryQueue,
        Queue aiGenerateQuestionsQueue,
        Queue aiGenerateFollowupQueue,
        Queue aiGenerateFeedbackQueue,
        Queue aiAnalyzeVoiceQueue,
        Queue coreCallbackAnalysisQueue,
        Queue coreCallbackQuestionsQueue,
        Queue coreCallbackFeedbackQueue,
        Queue coreCallbackVoiceQueue,
        Queue coreCallbackTtsQueue
    ) {
        Queue dlqAiAnalyzeResume = dlq(properties.queues().names().aiAnalyzeResume());
        Queue dlqAiAnalyzeRepository = dlq(properties.queues().names().aiAnalyzeRepository());
        Queue dlqAiGenerateQuestions = dlq(properties.queues().names().aiGenerateQuestions());
        Queue dlqAiGenerateFollowup = dlq(properties.queues().names().aiGenerateFollowup());
        Queue dlqAiGenerateFeedback = dlq(properties.queues().names().aiGenerateFeedback());
        Queue dlqAiAnalyzeVoice = dlq(properties.queues().names().aiAnalyzeVoice());
        Queue dlqCoreCallbackAnalysis = dlq(properties.queues().names().coreCallbackAnalysis());
        Queue dlqCoreCallbackQuestions = dlq(properties.queues().names().coreCallbackQuestions());
        Queue dlqCoreCallbackFeedback = dlq(properties.queues().names().coreCallbackFeedback());
        Queue dlqCoreCallbackVoice = dlq(properties.queues().names().coreCallbackVoice());
        Queue dlqCoreCallbackTts = dlq(properties.queues().names().coreCallbackTts());

        return new Declarables(
            coreToAiExchange,
            aiToCoreExchange,
            realtimeExchange,
            deadLetterExchange,
            aiAnalyzeResumeQueue,
            aiAnalyzeRepositoryQueue,
            aiGenerateQuestionsQueue,
            aiGenerateFollowupQueue,
            aiGenerateFeedbackQueue,
            aiAnalyzeVoiceQueue,
            coreCallbackAnalysisQueue,
            coreCallbackQuestionsQueue,
            coreCallbackFeedbackQueue,
            coreCallbackVoiceQueue,
            coreCallbackTtsQueue,
            dlqAiAnalyzeResume,
            dlqAiAnalyzeRepository,
            dlqAiGenerateQuestions,
            dlqAiGenerateFollowup,
            dlqAiGenerateFeedback,
            dlqAiAnalyzeVoice,
            dlqCoreCallbackAnalysis,
            dlqCoreCallbackQuestions,
            dlqCoreCallbackFeedback,
            dlqCoreCallbackVoice,
            dlqCoreCallbackTts,
            BindingBuilder.bind(aiAnalyzeResumeQueue).to(coreToAiExchange).with(properties.routingKeys().analyzeResume()),
            BindingBuilder.bind(aiAnalyzeRepositoryQueue).to(coreToAiExchange).with(properties.routingKeys().analyzeRepository()),
            BindingBuilder.bind(aiGenerateQuestionsQueue).to(coreToAiExchange).with(properties.routingKeys().generateQuestions()),
            BindingBuilder.bind(aiGenerateFollowupQueue).to(coreToAiExchange).with(properties.routingKeys().generateFollowup()),
            BindingBuilder.bind(aiGenerateFeedbackQueue).to(coreToAiExchange).with(properties.routingKeys().generateFeedback()),
            BindingBuilder.bind(aiAnalyzeVoiceQueue).to(coreToAiExchange).with(properties.routingKeys().analyzeVoice()),
            BindingBuilder.bind(coreCallbackAnalysisQueue).to(aiToCoreExchange).with(properties.routingKeys().callbackAnalysis()),
            BindingBuilder.bind(coreCallbackQuestionsQueue).to(aiToCoreExchange).with(properties.routingKeys().callbackQuestions()),
            BindingBuilder.bind(coreCallbackFeedbackQueue).to(aiToCoreExchange).with(properties.routingKeys().callbackFeedback()),
            BindingBuilder.bind(coreCallbackVoiceQueue).to(aiToCoreExchange).with(properties.routingKeys().callbackVoice()),
            BindingBuilder.bind(coreCallbackTtsQueue).to(aiToCoreExchange).with(properties.routingKeys().callbackTts()),
            BindingBuilder.bind(dlqAiAnalyzeResume).to(deadLetterExchange).with(dlqAiAnalyzeResume.getName()),
            BindingBuilder.bind(dlqAiAnalyzeRepository).to(deadLetterExchange).with(dlqAiAnalyzeRepository.getName()),
            BindingBuilder.bind(dlqAiGenerateQuestions).to(deadLetterExchange).with(dlqAiGenerateQuestions.getName()),
            BindingBuilder.bind(dlqAiGenerateFollowup).to(deadLetterExchange).with(dlqAiGenerateFollowup.getName()),
            BindingBuilder.bind(dlqAiGenerateFeedback).to(deadLetterExchange).with(dlqAiGenerateFeedback.getName()),
            BindingBuilder.bind(dlqAiAnalyzeVoice).to(deadLetterExchange).with(dlqAiAnalyzeVoice.getName()),
            BindingBuilder.bind(dlqCoreCallbackAnalysis).to(deadLetterExchange).with(dlqCoreCallbackAnalysis.getName()),
            BindingBuilder.bind(dlqCoreCallbackQuestions).to(deadLetterExchange).with(dlqCoreCallbackQuestions.getName()),
            BindingBuilder.bind(dlqCoreCallbackFeedback).to(deadLetterExchange).with(dlqCoreCallbackFeedback.getName()),
            BindingBuilder.bind(dlqCoreCallbackVoice).to(deadLetterExchange).with(dlqCoreCallbackVoice.getName()),
            BindingBuilder.bind(dlqCoreCallbackTts).to(deadLetterExchange).with(dlqCoreCallbackTts.getName())
            // 주의: q.realtime.session.notify 큐 + binding (+ DLQ) 은 RealTime 서버 측에서
            // infra/rabbitmq/definitions.json 으로 import 되므로 Core 는 exchange 만 declare.
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

    @Bean(name = LISTENER_CONTAINER_FACTORY)
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private MethodInterceptor retryInterceptor() {
        RabbitMqProperties.Retry retry = properties.retry();
        return RetryInterceptorBuilder.stateless()
            .maxRetries(retry.maxRetries())
            .backOffOptions(
                retry.initialInterval().toMillis(),
                retry.multiplier(),
                retry.maxInterval().toMillis()
            )
            .recoverer(new RejectAndDontRequeueRecoverer())
            .build();
    }

    private Queue workQueue(String name) {
        return QueueBuilder.durable(name)
            .withArguments(deadLetterArguments(name))
            .build();
    }

    private Queue dlq(String workQueueName) {
        return QueueBuilder.durable(dlqName(workQueueName)).build();
    }

    private Map<String, Object> deadLetterArguments(String workQueueName) {
        return Map.of(
            "x-dead-letter-exchange", properties.deadLetter().exchange(),
            "x-dead-letter-routing-key", dlqName(workQueueName)
        );
    }

    private String dlqName(String workQueueName) {
        return properties.deadLetter().routingKeyPrefix() + workQueueName;
    }
}
