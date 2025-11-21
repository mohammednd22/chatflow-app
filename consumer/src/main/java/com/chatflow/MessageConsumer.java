package com.chatflow;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageConsumer implements Runnable {
    private final int consumerId;
    private final int roomId; // NEW: Each consumer handles specific room(s)
    private final MessageProcessor processor;
    private final AtomicBoolean running;
    private Channel channel;
    private String consumerTag;

    public MessageConsumer(int consumerId, int roomId, MessageProcessor processor) {
        this.consumerId = consumerId;
        this.roomId = roomId;
        this.processor = processor;
        this.running = new AtomicBoolean(false);
    }

    @Override
    public void run() {
        try {
            channel = ConsumerConfig.createChannel();
            running.set(true);

            String queueName = ConsumerConfig.getRoomQueueName(roomId);
            System.out.println("Consumer #" + consumerId + " started for room " + roomId +
                    " (queue: " + queueName + ")");

            // Track delivery tags for batching
            final long[] lastAckTag = {0};
            final int BATCH_SIZE = 100; // Ack every 100 messages
            final AtomicInteger batchCount = new AtomicInteger(0);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String messageBody = new String(delivery.getBody(), "UTF-8");

                try {
                    boolean success = processor.processMessage(messageBody);
                    long deliveryTag = delivery.getEnvelope().getDeliveryTag();

                    if (success) {
                        lastAckTag[0] = deliveryTag;

                        // Batch ack every BATCH_SIZE messages
                        if (batchCount.incrementAndGet() >= BATCH_SIZE) {
                            if (channel.isOpen()) {
                                channel.basicAck(lastAckTag[0], true); // true = multiple ack
                            }
                            batchCount.set(0);
                        }
                    } else {
                        // Failed messages - ack individually and nack
                        if (channel.isOpen()) {
                            channel.basicNack(deliveryTag, false, false);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Consumer #" + consumerId + " error: " + e.getMessage());
                    try {
                        if (channel.isOpen()) {
                            channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
            };

            CancelCallback cancelCallback = consumerTag -> {
                // Final ack of remaining messages
                if (lastAckTag[0] > 0 && channel.isOpen()) {
                    try {
                        channel.basicAck(lastAckTag[0], true);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                System.out.println("Consumer #" + consumerId + " (room " + roomId + ") was cancelled");
                running.set(false);
            };

            consumerTag = channel.basicConsume(
                    queueName,
                    false,
                    "consumer-" + consumerId + "-room-" + roomId,
                    deliverCallback,
                    cancelCallback
            );

            while (running.get() && channel.isOpen()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } catch (IOException | TimeoutException e) {
            System.err.println("Consumer #" + consumerId + " (room " + roomId + ") failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        running.set(false);
        try {
            if (channel != null && channel.isOpen()) {
                if (consumerTag != null) {
                    channel.basicCancel(consumerTag);
                }
                channel.close();
            }
            System.out.println("Consumer #" + consumerId + " (room " + roomId + ") shut down");
        } catch (Exception e) {
            System.err.println("Error shutting down consumer #" + consumerId + ": " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}