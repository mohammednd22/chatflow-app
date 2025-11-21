package com.chatflow;

/**
 * Standalone utility to fetch and display database metrics from server
 * Run after load test completes
 */
public class FetchMetrics {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           ChatFlow Database Metrics Fetcher                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        MetricsFetcher.fetchAndPrintMetrics();

        System.out.println("\nDone! Screenshot this output for your report.");
    }
}