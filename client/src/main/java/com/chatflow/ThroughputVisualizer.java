package com.chatflow;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class ThroughputVisualizer {

    public static void createThroughputChart(Map<Long, Integer> throughputData,
                                             long startTime,
                                             String filename) throws IOException {

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        TreeMap<Long, Integer> sortedData = new TreeMap<>(throughputData);

        for (Map.Entry<Long, Integer> entry : sortedData.entrySet()) {
            long bucketTime = entry.getKey() * 10000; // Convert back to ms
            long elapsedSeconds = (bucketTime - startTime) / 1000;
            double messagesPerSecond = entry.getValue() / 10.0; // 10-second buckets

            dataset.addValue(messagesPerSecond, "Throughput", String.valueOf(elapsedSeconds));
        }

        JFreeChart chart = ChartFactory.createLineChart(
                "Throughput Over Time",
                "Time (seconds)",
                "Messages per Second",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        ChartUtils.saveChartAsPNG(new File(filename), chart, 1200, 600);
        System.out.println("Throughput chart saved to " + filename);
    }
}