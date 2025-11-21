# ChatFlow - Distributed Chat System

A scalable, distributed real-time messaging platform built with WebSockets, RabbitMQ, Redis, and PostgreSQL. Designed to handle high-throughput message delivery with persistent storage and real-time analytics.

## Architecture
```
Client → Load Balancer → WebSocket Servers → RabbitMQ → Consumers → Redis Pub/Sub → PostgreSQL
```

### Components

- **Server** - WebSocket servers handling client connections and message validation
- **Consumer** - Message processors with async database persistence and Redis broadcasting
- **Client** - Load testing client with performance metrics collection
- **Database** - PostgreSQL with time-series partitioning for message persistence

## Features

- ✅ Real-time WebSocket messaging across 20 chat rooms
- ✅ Message persistence with batch writing (1,000 msg/batch)
- ✅ High-throughput processing (50,000+ msg/s at consumer)
- ✅ Load balancing with sticky sessions
- ✅ Redis pub/sub for room-based broadcasting
- ✅ Comprehensive performance metrics and analytics
- ✅ Circuit breaker and connection pooling

## Project Structure
```
chatflow-app/
├── server/              # WebSocket server + metrics API
├── consumer/            # Message consumer with persistence
├── client/              # Load testing client
├── database/            # PostgreSQL schema and setup scripts
└── results/             # Performance metrics and charts
```

## Prerequisites

- Java 11+
- Maven 3.6+
- PostgreSQL 14+
- RabbitMQ 3.9+
- Redis 6+
- AWS EC2 instances (for deployment)

## Quick Start

### 1. Database Setup
```bash
cd database
chmod +x setup_database.sh
export DB_USER=chatflow_user
export DB_PASSWORD=ChatFlow2024!
./setup_database.sh
```

### 2. Build All Modules
```bash
mvn clean package
```

### 3. Deploy to EC2
```bash
# Upload JARs
scp -i key.pem server/target/server-1.0-SNAPSHOT.jar ec2-user@SERVER_IP:/home/ec2-user/
scp -i key.pem consumer/target/consumer-1.0-SNAPSHOT.jar ec2-user@CONSUMER_IP:/home/ec2-user/
```

### 4. Start Services

**Consumer:**
```bash
export RABBITMQ_HOST=<rabbitmq-ip>
export REDIS_HOST=<redis-ip>
export DB_HOST=<db-private-ip>
export ENABLE_PERSISTENCE=true
java -jar consumer-1.0-SNAPSHOT.jar
```

**Server (on all instances):**
```bash
export RABBITMQ_HOST=<rabbitmq-ip>
export REDIS_HOST=<redis-ip>
export DB_HOST=<db-private-ip>
java -jar server-1.0-SNAPSHOT.jar
```

### 5. Run Load Test
```bash
cd client
java -jar target/client-1.0-SNAPSHOT.jar
```

### 6. Fetch Metrics
```bash
java -cp target/client-1.0-SNAPSHOT.jar com.chatflow.FetchMetrics
```

## Performance Results

### Normal Load (500K messages)
- **Throughput:** 5,120 msg/s (client), 50,000 msg/s (consumer)
- **Latency:** p95: 0ms, p99: 1ms
- **Success Rate:** 100%
- **Runtime:** 97.65 seconds

### Stress Load (1M messages)
- **Throughput:** 5,285 msg/s (client), 100,000 msg/s (consumer)
- **Latency:** p95: 0ms, p99: 1ms, max: 356ms
- **Success Rate:** 100%
- **Runtime:** 189.21 seconds

## Configuration

### Database Writer
- Batch size: 1,000 messages
- Flush interval: 500ms
- Writer threads: 4
- Connection pool: 50 max (HikariCP)

### Consumer Pool
- 100 consumer threads (5 per room)
- Prefetch: 100 messages
- Batch ACK: every 100 messages

### Client
- Warmup: 32 threads × 1,000 messages
- Main phase: 64 threads
- Connection pooling with circuit breaker

## Key Technologies

- **WebSockets:** Java-WebSocket library
- **Message Queue:** RabbitMQ with direct exchange
- **Pub/Sub:** Redis with pattern subscriptions
- **Database:** PostgreSQL with date partitioning
- **Connection Pool:** HikariCP
- **Load Balancer:** AWS Application Load Balancer

## API Endpoints

- `ws://host:8080/chat/{roomId}` - WebSocket connection
- `http://host:8080/health` - Health check
- `http://host:8082/api/metrics` - Analytics and metrics

## Monitoring

Real-time metrics tracked:
- Messages per second
- Queue depth
- Database write performance
- Connection pool stats
- Per-room throughput
- Top users and rooms

## Author

Mohammed Noureddine  
CS6650 - Building Scalable Distributed Systems  
Northeastern University