package com.chatflow;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database configuration with HikariCP connection pooling
 * Optimized for high-throughput write operations
 */
public class DatabaseConfig {

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final int DB_PORT = Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432"));
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "chatflow");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "postgres");

    private static HikariDataSource dataSource;

    public static void initialize() {
        HikariConfig config = new HikariConfig();

        // Connection settings
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", DB_HOST, DB_PORT, DB_NAME));
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

        // Pool sizing - tune based on load
        config.setMinimumIdle(10);
        config.setMaximumPoolSize(50);

        // Connection timeout settings
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes

        // Performance optimizations
        config.setAutoCommit(false); // Manual commit for batch operations
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("ChatFlowPool");

        // PostgreSQL specific optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("reWriteBatchedInserts", "true"); // Critical for batch performance

        dataSource = new HikariDataSource(config);

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  Database Connection Pool Initialized                      ║");
        System.out.println("║  Host: " + DB_HOST + ":" + DB_PORT + "                              ║");
        System.out.println("║  Database: " + DB_NAME + "                                    ║");
        System.out.println("║  Pool Size: " + config.getMaximumPoolSize() + " connections                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource not initialized. Call initialize() first.");
        }
        return dataSource.getConnection();
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection pool closed");
        }
    }

    // Metrics for monitoring
    public static void printPoolStats() {
        if (dataSource != null) {
            System.out.println("\n" + "─".repeat(60));
            System.out.println("DATABASE POOL STATS");
            System.out.println("─".repeat(60));
            System.out.println("Active connections: " + dataSource.getHikariPoolMXBean().getActiveConnections());
            System.out.println("Idle connections: " + dataSource.getHikariPoolMXBean().getIdleConnections());
            System.out.println("Total connections: " + dataSource.getHikariPoolMXBean().getTotalConnections());
            System.out.println("Threads waiting: " + dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
            System.out.println("─".repeat(60) + "\n");
        }
    }
}