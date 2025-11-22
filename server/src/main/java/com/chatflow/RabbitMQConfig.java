package com.chatflow;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RabbitMQConfig {
    // Exchange names
    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String DLX_EXCHANGE = "chat.dlx.exchange";

    // Queue name pattern - one queue per room
    public static final String ROOM_QUEUE_PREFIX = "chat.room.";
    public static final String DLQ_QUEUE = "chat.dlq";

    // Connection settings
    private static final String RABBITMQ_HOST = System.getenv("RABBITMQ_HOST");
    private static final int RABBITMQ_PORT = Integer.parseInt(System.getenv("RABBITMQ_PORT"));
    private static final String RABBITMQ_USERNAME = System.getenv("RABBITMQ_USERNAME");
    private static final String RABBITMQ_PASSWORD = System.getenv("RABBITMQ_PASSWORD");

    private static Connection connection;

    public static void initialize() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setPort(RABBITMQ_PORT);
        factory.setUsername(RABBITMQ_USERNAME);
        factory.setPassword(RABBITMQ_PASSWORD);

        // Connection settings for high throughput
        factory.setRequestedHeartbeat(30);
        factory.setConnectionTimeout(10000);
        factory.setNetworkRecoveryInterval(10000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);

        connection = factory.newConnection("ChatFlow-Server");

        // Setup exchanges and DLQ
        Channel setupChannel = connection.createChannel();
        setupExchangesAndDLQ(setupChannel);
        setupChannel.close();

        System.out.println("RabbitMQ connection established to " + RABBITMQ_HOST + ":" + RABBITMQ_PORT);
    }

    private static void setupExchangesAndDLQ(Channel channel) throws IOException {
        // Declare Dead Letter Exchange
        channel.exchangeDeclare(DLX_EXCHANGE, "direct", true);

        // Declare Dead Letter Queue
        channel.queueDeclare(DLQ_QUEUE, true, false, false, null);
        channel.queueBind(DLQ_QUEUE, DLX_EXCHANGE, "dlq");

        // Declare main exchange (use roomId as routing key)
        channel.exchangeDeclare(CHAT_EXCHANGE, "direct", true);

        System.out.println("RabbitMQ exchanges and DLQ configured");
    }

    /**
     * Create or ensure a queue exists for a specific room
     */
    public static void ensureRoomQueue(Channel channel, String roomId) throws IOException {
        String queueName = ROOM_QUEUE_PREFIX + roomId;

        Map<String, Object> queueArgs = new HashMap<>();
        queueArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);
        queueArgs.put("x-dead-letter-routing-key", "dlq");
        queueArgs.put("x-max-length", 50000); // Per-room limit

        // Declare queue (idempotent - safe to call multiple times)
        channel.queueDeclare(queueName, true, false, false, queueArgs);

        // Bind queue to exchange with roomId as routing key
        channel.queueBind(queueName, CHAT_EXCHANGE, roomId);
    }

    public static String getRoomQueueName(String roomId) {
        return ROOM_QUEUE_PREFIX + roomId;
    }

    public static Connection getConnection() {
        return connection;
    }

    public static Channel createChannel() throws IOException {
        Channel channel = connection.createChannel();
        return channel;
    }

    public static void shutdown() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
            System.out.println("RabbitMQ connection closed");
        } catch (IOException e) {
            System.err.println("Error closing RabbitMQ connection: " + e.getMessage());
        }
    }
}