package com.example.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import scala.Tuple2;

import java.util.Arrays;
import java.util.List;

/**
 * Word Count application that works in both local mode and Azure Databricks.
 * 
 * For Azure Databricks:
 * - Upload JAR to DBFS (e.g., /FileStore/jars/)
 * - Create a job or run in notebook using spark-submit
 * - Pass DBFS file path as argument (e.g., dbfs:/FileStore/sample.txt)
 */
public class WordCountAppDatabricks {

    public static void main(String[] args) {
        JavaSparkContext sc = null;
        
        try {
            // Check if we're running in Databricks or locally
            boolean isDatabricks = System.getenv("DATABRICKS_RUNTIME_VERSION") != null;
            
            if (isDatabricks) {
                // In Databricks, get the existing SparkSession
                System.out.println("Running in Azure Databricks environment");
                SparkSession spark = SparkSession.builder().getOrCreate();
                sc = JavaSparkContext.fromSparkContext(spark.sparkContext());
            } else {
                // Local mode - create our own SparkContext
                System.out.println("Running in local mode");
                SparkConf conf = new SparkConf()
                        .setAppName("Word Count Example")
                        .setMaster("local[*]");
                sc = new JavaSparkContext(conf);
            }

            // Determine input file path
            String inputFile;
            if (args.length > 0) {
                inputFile = args[0];
            } else {
                // Default paths based on environment
                inputFile = isDatabricks ? "dbfs:/FileStore/sample.txt" : "data/sample.txt";
            }
            
            System.out.println("==================================================");
            System.out.println("Reading file: " + inputFile);
            System.out.println("Environment: " + (isDatabricks ? "Azure Databricks" : "Local"));
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
            // Only stop the context if we're running locally
            // In Databricks, the platform manages the lifecycle
            boolean isDatabricks = System.getenv("DATABRICKS_RUNTIME_VERSION") != null;
            if (sc != null && !isDatabricks) {
                sc.stop();
            }
        }
    }
}
