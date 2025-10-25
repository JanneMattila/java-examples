# Databricks Connect Examples

This repository contains Java applications demonstrating Apache Spark workloads via **[Databricks Connect](https://learn.microsoft.com/en-us/azure/databricks/dev-tools/databricks-connect/)**. The applications execute locally while leveraging remote Databricks compute resources, making them perfect for development, testing, and production deployment.

## Examples Overview

1. **[Word Count Demo](#word-count-demo)** - Classic text analysis using DataFrame API
2. **[Sales Analytics Demo](#sales-analytics-demo)** - Advanced business analytics with complex aggregations

## Table of Contents

- [Key Features](#key-features)
- [Databricks Connect Overview](#databricks-connect-overview)
- [Prerequisites](#prerequisites)
- [Authentication and Configuration](#authentication-and-configuration)
- [Project Structure](#project-structure)
- [Word Count Demo](#word-count-demo)
- [Sales Analytics Demo](#sales-analytics-demo)
- [Building the Application](#building-the-application)
- [Understanding the Code](#understanding-the-code)
- [POM Configuration](#pom-configuration-for-databricks-connect)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)
- [Resources](#useful-resources)

## Key Features

- **Modern DataFrame API**: Uses Spark SQL DataFrames instead of legacy RDDs
- **Databricks Connect**: Executes locally while using remote Databricks compute
- **Flexible Input**: Accepts file paths via command-line, environment variable, or defaults
- **Session Management**: Properly handles SparkSession lifecycle
- **Multiple Demos**: From simple text analysis to complex business analytics
- **Volume Output**: Automatically writes results to Unity Catalog Volumes (Sales Demo)
- **Duration Tracking**: Human-readable execution time reporting

## Databricks Connect Overview

**Databricks Connect** allows you to run Spark applications from your local development environment while leveraging Databricks compute resources:

| Aspect | How It Works |
|--------|--------------|
| **Execution** | Runs on your local JVM |
| **SparkSession** | Created with `DatabricksSession.builder()` |
| **Compute** | Connects to remote Databricks cluster or SQL warehouse |
| **File Access** | DBFS/cloud paths resolved on Databricks side |
| **Authentication** | CLI profile, service principal, managed identity, or PAT |
| **Session Lifecycle** | You manage with `.close()` |

The application automatically detects it's running via Databricks Connect and manages the session appropriately.

## Prerequisites

- **Java 21** (or compatible JDK version)
- **Maven 3.6+**
- **Active Azure Databricks workspace**
- **Databricks CLI** (optional - only for CLI-based authentication)

## Project Structure

```
databricks/
├── pom.xml                                    # Maven config with Databricks Connect
├── run-app.bat                                # Windows script for Word Count demo
├── run-sales-demo.bat                         # Windows script for Sales Analytics demo
├── .env                                       # Environment variable examples
├── README.md                                  # This file
├── data/
│   ├── sample.txt                             # Sample text file (Word Count)
│   └── sales_data.csv                         # Sample sales data (100k records)
├── scripts/
│   ├── generate_sales_data.py                 # Python script to generate larger datasets
│   └── README.md                              # Data generator documentation
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── databricks/
                        ├── WordCountAppDatabricks.java    # Word Count demo
                        └── SalesAnalyticsDemo.java        # Sales Analytics demo
```

---

# Word Count Demo

Classic text analysis demonstrating fundamental DataFrame operations with Apache Spark.

## What This Demo Does

The application performs a **word count** analysis on a text file using Apache Spark's DataFrame API. It:

1. Reads a text file (from DBFS, Unity Catalog Volumes, or cloud storage)
2. Splits text into words using DataFrame transformations
3. Normalizes words (lowercase, removes punctuation)
4. Counts word frequencies using groupBy aggregations
5. Displays the top 20 most frequent words
6. Shows statistics about total and unique words

## Running the Word Count Demo

### Option 1: Using the Batch Script (Easiest)

```bash
.\run-app.bat
```

The script:
- Sets Java and Maven options for Spark compatibility
- Runs via Maven exec plugin with Databricks Connect

### Option 2: Using Maven Directly

```bash
mvn exec:java -Dexec.mainClass=com.example.databricks.WordCountAppDatabricks
```

With custom file:
```bash
mvn exec:java -Dexec.mainClass=com.example.databricks.WordCountAppDatabricks -Dexec.args="dbfs:/mydata.txt"
```

## Word Count Input Files

The application supports three ways to specify the input file (in priority order):

1. **Command-line argument**: 
   ```bash
   mvn exec:java -Dexec.args="dbfs:/path/to/file.txt"
   ```

2. **Environment variable**:
   ```powershell
   $env:INPUT_FILE = "/Volumes/demo/customer1/sample.txt"
   mvn exec:java
   ```

3. **Default paths**:
   - Databricks Connect: `/Volumes/demo/customer1/sample.txt`

## Word Count Sample Output

```
Starting WordCountAppDatabricks...
Running using Databricks Connect
Using input file from command-line argument
==================================================
Reading file: /Volumes/demo/customer1/sample.txt
Environment: Connect
==================================================

==================================================
Top 20 most frequent words:
==================================================
 1. data                  : 127
 2. spark                 : 89
 3. processing            : 56
 4. analytics             : 42
 5. distributed           : 38
...

==================================================
Statistics:
==================================================
Total words: 2847
Unique words: 623
==================================================
```

---

# Sales Analytics Demo

Advanced DataFrame showcase with 100,000+ sales records demonstrating complex business analytics operations.

## What This Demo Does

The Sales Analytics demo performs comprehensive business intelligence analysis on sales data:

1. **Revenue by Product Category** - Total revenue, order count, and average order value per category
2. **Regional Performance** - Sales metrics across different geographic regions
3. **Top Products** - Best-selling products by revenue
4. **Payment Method Distribution** - Transaction analysis by payment type
5. **Monthly Revenue Trend** - Time-series revenue analysis
6. **Customer Segmentation** - Top spending customers and their behavior patterns

## Sales Analytics Features

- **CSV Data Processing** - Reads and processes large CSV files
- **Complex Aggregations** - Multi-dimensional revenue analysis
- **Window Functions** - Customer segmentation and ranking
- **Date/Time Operations** - Monthly trend analysis
- **Multiple Grouping** - Cross-sectional analytics
- **Serverless Compatible** - Works with Databricks Serverless compute
- **Volume Output** - Automatically writes results to Unity Catalog Volumes when input is from a Volume
- **Duration Tracking** - Human-readable execution time reporting

## Sales Demo Dataset

The demo includes a generated dataset (`data/sales_data.csv`) with 100,000 records containing:

- Order ID and Customer ID
- Product category and name
- Quantity and unit price
- Order date
- Geographic region
- Payment method

### Dataset Statistics:
- **Size**: ~6.8 MB
- **Records**: 100,000 orders
- **Categories**: Electronics, Clothing, Home & Garden, Books, Sports, Toys, Beauty, Food
- **Regions**: North America, Europe, Asia, South America, Africa, Oceania
- **Date Range**: January 2023 - September 2024

## Generating Larger Datasets

Use the included Python script to generate datasets of any size:

```bash
# Generate 1 million records
python scripts/generate_sales_data.py 1M sales_data_1m.csv

# Generate 50 million records
python scripts/generate_sales_data.py 50M sales_data_50m.csv

# Generate 100k records (supports k for thousands, M for millions)
python scripts/generate_sales_data.py 100k sales_data_100k.csv
```

See `scripts/README.md` for detailed documentation and performance benchmarks.

## Uploading Data to Databricks

Upload CSV files to DBFS or Unity Catalog Volumes using Databricks CLI:

```bash
# Upload to DBFS
databricks fs cp data/sales_data.csv dbfs:/FileStore/sales_data.csv

# Or upload to Unity Catalog Volume
databricks fs cp data/sales_data.csv dbfs:/Volumes/my_catalog/my_schema/my_volume/sales_data.csv

# Upload large files (50M+ records)
databricks fs cp sales_data_50m.csv dbfs:/Volumes/my_catalog/my_schema/my_volume/sales_data_50m.csv
```

## Running the Sales Analytics Demo

### Option 1: Using the Batch Script (Easiest)

```bash
# Using environment variable (recommended)
$env:SALES_DATA_FILE = "/Volumes/my_catalog/my_schema/my_volume/sales_data_50m.csv"
.\run-sales-demo.bat

# Or pass file path as argument
.\run-sales-demo.bat "/Volumes/my_catalog/my_schema/my_volume/sales_data.csv"

# Using DBFS path
$env:SALES_DATA_FILE = "dbfs:/FileStore/sales_data.csv"
.\run-sales-demo.bat
```

### Option 2: Using Maven Directly

```bash
mvn exec:java -Dexec.mainClass=com.example.databricks.SalesAnalyticsDemo -Dexec.args="/Volumes/my_catalog/my_schema/my_volume/sales_data.csv"
```

## Sales Analytics Output

The demo provides two types of output:

### Console Output (Always)
All analytics results are displayed in the console with formatted tables and statistics.

### Volume Output (Automatic)
When input is from a Unity Catalog Volume (`/Volumes/...`), results are automatically saved to a timestamped file in the same directory:

- **Input**: `/Volumes/my_catalog/my_schema/my_volume/sales_data_50m.csv`
- **Output**: `/Volumes/my_catalog/my_schema/my_volume/sales_data_50m-analytics-20251025_181520.txt`

**Note**: DBFS inputs only display console output (no file output).

## Sales Analytics Sample Output

```
======================================================================
Sales Analytics Demo - DataFrame Showcase
======================================================================
Input file: /Volumes/my_catalog/my_schema/my_volume/sales_data_50m.csv
Started: 2025-10-25 18:45:00
----------------------------------------------------------------------

1. TOTAL REVENUE BY PRODUCT CATEGORY
----------------------------------------------------------------------
Category             Total Revenue    # Orders     Avg Order Value
----------------------------------------------------------------------
Electronics          $  12,345,678.90      15,234 $          810.42
Clothing             $   8,234,567.12      23,456 $          351.02
...

2. SALES PERFORMANCE BY REGION
----------------------------------------------------------------------
Region               Total Revenue    # Orders     Unique Customers
----------------------------------------------------------------------
North America        $  15,678,901.23      25,432              8,234
Europe               $  12,345,678.90      21,345              7,123
...

======================================================================
Analytics Complete!
Started: 2025-10-25 18:45:00
Finished: 2025-10-25 18:47:35
Duration: 2 minutes, 35 seconds
======================================================================

✓ Results saved to Volume: /Volumes/my_catalog/my_schema/my_volume/sales_data_50m-analytics-20251025_184735.txt
```

## Key Differences Between Demos

| Feature | Word Count | Sales Analytics |
|---------|------------|-----------------|
| **Data Type** | Unstructured text | Structured CSV |
| **API Style** | Basic DataFrame ops | Complex aggregations |
| **Operations** | Split, filter, group | Window functions, multi-group, time-series |
| **Output** | Console only | Console + Volume file |
| **Caching** | Not used (serverless) | Not used (serverless) |
| **Use Case** | Text analysis | Business intelligence |
| **Duration Tracking** | Basic | Human-readable format |

---

## Authentication and Configuration

The application supports multiple authentication methods, automatically selected in this priority order:

### Method 1: Azure Managed Identity (Recommended for Production)

**No CLI required!** When running on Azure resources (VMs, App Services, Azure Functions, Container Instances), the SDK automatically uses Managed Identity:

```powershell
# Set workspace and compute
$env:DATABRICKS_HOST = "https://adb-1234567890123456.12.azuredatabricks.net"
$env:DATABRICKS_WAREHOUSE_ID = "abc123def456"

# Run - Managed Identity is used automatically
mvn exec:java
```

**How it works**:
1. Application detects it's running in Azure (via Azure Instance Metadata Service)
2. SDK requests an access token from the Azure Managed Identity endpoint
3. Token is used to authenticate with Databricks workspace
4. No secrets or credentials stored in code or configuration files

**Setup requirements**:
- Enable System-assigned or User-assigned Managed Identity on your Azure resource
- Grant the identity access to your Databricks workspace:
  - Navigate to Databricks workspace → Settings → Identity and Access
  - Add the managed identity with appropriate permissions

### Method 2: Service Principal (for CI/CD)

Use Azure Service Principal credentials:

```powershell
$env:DATABRICKS_HOST = "https://adb-1234567890123456.12.azuredatabricks.net"
$env:DATABRICKS_WAREHOUSE_ID = "abc123def456"
$env:ARM_CLIENT_ID = "<service-principal-client-id>"
$env:ARM_CLIENT_SECRET = "<service-principal-secret>"
$env:ARM_TENANT_ID = "<azure-tenant-id>"

mvn exec:java
```

### Method 3: Personal Access Token

Use a Databricks personal access token:

```powershell
$env:DATABRICKS_HOST = "https://adb-1234567890123456.12.azuredatabricks.net"
$env:DATABRICKS_TOKEN = "<your-personal-access-token>"
$env:DATABRICKS_WAREHOUSE_ID = "abc123def456"

mvn exec:java
```

### Method 4: Databricks CLI Profile (for Local Development)

Use the Databricks CLI to store credentials:

1. **Install Databricks CLI**:
   ```powershell
   winget install Databricks.DatabricksCLI
   ```

2. **Authenticate with your workspace**:
   ```powershell
   databricks auth login --profile databricks-connect
   ```
   You'll be prompted for:
   - **Workspace URL**: `https://adb-<workspace-id>.<region>.azuredatabricks.net`
   - **Personal Access Token**: Generate from User Settings → Access Tokens

3. **Configure environment variables**:
   ```powershell
   $env:DATABRICKS_CONFIG_PROFILE = "databricks-connect"
   $env:DATABRICKS_CLUSTER_ID = "0123-456789-abcdefgh"
   $env:DATABRICKS_WAREHOUSE_ID = "abc123def456"
   ```

### Authentication Priority

The SDK tries authentication methods in this order:

1. `DATABRICKS_TOKEN` (explicit token)
2. **Azure Managed Identity** (automatic in Azure, no configuration needed)
3. Service Principal (`ARM_CLIENT_ID`, `ARM_CLIENT_SECRET`, `ARM_TENANT_ID`)
4. Azure CLI credentials (`az login`)
5. Databricks CLI profile (`DATABRICKS_CONFIG_PROFILE`)

**The code automatically detects and uses the first available method**, making it seamless to move from local development (CLI) to production (Managed Identity).

### Compute Selection

Choose your execution target:

```powershell
# Option 1: All-purpose or job cluster
$env:DATABRICKS_CLUSTER_ID = "0123-456789-abcdefgh"

# Option 2: SQL Warehouse (recommended for DataFrame workloads)
$env:DATABRICKS_WAREHOUSE_ID = "abc123def456"
```

## Specifying Input Files

The application supports three ways to specify the input file (in priority order):

1. **Command-line argument**: 
   ```bash
   mvn exec:java -Dexec.args="dbfs:/path/to/file.txt"
   ```

2. **Environment variable**:
   ```powershell
   $env:INPUT_FILE = "/Volumes/demo/customer1/sample.txt"
   mvn exec:java
   ```

3. **Default paths**:
   - Databricks Connect: `/Volumes/demo/customer1/sample.txt`

## Building the Application

```bash
mvn clean package
```

This creates `target/databricks-connect-example-1.0.2.jar` with all dependencies bundled.

---

## Accessing Cloud Storage

### Azure Blob Storage (WASBS)
```java
String inputFile = "wasbs://container@account.blob.core.windows.net/path/file.txt";
```

### Azure Data Lake Gen2 (ABFSS)
```java
String inputFile = "abfss://container@account.dfs.core.windows.net/path/file.txt";
```

### Unity Catalog Volumes
```java
String inputFile = "/Volumes/catalog/schema/volume/file.txt";
```

Configure storage credentials via:
- Cluster configuration (Spark config)
- Unity Catalog external locations
- Service principals with secrets

---

## Understanding the Code

### Session Creation

```java
SparkSession spark = DatabricksSession.builder().getOrCreate();
```

`DatabricksSession.builder()` creates a client session that connects to remote Databricks compute (cluster or SQL warehouse).

### DataFrame Operations

```java
Dataset<Row> lines = spark.read().text(inputFile);

Dataset<Row> normalizedWords = lines
    .select(explode(split(col("value"), "\\s+")).alias("raw_word"))
    .select(lower(regexp_replace(col("raw_word"), "[^a-z0-9]", "")).alias("word"))
    .filter(length(col("word")).gt(0))
    .cache();

Dataset<Row> wordCounts = normalizedWords
    .groupBy("word")
    .count()
    .orderBy(desc("count"), col("word"));
```

All operations are:
- **Lazy**: no execution until an action (`.collect()`, `.count()`) is called
- **Optimized**: Spark's Catalyst optimizer generates efficient query plans
- **Distributed**: executed in parallel across cluster nodes

### Lifecycle Management

```java
finally {
    if (spark != null && !isDatabricks) {
        spark.close();  // Close session when done
    }
}
```

The application checks if it's running in Databricks workspace (where session is managed by the platform) or via Databricks Connect (where you manage the lifecycle).

---

## POM Configuration for Databricks Connect

Key dependencies:

```xml
<!-- Spark dependencies marked as provided -->
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-sql_2.12</artifactId>
    <version>3.5.0</version>
    <scope>provided</scope>
</dependency>

<!-- Databricks Connect bundles Spark runtime -->
<dependency>
    <groupId>com.databricks</groupId>
    <artifactId>databricks-connect</artifactId>
    <version>16.4.6</version>
</dependency>

<!-- Jackson for SDK compatibility -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

**Important**: Use Scala 2.12 artifacts and mark Spark as `provided` to avoid version conflicts.

---

## Troubleshooting

### Issue: "Cluster serverless does not exist"
**Cause**: Invalid cluster ID  
**Solution**: Use an actual cluster ID or warehouse ID from your workspace

### Issue: "NoClassDefFoundError: scala.collection.GenMap"
**Cause**: Scala 2.13 vs 2.12 mismatch  
**Solution**: Ensure `scala.binary.version=2.12` in pom.xml

### Issue: "Authentication errors"
**Cause**: Invalid or expired credentials  
**Solution**: Rerun `databricks auth login --profile databricks-connect`

### Issue: "File not found"
**Cause**: Incorrect path or missing permissions  
**Solution**: 
- Verify DBFS paths use `dbfs:/` prefix
- Check Unity Catalog permissions for Volume paths
- Ensure cluster has storage access configured

### Issue: "SparkSession.Builder.client method not found"
**Cause**: Mixing incompatible Spark and Databricks Connect versions  
**Solution**: Mark Spark dependencies as `provided` scope

## Sample Output

```
Starting WordCountAppDatabricks...
Running using Databricks Connect
Using input file from command-line argument
==================================================
Reading file: /Volumes/demo/customer1/sample.txt
Environment: Connect
==================================================

==================================================
Top 20 most frequent words:
==================================================
 1. data                  : 127
 2. spark                 : 89
 3. processing            : 56
 4. analytics             : 42
 5. distributed           : 38
...

==================================================
Statistics:
==================================================
Total words: 2847
Unique words: 623
==================================================
```

## Best Practices

1. **Use SQL Warehouses for Development**: Faster startup, auto-scaling, better cost control
2. **Leverage Unity Catalog Volumes**: Better governance and permissions than raw DBFS
3. **Cache Strategically**: Cache intermediate DataFrames, `.unpersist()` when done
4. **Monitor in Spark UI**: Available in Databricks workspace under compute details
5. **Environment Detection**: Use `DATABRICKS_RUNTIME_VERSION` to detect if running in workspace

## Environment Variables Reference

| Variable | Purpose | Example |
|----------|---------|---------|
| `DATABRICKS_CONFIG_PROFILE` | Profile name in `~/.databrickscfg` | `databricks-connect` |
| `DATABRICKS_CLUSTER_ID` | All-purpose or job cluster ID | `0123-456789-abcdefgh` |
| `DATABRICKS_WAREHOUSE_ID` | SQL warehouse ID | `abc123def456` |
| `DATABRICKS_RUNTIME_VERSION` | Set by Databricks (read-only) | `17.2` |
| `INPUT_FILE` | Input file for Word Count demo | `dbfs:/my-data.txt` |
| `SALES_DATA_FILE` | Input file for Sales Analytics demo | `/Volumes/my_catalog/my_schema/my_volume/sales_data.csv` |

---

## Next Steps

- **Scale Up**: Process larger datasets using cluster autoscaling or serverless compute
- **Add Streaming**: Use Structured Streaming for real-time data
- **Integrate Delta Lake**: Write results to Delta tables for ACID transactions
- **Add Testing**: Unit test DataFrame logic with local Spark sessions
- **Generate Large Datasets**: Use `scripts/generate_sales_data.py` for performance testing

---

## Useful Resources
- **Integrate Delta Lake**: Write results to Delta tables for ACID transactions
- **Add Testing**: Unit test DataFrame logic with local Spark sessions
- **Explore Sales Demo**: Check out `SALES_DEMO_README.md` for advanced DataFrame operations

## Useful Resources

- [Databricks Connect Documentation](https://docs.databricks.com/dev-tools/databricks-connect.html)
- [Apache Spark DataFrame Guide](https://spark.apache.org/docs/latest/sql-programming-guide.html)
- [Databricks CLI Reference](https://docs.databricks.com/dev-tools/cli/index.html)
- [Unity Catalog Volumes](https://docs.databricks.com/data-governance/unity-catalog/volumes.html)

---

## Summary

This example demonstrates a production-ready pattern for Spark applications with Databricks Connect:

✅ Runs locally while leveraging remote Databricks compute  
✅ Modern DataFrame API with Catalyst optimization  
✅ Flexible input configuration (args, env vars, defaults)  
✅ Proper session lifecycle management  
✅ Multiple authentication methods (Managed Identity, Service Principal, PAT, CLI)  
✅ Support for clusters and SQL warehouses  
✅ Two comprehensive demos: text analysis and business analytics  
✅ Automatic Volume output for large-scale analytics  
✅ Human-readable duration tracking  

The demos provide complete examples from simple text processing to complex business intelligence analytics!
