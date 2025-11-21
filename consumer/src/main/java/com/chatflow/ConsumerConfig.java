package com.chatflow;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class ConsumerConfig {
    // Match server configuration
    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String ROOM_QUEUE_PREFIX = "chat.room.";

    // Connection settings
    private static final String RABBITMQ_HOST = System.getenv("RABBITMQ_HOST");
    private static final int RABBITMQ_PORT = Integer.parseInt(System.getenv("RABBITMQ_PORT"));
    private static final String RABBITMQ_USERNAME = System.getenv("RABBITMQ_USERNAME");
    private static final String RABBITMQ_PASSWORD = System.getenv("RABBITMQ_PASSWORD");

    // Performance tuning
    public static final int PREFETCH_COUNT = Integer.parseInt(System.getenv("PREFETCH_COUNT"));
    public static final int CONSUMER_THREADS = Integer.parseInt(System.getenv("CONSUMER_THREADS"));

    // Which rooms to consume from (1-20)
    public static final int ROOM_COUNT = 20;

    private static Connection connection;

    public static Connection getConnection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBITMQ_HOST);
            factory.setPort(RABBITMQ_PORT);
            factory.setUsername(RABBITMQ_USERNAME);
            factory.setPassword(RABBITMQ_PASSWORD);

            // High-performance settings
            factory.setRequestedHeartbeat(30);
            factory.setConnectionTimeout(10000);
            factory.setNetworkRecoveryInterval(10000);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setTopologyRecoveryEnabled(true);

            connection = factory.newConnection("ChatFlow-Consumer");
            System.out.println("RabbitMQ consumer connection established to " + RABBITMQ_HOST + ":" + RABBITMQ_PORT);
        }
        return connection;
    }

    public static Channel createChannel() throws IOException, TimeoutException {
        Connection conn = getConnection();
        Channel channel = conn.createChannel();

        // Set QoS - prefetch messages for better throughput
        channel.basicQos(PREFETCH_COUNT);

        return channel;
    }

    /**
     * Get queue name for a specific room
     */
    public static String getRoomQueueName(int roomId) {
        return ROOM_QUEUE_PREFIX + roomId;
    }

    public static void shutdown() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
                System.out.println("RabbitMQ consumer connection closed");
            }
        } catch (IOException e) {
            System.err.println("Error closing RabbitMQ connection: " + e.getMessage());
        }
    }
}