package com.gihara.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.notification.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.notification.queue}")
    private String queueName;

    @Value("${rabbitmq.notification.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.notification.dlx}")
    private String deadLetterExchangeName;

    @Value("${rabbitmq.notification.dlq}")
    private String deadLetterQueueName;

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public TopicExchange notificationDeadLetterExchange() {
        return new TopicExchange(deadLetterExchangeName, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", deadLetterExchangeName)
                .withArgument("x-dead-letter-routing-key", deadLetterQueueName)
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueueName).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with(routingKey);
    }

    @Bean
    public Binding notificationDeadLetterBinding() {
        return BindingBuilder.bind(notificationDeadLetterQueue())
                .to(notificationDeadLetterExchange())
                .with(deadLetterQueueName);
    }

    @Bean
    public Declarables notificationDeclarables() {
        return new Declarables(
                notificationExchange(),
                notificationDeadLetterExchange(),
                notificationQueue(),
                notificationDeadLetterQueue(),
                notificationBinding(),
                notificationDeadLetterBinding()
        );
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonJsonMessageConverter() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jacksonJsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonJsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setErrorHandler(new ConditionalRejectingErrorHandler());
        return factory;
    }
}
