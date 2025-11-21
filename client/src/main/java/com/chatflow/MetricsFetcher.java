package com.chatflow;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetches and displays metrics from server after test completion
 */
public class MetricsFetcher {

    private static final String METRICS_URL = "http://34.211.84.91:8082/api/metrics";

    public static void fetchAndPrintMetrics() {
        System.out.println("\n" + "═".repeat(80));
        System.out.println("FETCHING DATABASE METRICS FROM SERVER");
        System.out.println("═".repeat(80));

        try {
            URL url = new URL(METRICS_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = in.readLine()) != null) {
                    response.append(line).append("\n");
                }
                in.close();

                System.out.println("\nMETRICS API RESPONSE:");
                System.out.println("─".repeat(80));
                System.out.println(response.toString());
                System.out.println("─".repeat(80));

            } else {
                System.err.println("Failed to fetch metrics. HTTP " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("Error fetching metrics: " + e.getMessage());
            System.err.println("Make sure servers are running with database access");
        }

        System.out.println("═".repeat(80));
    }
}