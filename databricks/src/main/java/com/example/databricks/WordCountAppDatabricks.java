package com.example.databricks;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import com.databricks.connect.DatabricksSession;

import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.desc;
import static org.apache.spark.sql.functions.explode;
import static org.apache.spark.sql.functions.length;
import static org.apache.spark.sql.functions.lower;
import static org.apache.spark.sql.functions.regexp_replace;
import static org.apache.spark.sql.functions.split;

/**
 * Word Count application that works in both Connect mode and Azure Databricks.
 */
public class WordCountAppDatabricks {

    public static void main(String[] args) {

        System.out.println("Starting WordCountAppDatabricks...");

        final boolean isDatabricks = System.getenv("DATABRICKS_RUNTIME_VERSION") != null;
        SparkSession spark = null;
        
        try {
            System.out.println(isDatabricks
                    ? "Running in Azure Databricks environment"
                    : "Running using Databricks Connect");

            spark = DatabricksSession.builder().getOrCreate();

            WordCountAppDatabricks.runWordCount(spark, args, isDatabricks);

        } catch (Exception e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (spark != null && !isDatabricks) {
                spark.close();
            }
        }
    }

    private static void runWordCount(SparkSession spark, String[] args, boolean isDatabricks) {
        // Determine input file path from command-line argument, environment variable, or default
        String inputFile;
        
        if (args.length > 0) {
            // Priority 1: Command-line argument
            inputFile = args[0];
            System.out.println("Using input file from command-line argument");
        } else {
            // Priority 2: Environment variable (can be set in Databricks job parameters)
            String envFile = System.getenv("INPUT_FILE");
            if (envFile != null && !envFile.isEmpty()) {
                inputFile = envFile;
                System.out.println("Using input file from INPUT_FILE environment variable");
            } else {
                // Priority 3: Default paths based on environment
                inputFile = isDatabricks ? "dbfs:/FileStore/sample.txt" : "/Volumes/demo/customer1/sample.txt";
                System.out.println("Using default input file for environment");
            }
        }

        System.out.println("==================================================");
        System.out.println("Reading file: " + inputFile);
        System.out.println("Environment: " + (isDatabricks ? "Azure Databricks" : "Connect"));
        System.out.println("==================================================");

        Dataset<Row> lines = spark.read().text(inputFile);

        Dataset<Row> normalizedWords = lines
                .select(explode(split(col("value"), "\\s+")).alias("raw_word"))
                .select(lower(regexp_replace(col("raw_word"), "[^a-z0-9]", "")).alias("word"))
                .filter(length(col("word")).gt(0));

        Dataset<Row> wordCounts = normalizedWords
                .groupBy("word")
                .count();

        List<Row> topWords = wordCounts
                .orderBy(desc("count"), col("word"))
                .limit(20)
                .collectAsList();

        System.out.println("\n==================================================");
        System.out.println("Top 20 most frequent words:");
        System.out.println("==================================================");

        for (int i = 0; i < topWords.size(); i++) {
            Row row = topWords.get(i);
            System.out.printf("%2d. %-20s : %d%n", i + 1, row.getString(0), row.getLong(1));
        }

        long totalWords = normalizedWords.count();
        long uniqueWords = wordCounts.count();

        System.out.println("\n==================================================");
        System.out.println("Statistics:");
        System.out.println("==================================================");
        System.out.println("Total words: " + totalWords);
        System.out.println("Unique words: " + uniqueWords);
        System.out.println("==================================================\n");
    }
}
