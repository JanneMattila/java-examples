# Running Spark Application in Azure Databricks

This guide explains how to adapt the local Spark application to run in Azure Databricks.

## Key Differences Between Local and Databricks

| Aspect | Local Mode | Azure Databricks |
|--------|------------|------------------|
| **SparkContext** | You create it | Pre-configured by platform |
| **Master URL** | `local[*]` | Managed by Databricks cluster |
| **File Paths** | Local filesystem | DBFS (`dbfs:/`) or cloud storage |
| **Dependencies** | Included in JAR | May use cluster libraries |
| **Lifecycle** | You manage start/stop | Platform manages |

## Required Code Changes

### 1. Don't Create SparkContext (Use Existing One)

**❌ Local Mode (Don't use in Databricks):**
```java
SparkConf conf = new SparkConf()
    .setAppName("Word Count Example")
    .setMaster("local[*]");
JavaSparkContext sc = new JavaSparkContext(conf);
```

**✅ Databricks (Get existing session):**
```java
SparkSession spark = SparkSession.builder().getOrCreate();
JavaSparkContext sc = JavaSparkContext.fromSparkContext(spark.sparkContext());
```

### 2. Don't Stop the SparkContext

**❌ Local Mode:**
```java
finally {
    sc.stop(); // This will crash in Databricks!
}
```

**✅ Databricks:**
```java
finally {
    // Don't stop - let Databricks manage the lifecycle
    // Or check environment first
}
```

### 3. Use DBFS File Paths

**❌ Local Mode:**
```java
String inputFile = "data/sample.txt";
```

**✅ Databricks:**
```java
String inputFile = "dbfs:/FileStore/sample.txt";
// Or from Azure Storage:
// "wasbs://container@account.blob.core.windows.net/path/file.txt"
// "abfss://container@account.dfs.core.windows.net/path/file.txt"
```

## Deployment Options

### Option 1: Use the Databricks-Compatible Java Class

`WordCountAppDatabricks.java` automatically detects the environment:

```java
boolean isDatabricks = System.getenv("DATABRICKS_RUNTIME_VERSION") != null;

if (isDatabricks) {
    // Use existing SparkSession
    SparkSession spark = SparkSession.builder().getOrCreate();
    sc = JavaSparkContext.fromSparkContext(spark.sparkContext());
} else {
    // Create local SparkContext
    SparkConf conf = new SparkConf()
        .setAppName("Word Count Example")
        .setMaster("local[*]");
    sc = new JavaSparkContext(conf);
}
```

**Build the JAR:**

```bash
mvn clean package
```

This creates: `target/spark-local-example-1.0.0.jar`

### Option 2: Use Databricks Notebook (Recommended for Development)

Upload the `WordCountDatabricks.scala` notebook to your Databricks workspace.

The notebook includes:
- ✅ Scala version
- ✅ Python version
- ✅ Sample data creation
- ✅ Result visualization
- ✅ Uses pre-configured `sc` and `spark`

### Option 3: Create a Databricks Job

**Steps:**

1. **Upload the JAR to DBFS:**
   - In Databricks UI: Go to **Data** → **Add Data** → **Upload File**
   - Upload `spark-local-example-1.0.0.jar` to `/FileStore/jars/`

2. **Upload Sample Data:**
   - Upload `data/sample.txt` to `/FileStore/sample.txt`
   - Or run a notebook cell to create it programmatically

3. **Create a Job:**
   - Go to **Workflows** → **Create Job**
   - **Task name:** WordCount
   - **Type:** JAR
   - **Main class:** `com.example.spark.WordCountAppDatabricks`
   - **Dependent libraries:** Upload your JAR
   - **Parameters:** `["dbfs:/FileStore/sample.txt"]`
   - **Cluster:** Select or create a cluster

4. **Run the Job:**
   - Click **Run now**
   - View output in the job run logs

## File Upload Methods

### Using Databricks CLI

```bash
# Install Databricks CLI
pip install databricks-cli

# Configure authentication
databricks configure --token

# Upload files
databricks fs cp spark-local-example-1.0.0.jar dbfs:/FileStore/jars/
databricks fs cp data/sample.txt dbfs:/FileStore/
```

### Using Azure Storage Explorer

1. Connect to the Databricks DBFS via Azure Storage Explorer
2. Navigate to the appropriate container
3. Upload files directly

### Using Databricks UI

1. **Workspace** → **Data** → **Upload File**
2. Drag and drop files
3. Files are stored in DBFS

## Running in Databricks Notebook

### Scala Cell:

```scala
%scala
// The SparkContext (sc) and SparkSession (spark) are already available
val inputFile = "dbfs:/FileStore/sample.txt"
val lines = sc.textFile(inputFile)

val wordCounts = lines
  .flatMap(_.split("\\s+"))
  .filter(_.nonEmpty)
  .map(_.toLowerCase.replaceAll("[^a-z0-9]", ""))
  .filter(_.nonEmpty)
  .map(word => (word, 1))
  .reduceByKey(_ + _)
  .sortBy(-_._2)

wordCounts.take(20).foreach(println)
```

### Python Cell:

```python
%python
# SparkContext (sc) and SparkSession (spark) are pre-configured
input_file = "dbfs:/FileStore/sample.txt"
lines = sc.textFile(input_file)

word_counts = (lines
    .flatMap(lambda line: line.split())
    .map(lambda word: word.lower())
    .filter(lambda word: word.isalnum())
    .map(lambda word: (word, 1))
    .reduceByKey(lambda a, b: a + b)
    .sortBy(lambda pair: -pair[1]))

for word, count in word_counts.take(20):
    print(f"{word}: {count}")
```

## Accessing Azure Storage from Databricks

### Azure Blob Storage (WASBS):
```java
String inputFile = "wasbs://container@storageaccount.blob.core.windows.net/path/to/file.txt";
```

### Azure Data Lake Storage Gen2 (ABFSS):
```java
String inputFile = "abfss://container@storageaccount.dfs.core.windows.net/path/to/file.txt";
```

**Configure access credentials in cluster configuration or notebook:**
```scala
spark.conf.set(
  "fs.azure.account.key.storageaccount.dfs.core.windows.net",
  "your-access-key")
```

## POM.xml Considerations for Databricks

The Databricks runtime includes Spark dependencies. You can mark them as `provided` to reduce JAR size:

```xml
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-core_2.13</artifactId>
    <version>${spark.version}</version>
    <scope>provided</scope>  <!-- Don't include in JAR -->
</dependency>
```

## Troubleshooting

### Issue: "SparkContext has been shutdown"
**Cause:** Trying to stop SparkContext in Databricks  
**Solution:** Remove `sc.stop()` or check environment first

### Issue: "File not found"
**Cause:** Wrong file path format  
**Solution:** Use `dbfs:/` prefix for DBFS files

### Issue: "ClassNotFoundException"
**Cause:** Dependencies not included in JAR  
**Solution:** Use maven-shade-plugin (already configured in our pom.xml)

### Issue: "Task not serializable"
**Cause:** Using non-serializable objects in closures  
**Solution:** Make classes implement `Serializable` or use static methods

## Best Practices

1. **Use Databricks Notebooks for Interactive Development**
   - Fast iteration
   - Built-in visualization
   - Easy debugging

2. **Use JAR Jobs for Production Workloads**
   - Scheduled/automated runs
   - Version control
   - CI/CD integration

3. **Use DataFrames/Datasets over RDDs**
   - Better performance (Catalyst optimizer)
   - More Pythonic/SQL-like API
   - Easier debugging

4. **Partition Your Data**
   - Use Delta Lake format
   - Optimize for query patterns
   - Enable Z-ordering for better performance

5. **Monitor with Spark UI**
   - Available in Databricks cluster page
   - Check stage execution times
   - Identify bottlenecks

## Summary

To run in Azure Databricks, you need to:

✅ Use existing `SparkSession` instead of creating `SparkContext`  
✅ Don't call `sc.stop()` - let Databricks manage lifecycle  
✅ Use DBFS paths (`dbfs:/`) or cloud storage paths  
✅ Upload JAR and data files to DBFS  
✅ Create Databricks Job or use notebook for execution  
✅ Consider marking Spark dependencies as `provided` scope

The `WordCountAppDatabricks.java` class I created handles both environments automatically!
