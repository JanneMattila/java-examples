package com.example.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.util.Arrays;
import java.util.List;

/**
 * Simple Word Count application using Apache Spark in local mode.
 * This application runs Spark in-process (locally) without needing a cluster.
 */
public class WordCountApp {

    public static void main(String[] args) {
        // Configure Spark to run in local mode
        // local[*] means use all available cores on the local machine
        SparkConf conf = new SparkConf()
                .setAppName("Word Count Example")
                .setMaster("local[*]"); // Run Spark locally with all available cores

        // Create Spark context
        JavaSparkContext sc = new JavaSparkContext(conf);

        try {
            // Determine input file path
            String inputFile = args.length > 0 ? args[0] : "data/sample.txt";
            
            System.out.println("==================================================");
            System.out.println("Reading file: " + inputFile);
            System.out.println("==================================================");

            // Read the input file
            JavaRDD<String> lines = sc.textFile(inputFile);

            // Split each line into words
            JavaRDD<String> words = lines.flatMap(line -> 
                Arrays.asList(line.split("\\s+")).iterator()
            );

            // Filter out empty strings
            words = words.filter(word -> !word.isEmpty());

            // Convert to lowercase and remove punctuation
            words = words.map(word -> 
                word.toLowerCase().replaceAll("[^a-z0-9]", "")
            ).filter(word -> !word.isEmpty());

            // Map each word to (word, 1)
            JavaPairRDD<String, Integer> wordPairs = words.mapToPair(word -> 
                new Tuple2<>(word, 1)
            );

            // Reduce by key to count occurrences
            JavaPairRDD<String, Integer> wordCounts = wordPairs.reduceByKey(Integer::sum);

            // Sort by count descending
            JavaPairRDD<String, Integer> sortedWordCounts = wordCounts
                    .mapToPair(tuple -> new Tuple2<>(tuple._2, tuple._1))  // Swap to (count, word)
                    .sortByKey(false)                                       // Sort descending
                    .mapToPair(tuple -> new Tuple2<>(tuple._2, tuple._1)); // Swap back to (word, count)

            // Collect and display top 20 words
            List<Tuple2<String, Integer>> topWords = sortedWordCounts.take(20);

            System.out.println("\n==================================================");
            System.out.println("Top 20 most frequent words:");
            System.out.println("==================================================");
            
            for (int i = 0; i < topWords.size(); i++) {
                Tuple2<String, Integer> wordCount = topWords.get(i);
                System.out.printf("%2d. %-20s : %d%n", 
                    i + 1, wordCount._1, wordCount._2);
            }

            // Display statistics
            long totalWords = words.count();
            long uniqueWords = wordCounts.count();

            System.out.println("\n==================================================");
            System.out.println("Statistics:");
            System.out.println("==================================================");
            System.out.println("Total words: " + totalWords);
            System.out.println("Unique words: " + uniqueWords);
            System.out.println("==================================================\n");

        } catch (Exception e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Stop the Spark context
            sc.stop();
        }
    }
}
