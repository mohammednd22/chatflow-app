#!/bin/bash

# ChatFlow Database Setup Script
# Sets up PostgreSQL database with optimized configuration

set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║         ChatFlow Database Setup - PostgreSQL               ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Configuration
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-chatflow}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"

echo "Database Configuration:"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"
echo ""

# Check if PostgreSQL is running
echo "1. Checking PostgreSQL connection..."
if ! pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" > /dev/null 2>&1; then
    echo "❌ PostgreSQL is not running or not accessible"
    echo "   Please start PostgreSQL and try again"
    exit 1
fi
echo "✓ PostgreSQL is running"
echo ""

# Create database if it doesn't exist
echo "2. Creating database '$DB_NAME' if not exists..."
PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 || \
PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -c "CREATE DATABASE $DB_NAME"
echo "✓ Database ready"
echo ""

# Run schema creation
echo "3. Creating tables and indexes..."
PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f schema.sql
echo "✓ Schema created"
echo ""

# Verify setup
echo "4. Verifying setup..."
TABLE_COUNT=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'")
echo "   Tables created: $TABLE_COUNT"

INDEX_COUNT=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public'")
echo "   Indexes created: $INDEX_COUNT"
echo ""

# Performance recommendations
echo "5. Performance Recommendations:"
echo ""
echo "   Add to postgresql.conf for better write performance:"
echo "   ┌──────────────────────────────────────────────────────┐"
echo "   │ shared_buffers = 4GB                                 │"
echo "   │ effective_cache_size = 12GB                          │"
echo "   │ maintenance_work_mem = 1GB                           │"
echo "   │ checkpoint_completion_target = 0.9                   │"
echo "   │ wal_buffers = 16MB                                   │"
echo "   │ default_statistics_target = 100                      │"
echo "   │ random_page_cost = 1.1                               │"
echo "   │ effective_io_concurrency = 200                       │"
echo "   │ work_mem = 256MB                                     │"
echo "   │ min_wal_size = 2GB                                   │"
echo "   │ max_wal_size = 8GB                                   │"
echo "   │ max_worker_processes = 8                             │"
echo "   │ max_parallel_workers_per_gather = 4                  │"
echo "   │ max_parallel_workers = 8                             │"
echo "   │ max_parallel_maintenance_workers = 4                 │"
echo "   └──────────────────────────────────────────────────────┘"
echo ""

echo "╔════════════════════════════════════════════════════════════╗"
echo "║              Database Setup Complete!                      ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "Connection string:"
echo "  jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME"
echo ""
echo "Next steps:"
echo "  1. Set environment variables in your application"
echo "  2. Run consumer with database persistence enabled"
echo "  3. Monitor write performance with: ./monitor_db.sh"