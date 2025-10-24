# Databricks Connect Word Count Example

This Java application demonstrates running Apache Spark workloads both **locally via Databricks Connect** and **inside Azure Databricks clusters**. The same code works in both environments, making it perfect for development, testing, and production deployment.

## What This Example Does

The application performs a **word count** analysis on a text file using Apache Spark's DataFrame API. It:

1. Reads a text file (from DBFS, Unity Catalog Volumes, or cloud storage)
2. Splits text into words using DataFrame transformations
3. Normalizes words (lowercase, removes punctuation)
4. Counts word frequencies using groupBy aggregations
5. Displays the top 20 most frequent words
6. Shows statistics about total and unique words

## Key Features

- **Modern DataFrame API**: Uses Spark SQL DataFrames instead of legacy RDDs
- **Environment Detection**: Automatically adapts to Databricks or Connect mode
- **Flexible Input**: Accepts file paths via command-line, environment variable, or defaults
- **Session Management**: Properly handles SparkSession lifecycle in both environments

## Runtime Modes

| Aspect | Databricks Connect (Local) | Azure Databricks Cluster |
|--------|---------------------------|--------------------------|
| **Execution** | Runs on your local JVM | Runs inside Databricks workspace |
| **SparkSession** | Created with `DatabricksSession.builder()` | Pre-configured by runtime |
| **Compute** | Proxies to remote cluster/warehouse | Managed by platform |
| **File Access** | DBFS/cloud paths resolved remotely | Direct DBFS/storage access |
| **Authentication** | CLI profile or env variables | Workspace managed |
| **Session Lifecycle** | You call `.stop()` | Platform manages |

The application automatically detects which mode it's running in by checking the `DATABRICKS_RUNTIME_VERSION` environment variable.

## Prerequisites

- **Java 21** (or compatible JDK version)
- **Maven 3.6+**
- **Databricks CLI** (for Connect mode)
- **Active Azure Databricks workspace**

## Project Structure

```
databricks/
├── pom.xml                                    # Maven config with Databricks Connect
├── run-databricks-connect.bat               # Windows script for Connect mode
├── .env                                      # Environment variable examples
├── README.md                                 # This file
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── databricks/
                        └── WordCountAppDatabricks.java
```

## Authentication and Configuration

### Setting Up Databricks Connect

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

3. **Configure environment variables**:
   ```powershell
   $env:DATABRICKS_CONFIG_PROFILE = "databricks-connect"
   $env:DATABRICKS_CLUSTER_ID = "1234-123456-abcd1234"
   $env:DATABRICKS_WAREHOUSE_ID = "1234567890abcdef"
   ```

### How Authentication Works

When you run the application with Databricks Connect:

1. **Profile Resolution**: The SDK reads `DATABRICKS_CONFIG_PROFILE` to locate credentials in `~/.databrickscfg`
2. **Credential Loading**: Loads workspace URL and token from the profile
3. **Compute Selection**: Uses `DATABRICKS_CLUSTER_ID` or `DATABRICKS_WAREHOUSE_ID` to determine target compute
4. **Session Creation**: `DatabricksSession.builder().getOrCreate()` establishes a remote connection
5. **Command Forwarding**: All DataFrame operations are serialized and sent to the remote compute via gRPC

The application never processes data locally—your machine acts as a client sending Spark commands to the remote cluster.

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
   - Databricks cluster: `dbfs:/FileStore/sample.txt`
   - Databricks Connect: `/Volumes/demo/customer1/sample.txt`

## Building the Application

```bash
mvn clean package
```

This creates `target/databricks-connect-example-1.0.2.jar` with all dependencies bundled.

## Running with Databricks Connect

### Option 1: Using the Batch Script (Easiest)

```bash
.\run-app.bat
```

The script:
- Sets Java and Maven options for Spark compatibility
- Applies the `databricks-connect` profile by default
- Runs via Maven exec plugin

### Option 2: Using Maven Directly

```bash
mvn exec:java -Dexec.mainClass=com.example.databricks.WordCountAppDatabricks
```

With custom file:
```bash
mvn exec:java -Dexec.mainClass=com.example.databricks.WordCountAppDatabricks -Dexec.args="dbfs:/mydata.txt"
```

## Running Inside Azure Databricks

### Step 1: Upload the JAR

Using Databricks CLI:
```bash
databricks fs cp target/databricks-connect-example-1.0.2.jar dbfs:/FileStore/jars/
```

Or via Databricks UI: **Workspace → Data → Upload File**

### Step 2: Upload Sample Data (if needed)

```bash
databricks fs cp data/sample.txt dbfs:/FileStore/sample.txt
```

### Step 3: Create a Databricks Job

1. Navigate to **Workflows → Create Job**
2. Configure the task:
   - **Task name**: WordCount
   - **Type**: JAR
   - **Main class**: `com.example.databricks.WordCountAppDatabricks`
   - **Dependent JAR**: `dbfs:/FileStore/jars/databricks-connect-example-1.0.2.jar`
   - **Parameters**: `["dbfs:/FileStore/sample.txt"]`
   - **Cluster**: Select existing or create new
3. **Run now** and view logs

### Alternative: Run in a Notebook

```scala
%scala
import com.example.databricks.WordCountAppDatabricks

// The class will detect it's running in Databricks
WordCountAppDatabricks.main(Array("dbfs:/FileStore/sample.txt"))
```

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

## Understanding the Code

### Session Creation

```java
final boolean isDatabricks = System.getenv("DATABRICKS_RUNTIME_VERSION") != null;
SparkSession spark = DatabricksSession.builder().getOrCreate();
```

`DatabricksSession.builder()`:
- In Connect mode: creates a client session proxying to remote compute
- In Databricks: returns the existing workspace session

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
        spark.stop();  // Only stop in Connect mode
    }
}
```

**Critical**: Never call `.stop()` when running inside Databricks—the platform manages the session lifecycle.

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

1. **Use SQL Warehouses for Development**: Faster startup than clusters, auto-scaling, better cost control
2. **Use Job Clusters for Production**: Optimized for batch workloads, terminate after completion
3. **Leverage Unity Catalog Volumes**: Better governance and permissions than raw DBFS
4. **Cache Strategically**: The sample caches intermediate DataFrames—`.unpersist()` when done
5. **Monitor in Spark UI**: Available in Databricks workspace under cluster details
6. **Version Control Your JARs**: Tag releases and track which version runs in production

## Environment Variables Reference

| Variable | Purpose | Example |
|----------|---------|---------|
| `DATABRICKS_CONFIG_PROFILE` | Profile name in `~/.databrickscfg` | `databricks-connect` |
| `DATABRICKS_CLUSTER_ID` | All-purpose or job cluster ID | `1023-084458-l70zdchj` |
| `DATABRICKS_WAREHOUSE_ID` | SQL warehouse ID | `2a20ab81c2a7bf93` |
| `INPUT_FILE` | Override default input file path | `dbfs:/my-data.txt` |
| `DATABRICKS_RUNTIME_VERSION` | Set by Databricks (read-only) | `14.3` |

## Next Steps

- **Scale Up**: Process larger datasets using cluster autoscaling
- **Add Streaming**: Use Structured Streaming for real-time data
- **Integrate Delta Lake**: Write results to Delta tables for ACID transactions
- **Build Pipelines**: Use Databricks Workflows for orchestration
- **Add Testing**: Unit test DataFrame logic with local Spark sessions

## Useful Resources

- [Databricks Connect Documentation](https://docs.databricks.com/dev-tools/databricks-connect.html)
- [Apache Spark DataFrame Guide](https://spark.apache.org/docs/latest/sql-programming-guide.html)
- [Databricks CLI Reference](https://docs.databricks.com/dev-tools/cli/index.html)
- [Unity Catalog Volumes](https://docs.databricks.com/data-governance/unity-catalog/volumes.html)

## Summary

This example demonstrates a production-ready pattern for Spark applications:

✅ Single codebase runs locally and in Databricks  
✅ Modern DataFrame API with Catalyst optimization  
✅ Flexible input configuration (args, env vars, defaults)  
✅ Proper session lifecycle management  
✅ Authentication via Databricks CLI profiles  
✅ Support for clusters and SQL warehouses  

The `WordCountAppDatabricks.java` class handles all environment differences automatically!
