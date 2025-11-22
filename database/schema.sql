-- ChatFlow Database Schema for PostgreSQL

-- ============================================
-- Main Messages Table (Partitioned by day)
-- ============================================
CREATE TABLE IF NOT EXISTS messages (
                                        message_id VARCHAR(36),
    room_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    username VARCHAR(20) NOT NULL,
    message_text VARCHAR(500) NOT NULL,
    message_type VARCHAR(10) NOT NULL,
    client_timestamp TIMESTAMPTZ NOT NULL,
    server_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, created_at)
    ) PARTITION BY RANGE (created_at);

-- Create partitions for current month (extend as needed)
CREATE TABLE IF NOT EXISTS messages_2024_11 PARTITION OF messages
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');

CREATE TABLE IF NOT EXISTS messages_2024_12 PARTITION OF messages
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

CREATE TABLE IF NOT EXISTS messages_2025_01 PARTITION OF messages
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE IF NOT EXISTS messages_2025_11 PARTITION OF messages
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE IF NOT EXISTS messages_2025_12 PARTITION OF messages
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');
-- ============================================
-- Indexes for Core Queries
-- ============================================

-- Query 1: Get messages for room in time range
CREATE INDEX IF NOT EXISTS idx_messages_room_time
    ON messages (room_id, created_at DESC);

-- Query 2: Get user's message history
CREATE INDEX IF NOT EXISTS idx_messages_user_time
    ON messages (user_id, created_at DESC);

-- Query 3: Count active users (covered by user_id index)
CREATE INDEX IF NOT EXISTS idx_messages_time_user
    ON messages (created_at, user_id);

-- Query 4: Rooms user participated in
CREATE INDEX IF NOT EXISTS idx_messages_user_room
    ON messages (user_id, room_id, created_at DESC);

-- ============================================
-- User Activity Summary Table
-- ============================================
CREATE TABLE IF NOT EXISTS user_activity (
                                             user_id INTEGER PRIMARY KEY,
                                             username VARCHAR(20) NOT NULL,
    total_messages INTEGER DEFAULT 0,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rooms_participated JSONB DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_user_activity_messages
    ON user_activity (total_messages DESC);

-- ============================================
-- Room Activity Summary Table
-- ============================================
CREATE TABLE IF NOT EXISTS room_activity (
                                             room_id INTEGER PRIMARY KEY,
                                             total_messages INTEGER DEFAULT 0,
                                             unique_users INTEGER DEFAULT 0,
                                             first_message TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_room_activity_messages
    ON room_activity (total_messages DESC);

-- ============================================
-- Materialized View for Analytics
-- ============================================

-- Messages per minute aggregation
CREATE MATERIALIZED VIEW IF NOT EXISTS messages_per_minute AS
SELECT
    date_trunc('minute', created_at) as minute,
    COUNT(*) as message_count,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT room_id) as active_rooms
FROM messages
GROUP BY date_trunc('minute', created_at)
ORDER BY minute DESC;

CREATE INDEX IF NOT EXISTS idx_messages_per_minute_time
    ON messages_per_minute (minute DESC);

-- ============================================
-- Functions for Upsert Operations
-- ============================================

-- Update user activity (called after batch insert)
CREATE OR REPLACE FUNCTION update_user_activity()
RETURNS TRIGGER AS $$
BEGIN
INSERT INTO user_activity (user_id, username, total_messages, first_seen, last_seen, rooms_participated)
VALUES (
           NEW.user_id,
           NEW.username,
           1,
           NEW.created_at,
           NEW.created_at,
           jsonb_build_array(NEW.room_id)
       )
    ON CONFLICT (user_id) DO UPDATE SET
    total_messages = user_activity.total_messages + 1,
                                 last_seen = GREATEST(user_activity.last_seen, NEW.created_at),
                                 rooms_participated = (
                                 SELECT jsonb_agg(DISTINCT elem)
                                 FROM jsonb_array_elements(user_activity.rooms_participated || jsonb_build_array(NEW.room_id)) elem
                                 ),
                                 updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Update room activity
CREATE OR REPLACE FUNCTION update_room_activity()
RETURNS TRIGGER AS $$
BEGIN
INSERT INTO room_activity (room_id, total_messages, unique_users, first_message, last_message)
SELECT
    NEW.room_id,
    1,
    1,
    NEW.created_at,
    NEW.created_at
    ON CONFLICT (room_id) DO UPDATE SET
    total_messages = room_activity.total_messages + 1,
                                 last_message = GREATEST(room_activity.last_message, NEW.created_at),
                                 updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;