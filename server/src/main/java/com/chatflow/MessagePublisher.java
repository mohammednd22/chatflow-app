package com.chatflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class MessagePublisher {
    private final ThreadLocal<Channel> channelThreadLocal;
    private final ObjectMapper objectMapper;
    private final AtomicLong publishedCount;
    private final AtomicLong failedPublishCount;
    private final ConcurrentHashMap<String, Boolean> ensuredQueues;

    public MessagePublisher() throws IOException, TimeoutException {
        this.objectMapper = new ObjectMapper();
        this.publishedCount = new AtomicLong(0);
        this.failedPublishCount = new AtomicLong(0);
        this.ensuredQueues = new ConcurrentHashMap<>();

        // Each thread gets its own channel for thread safety
        this.channelThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                Channel channel = RabbitMQConfig.createChannel();
                // Enable publisher confirms for reliability
                channel.confirmSelect();
                System.out.println("Created new publish channel for thread: " +
                        Thread.currentThread().getName());
                return channel;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create channel", e);
            }
        });
    }


    public boolean publishMessage(QueuedMessage queuedMessage) {
        Channel channel = channelThreadLocal.get();
        String roomId = queuedMessage.getRoomId();


        try {
            String messageJson = objectMapper.writeValueAsString(queuedMessage);

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2)
                    .timestamp(new java.util.Date())
                    .build();

//            System.out.println("[PUBLISH] About to call basicPublish...");
//            System.out.println("[PUBLISH]   Exchange: " + RabbitMQConfig.CHAT_EXCHANGE);
//            System.out.println("[PUBLISH]   Routing key: '" + roomId + "'");

            channel.basicPublish(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    roomId,
                    props,
                    messageJson.getBytes()
            );


            long count = publishedCount.incrementAndGet();

            if (count % 10000 == 0) {
                System.out.println("✓ Published " + count + " messages to RabbitMQ");
            }

            return true;

        } catch (Exception e) {
            System.err.println("[PUBLISH] ✗✗✗ EXCEPTION THROWN ✗✗✗");
            System.err.println("[PUBLISH] Exception type: " + e.getClass().getName());
            System.err.println("[PUBLISH] Exception message: " + e.getMessage());
            e.printStackTrace();
            System.err.println("════════════════════════════════════════");

            failedPublishCount.incrementAndGet();
            channelThreadLocal.remove();
            return false;
        }
    }

    public long getPublishedCount() {
        return publishedCount.get();
    }

    public long getFailedPublishCount() {
        return failedPublishCount.get();
    }

    public void cleanup() {
        try {
            Channel channel = channelThreadLocal.get();
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing publisher channel: " + e.getMessage());
        } finally {
            channelThreadLocal.remove();
        }
    }
}