package com.chatflow;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class MessageGenerator implements Runnable {
    private static final String[] MESSAGE_POOL = {
            "Hello everyone!",
            "How's it going?",
            "Great to be here",
            "Anyone online?",
            "This is awesome!",
            "What's up folks?",
            "Good morning!",
            "Have a great day!",
            "Nice to meet you all",
            "Loving this chat!",
            "That's interesting",
            "I agree completely",
            "Tell me more",
            "Sounds good to me",
            "Let's do this!",
            "Count me in",
            "Absolutely right",
            "Makes sense",
            "Thanks for sharing",
            "Appreciate it",
            "Well said",
            "Good point",
            "Exactly my thoughts",
            "Couldn't agree more",
            "You're right about that",
            "That's helpful",
            "Thanks everyone",
            "See you later",
            "Catch you soon",
            "Take care all",
            "Have a good one",
            "Until next time",
            "Looking forward to it",
            "Great discussion",
            "This is fun",
            "Enjoying the conversation",
            "Keep it up",
            "Nice work team",
            "We're doing great",
            "Fantastic job",
            "Love the energy here",
            "This chat rocks",
            "Best community ever",
            "Happy to be here",
            "You all are awesome",
            "Let's keep chatting",
            "More to come",
            "Stay tuned",
            "Be right back",
            "Just checking in"
    };

    private final BlockingQueue<ChatMessage> messageQueue;
    private final int totalMessages;
    private final Random random;

    public MessageGenerator(BlockingQueue<ChatMessage> messageQueue, int totalMessages) {
        this.messageQueue = messageQueue;
        this.totalMessages = totalMessages;
        this.random = new Random();
    }

    @Override
    public void run() {
        System.out.println("Message generator started...");

        for (int i = 0; i < totalMessages; i++) {
            try {
                ChatMessage message = generateRandomMessage();
                messageQueue.put(message);

                if ((i + 1) % 50000 == 0) {
                    System.out.println("Generated " + (i + 1) + " messages...");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Message generator interrupted");
                break;
            }
        }

        System.out.println("Message generator completed. Total generated: " + totalMessages);
    }

    private ChatMessage generateRandomMessage() {
        int userId = random.nextInt(100000) + 1;
        String username = "user" + userId;
        String messageText = MESSAGE_POOL[random.nextInt(MESSAGE_POOL.length)];
        Integer roomId = random.nextInt(20) + 1;
        String messageType = determineMessageType();
        String timestamp = Instant.now().toString();

        if ("JOIN".equals(messageType)) {
            messageText = username + " has joined the chat";
        } else if ("LEAVE".equals(messageType)) {
            messageText = username + " has left the chat";
        }

        return new ChatMessage(userId, username, messageText, timestamp, messageType, roomId);
    }

    private String determineMessageType() {
        int rand = random.nextInt(100);
        if (rand < 90) {
            return "TEXT";
        } else if (rand < 95) {
            return "JOIN";
        } else {
            return "LEAVE";
        }
    }
}