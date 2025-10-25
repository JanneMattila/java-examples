package com.example.databricks;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import com.databricks.connect.DatabricksSession;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.apache.spark.sql.functions.*;

/**
 * Sales Analytics Demo - Showcases DataFrame operations with CSV data
 * Designed to work with Databricks Serverless compute
 * 
 * This demo analyzes 100k sales records and demonstrates:
 * - CSV file reading with schema inference
 * - Complex DataFrame transformations
 * - Aggregations and window functions
 * - Date/time operations
 * - Multiple grouping and filtering operations
 * - Outputs results to both console and timestamped file
 */
public class SalesAnalyticsDemo {
    
    /**
     * Helper class that writes to console and optionally to Volume
     */
    private static class DualOutputWriter {
        private final StringBuilder volumeBuffer;
        private final boolean writeToVolume;
        
        public DualOutputWriter(boolean writeToVolume) throws IOException {
            this.writeToVolume = writeToVolume;
            
            if (writeToVolume) {
                // For Volume output, collect in buffer to write at end
                this.volumeBuffer = new StringBuilder();
            } else {
                // Console only
                this.volumeBuffer = null;
            }
        }
        
        public void println(String text) {
            System.out.println(text);
            if (volumeBuffer != null) {
                volumeBuffer.append(text).append("\n");
            }
        }
        
        public void printf(String format, Object... args) {
            String formatted = String.format(format, args);
            System.out.print(formatted);
            if (volumeBuffer != null) {
                volumeBuffer.append(formatted);
            }
        }
        
        public void printSchema(Dataset<Row> df) {
            System.out.println("\nDataFrame Schema:");
            df.printSchema();
            
            // Manually write schema to file/buffer
            String schemaHeader = "\nDataFrame Schema:\nroot\n";
            if (volumeBuffer != null) {
                volumeBuffer.append(schemaHeader);
            }
            
            df.schema().foreach(field -> {
                String schemaLine = String.format(" |-- %s: %s (nullable = %s)\n",
                    field.name(),
                    field.dataType().simpleString(),
                    field.nullable());
                if (volumeBuffer != null) {
                    volumeBuffer.append(schemaLine);
                }
                return null;
            });
        }
        
        public String getBuffer() {
            return volumeBuffer != null ? volumeBuffer.toString() : null;
        }
        
        public void close() {
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("Sales Analytics Demo - DataFrame Showcase");
        System.out.println("=".repeat(70));

        final boolean isDatabricks = System.getenv("DATABRICKS_RUNTIME_VERSION") != null;
        SparkSession spark = null;
        
        try {
            System.out.println(isDatabricks
                    ? "Running in Azure Databricks environment"
                    : "Running using Databricks Connect");

            spark = DatabricksSession.builder().getOrCreate();

            SalesAnalyticsDemo.runAnalytics(spark, args, isDatabricks);

        } catch (Exception e) {
            System.err.println("Error in sales analytics: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (spark != null && !isDatabricks) {
                spark.close();
            }
        }
    }
    
    /**
     * Check if the input path is a Unity Catalog Volume path
     */
    private static boolean isVolumePath(String path) {
        return path != null && path.startsWith("/Volumes/");
    }
    
    /**
     * Format duration in human-readable format
     * Examples: "2 seconds", "1 minute, 30 seconds", "2 hours, 15 minutes, 30 seconds"
     */
    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        StringBuilder result = new StringBuilder();
        
        if (hours > 0) {
            result.append(hours).append(hours == 1 ? " hour" : " hours");
        }
        
        if (minutes > 0) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }
        
        if (seconds > 0 || result.length() == 0) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(seconds).append(seconds == 1 ? " second" : " seconds");
        }
        
        return result.toString();
    }
    
    /**
     * Generate Volume output path with timestamp
     * Example: /Volumes/catalog/schema/volume/sales_data.csv -> /Volumes/catalog/schema/volume/analytics-20251025_143052
     */
    private static String generateVolumeOutputPath(String inputVolumePath) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // Extract the directory part of the volume path
        int lastSlash = inputVolumePath.lastIndexOf('/');
        String volumeDir = lastSlash > 0 ? inputVolumePath.substring(0, lastSlash) : inputVolumePath;
        
        // Extract base filename without extension
        String fileName = lastSlash > 0 ? inputVolumePath.substring(lastSlash + 1) : inputVolumePath;
        String baseName = fileName.replaceAll("\\.csv$", "");
        
        return volumeDir + "/" + baseName + "-analytics-" + timestamp;
    }

    private static void runAnalytics(SparkSession spark, String[] args, boolean isDatabricks) {
        // Determine input file path
        String inputFile;
        
        if (args.length > 0) {
            inputFile = args[0];
            System.out.println("Using input file from command-line argument");
        } else {
            String envFile = System.getenv("SALES_DATA_FILE");
            if (envFile != null && !envFile.isEmpty()) {
                inputFile = envFile;
                System.out.println("Using input file from SALES_DATA_FILE environment variable");
            } else {
                // Default paths: DBFS for Databricks, local data folder for Connect
                if (isDatabricks) {
                    inputFile = "dbfs:/FileStore/sales_data.csv";
                } else {
                    // Convert relative path to absolute for local Databricks Connect
                    inputFile = new java.io.File("data/sales_data.csv").getAbsolutePath();
                }
                System.out.println("Using default input file for environment");
            }
        }

        System.out.println("\nReading CSV file: " + inputFile);
        
        // Determine output strategy based on input path
        boolean isVolume = isVolumePath(inputFile);
        String volumeOutputPath = null;
        
        if (isVolume) {
            volumeOutputPath = generateVolumeOutputPath(inputFile);
            System.out.println("Will write results to Volume: " + volumeOutputPath);
        }
        System.out.println("-".repeat(70));
        
        DualOutputWriter out = null;
        
        try {
            out = new DualOutputWriter(isVolume);
            LocalDateTime started = LocalDateTime.now();
            out.println("=".repeat(70));
            out.println("Sales Analytics Demo - DataFrame Showcase");
            out.println("=".repeat(70));
            out.println("Input file: " + inputFile);
            out.println("Started: " + started.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            out.println("-".repeat(70));

            // Read CSV with options
            Dataset<Row> salesDF = spark.read()
                    .option("header", "true")
                    .option("inferSchema", "true")
                    .csv(inputFile);

            // Display schema
            out.printSchema(salesDF);

            // Basic statistics
            long totalRecords = salesDF.count();
            out.println("\n" + "=".repeat(70));
            out.println("DATASET OVERVIEW");
            out.println("=".repeat(70));
            out.println("Total records: " + String.format("%,d", totalRecords));

            // 1. Revenue Analysis by Category
            out.println("\n" + "=".repeat(70));
            out.println("1. TOTAL REVENUE BY PRODUCT CATEGORY");
            out.println("=".repeat(70));
            
            Dataset<Row> revenueByCategory = salesDF
                    .withColumn("revenue", col("quantity").multiply(col("unit_price")))
                    .groupBy("product_category")
                    .agg(
                        sum("revenue").alias("total_revenue"),
                        count("order_id").alias("num_orders"),
                        avg("revenue").alias("avg_order_value")
                    )
                    .orderBy(desc("total_revenue"));

            List<Row> topCategories = revenueByCategory.limit(10).collectAsList();
            out.printf("%-20s %15s %12s %18s%n", 
                "Category", "Total Revenue", "# Orders", "Avg Order Value");
            out.println("-".repeat(70));
            
            for (Row row : topCategories) {
                out.printf("%-20s $%,14.2f %,11d $%,16.2f%n",
                    row.getString(0),
                    row.getDouble(1),
                    row.getLong(2),
                    row.getDouble(3));
            }

            // 2. Regional Performance
            out.println("\n" + "=".repeat(70));
            out.println("2. SALES PERFORMANCE BY REGION");
            out.println("=".repeat(70));
            
            Dataset<Row> regionalSales = salesDF
                    .withColumn("revenue", col("quantity").multiply(col("unit_price")))
                    .groupBy("region")
                    .agg(
                        sum("revenue").alias("total_revenue"),
                        count("order_id").alias("num_orders"),
                        countDistinct("customer_id").alias("unique_customers")
                    )
                    .orderBy(desc("total_revenue"));

            List<Row> regions = regionalSales.collectAsList();
            out.printf("%-20s %15s %12s %18s%n", 
                "Region", "Total Revenue", "# Orders", "Unique Customers");
            out.println("-".repeat(70));
            
            for (Row row : regions) {
                out.printf("%-20s $%,14.2f %,11d %,17d%n",
                    row.getString(0),
                    row.getDouble(1),
                    row.getLong(2),
                    row.getLong(3));
            }

            // 3. Top Products
            out.println("\n" + "=".repeat(70));
            out.println("3. TOP 10 BEST-SELLING PRODUCTS");
            out.println("=".repeat(70));
            
            Dataset<Row> topProducts = salesDF
                    .withColumn("revenue", col("quantity").multiply(col("unit_price")))
                    .groupBy("product_name", "product_category")
                    .agg(
                        sum("revenue").alias("total_revenue"),
                        sum("quantity").alias("units_sold")
                    )
                    .orderBy(desc("total_revenue"))
                    .limit(10);

            List<Row> products = topProducts.collectAsList();
            out.printf("%-25s %-20s %15s %12s%n", 
                "Product", "Category", "Revenue", "Units Sold");
            out.println("-".repeat(70));
            
            for (Row row : products) {
                out.printf("%-25s %-20s $%,13.2f %,11d%n",
                    row.getString(0),
                    row.getString(1),
                    row.getDouble(2),
                    row.getLong(3));
            }

            // 4. Payment Method Analysis
            out.println("\n" + "=".repeat(70));
            out.println("4. PAYMENT METHOD DISTRIBUTION");
            out.println("=".repeat(70));
            
            Dataset<Row> paymentAnalysis = salesDF
                    .withColumn("revenue", col("quantity").multiply(col("unit_price")))
                    .groupBy("payment_method")
                    .agg(
                        count("order_id").alias("num_transactions"),
                        sum("revenue").alias("total_revenue"),
                        avg("revenue").alias("avg_transaction_value")
                    )
                    .orderBy(desc("num_transactions"));

            List<Row> payments = paymentAnalysis.collectAsList();
            out.printf("%-20s %15s %15s %20s%n", 
                "Payment Method", "# Transactions", "Total Revenue", "Avg Transaction");
            out.println("-".repeat(70));
            
            for (Row row : payments) {
                out.printf("%-20s %,14d $%,14.2f $%,18.2f%n",
                    row.getString(0),
                    row.getLong(1),
                    row.getDouble(2),
                    row.getDouble(3));
            }

            // 5. Monthly Revenue Trend (last 12 months of data)
            out.println("\n" + "=".repeat(70));
            out.println("5. MONTHLY REVENUE TREND");
            out.println("=".repeat(70));
            
            Dataset<Row> monthlyTrend = salesDF
                    .withColumn("revenue", col("quantity").multiply(col("unit_price")))
                    .withColumn("year_month", date_format(col("order_date"), "yyyy-MM"))
                    .groupBy("year_month")
                    .agg(
                        sum("revenue").alias("monthly_revenue"),
                        count("order_id").alias("num_orders")
                    )
                    .orderBy(desc("year_month"))
                    .limit(12);

            List<Row> months = monthlyTrend.collectAsList();
            out.printf("%-15s %20s %15s%n", 
                "Month", "Revenue", "# Orders");
            out.println("-".repeat(70));
            
            for (Row row : months) {
                out.printf("%-15s $%,18.2f %,14d%n",
                    row.getString(0),
                    row.getDouble(1),
                    row.getLong(2));
            }

            // 6. Customer Segmentation - Top Spending Customers
            out.println("\n" + "=".repeat(70));
            out.println("6. TOP 15 CUSTOMERS BY TOTAL SPENDING");
            out.println("=".repeat(70));
            
            Dataset<Row> topCustomers = salesDF
                    .withColumn("revenue", col("quantity").multiply(col("unit_price")))
                    .groupBy("customer_id")
                    .agg(
                        sum("revenue").alias("total_spent"),
                        count("order_id").alias("num_orders"),
                        avg("revenue").alias("avg_order_value")
                    )
                    .orderBy(desc("total_spent"))
                    .limit(15);

            List<Row> customers = topCustomers.collectAsList();
            out.printf("%-15s %20s %15s %20s%n", 
                "Customer ID", "Total Spent", "# Orders", "Avg Order Value");
            out.println("-".repeat(70));
            
            for (Row row : customers) {
                out.printf("%-15d $%,18.2f %,14d $%,18.2f%n",
                    row.getInt(0),
                    row.getDouble(1),
                    row.getLong(2),
                    row.getDouble(3));
            }

            // Summary Statistics
            out.println("\n" + "=".repeat(70));
            out.println("SUMMARY STATISTICS");
            out.println("=".repeat(70));
            
            Dataset<Row> overallStats = salesDF
                    .withColumn("revenue", col("quantity").multiply(col("unit_price")))
                    .agg(
                        sum("revenue").alias("total_revenue"),
                        avg("revenue").alias("avg_revenue"),
                        min("revenue").alias("min_revenue"),
                        max("revenue").alias("max_revenue"),
                        countDistinct("customer_id").alias("unique_customers")
                    );

            Row stats = overallStats.first();
            out.printf("Total Revenue:        $%,18.2f%n", stats.getDouble(0));
            out.printf("Average Order Value:  $%,18.2f%n", stats.getDouble(1));
            out.printf("Minimum Order Value:  $%,18.2f%n", stats.getDouble(2));
            out.printf("Maximum Order Value:  $%,18.2f%n", stats.getDouble(3));
            out.printf("Unique Customers:     %,19d%n", stats.getLong(4));
            
            out.println("\n" + "=".repeat(70));
            out.println("Analytics Complete!");
            LocalDateTime finished = LocalDateTime.now();
            out.println("Started: " + started.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            out.println("Finished: " + finished.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            Duration duration = Duration.between(started, finished);
            out.println("Duration: " + formatDuration(duration));
            out.println("=".repeat(70) + "\n");
            
            // Write to Volume if needed
            if (isVolume && volumeOutputPath != null) {
                String outputContent = out.getBuffer();
                if (outputContent != null) {
                    // Create a single-row DataFrame with the output text
                    Dataset<Row> outputDF = spark.createDataFrame(
                        java.util.Collections.singletonList(outputContent),
                        String.class
                    ).toDF("value");
                    
                    // Write to Volume as single text file
                    outputDF.coalesce(1)
                        .write()
                        .mode("overwrite")
                        .text(volumeOutputPath);
                    
                    System.out.println("\n✓ Results saved to Volume: " + volumeOutputPath);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error writing output file: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
