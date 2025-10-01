# Apache Spark Local Example

This is a simple Java application that runs Apache Spark in **local mode** (in-process), without requiring a separate Spark cluster. This is perfect for development, testing, and learning Spark on your local machine.

## What This Example Does

The application performs a **word count** analysis on a text file using Apache Spark's distributed processing capabilities running locally. It:

1. Reads a text file
2. Splits the text into words
3. Counts the frequency of each word
4. Displays the top 20 most frequent words
5. Shows statistics about total and unique words

## Key Configuration for Local Mode

The most important part for running Spark in-process is the configuration:

```java
SparkConf conf = new SparkConf()
    .setAppName("Word Count Example")
    .setMaster("local[*]"); // This runs Spark locally with all available cores
```

The `.setMaster("local[*]")` tells Spark to:
- Run in local mode (no cluster needed)
- Use all available CPU cores on your machine
- Run everything in the same JVM process

## Prerequisites

- **Java 21** (or compatible JDK version)
- **Maven 3.6+**

### Important: Java 17+ Compatibility

Apache Spark requires access to certain internal Java modules that are restricted by default in Java 17 and newer versions. This example handles this automatically through the `MAVEN_OPTS` environment variable in the batch script.

If you're running manually, you need to set these JVM options:

```bash
# For PowerShell/CMD
$env:MAVEN_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.cs=ALL-UNNAMED --add-opens=java.base/sun.security.action=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED"
```

The `run-app.bat` script sets this automatically for you.

## Project Structure

```
spark/
├── pom.xml                          # Maven configuration with Spark dependencies
├── run-app.bat                      # Windows batch script to run the application
├── data/
│   └── sample.txt                   # Sample text file for word counting
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── spark/
                        └── WordCountApp.java  # Main application
```

## Building the Application

### Option 1: Compile Only

```bash
mvn clean compile
```

### Option 2: Build JAR Package

```bash
mvn clean package
```

This creates an uber-jar at `target/spark-local-example-1.0.0.jar` with all dependencies included.

## Running the Application

### Option 1: Using Maven Exec Plugin (Easiest)

```bash
mvn exec:java
```

This runs the application with the default sample file (`data/sample.txt`).

### Option 2: Using the Batch Script

```bash
run-app.bat
```

### Option 3: Using Maven with Custom File

```bash
mvn exec:java -Dexec.args="path/to/your/file.txt"
```

### Option 4: Running the JAR Directly

```bash
java -jar target/spark-local-example-1.0.0.jar
```

Or with a custom file:

```bash
java -jar target/spark-local-example-1.0.0.jar data/sample.txt
```

## Understanding Local Mode Options

When setting `.setMaster()`, you have several options:

- `local` - Run with 1 worker thread (no parallelism)
- `local[N]` - Run with N worker threads (e.g., `local[4]` uses 4 cores)
- `local[*]` - Run with as many worker threads as logical cores on your machine
- `local[*, M]` - Run with all cores and allow M task failures before giving up

For this example, we use `local[*]` to utilize all available cores.

## Sample Output

```
==================================================
Reading file: data/sample.txt
==================================================

==================================================
Top 20 most frequent words:
==================================================
 1. spark                 : 12
 2. data                  : 8
 3. apache                : 7
 4. processing            : 5
 5. and                   : 15
 6. the                   : 18
 7. for                   : 6
 ...

==================================================
Statistics:
==================================================
Total words: 425
Unique words: 187
==================================================
```

## How It Works (In-Process Execution)

1. **SparkConf Configuration**: Sets up Spark to run locally
2. **JavaSparkContext**: Creates the Spark context in the current JVM process
3. **RDD Operations**: All operations (map, filter, reduce) run in local threads
4. **No Network Communication**: Everything runs in-process, no cluster communication
5. **Automatic Cleanup**: Context is stopped when the application ends

## Adding Your Own Data

Simply create a text file in the `data/` directory or anywhere on your system, then run:

```bash
mvn exec:java -Dexec.args="path/to/your/data.txt"
```

## Useful Resources

- [Apache Spark Official Documentation](https://spark.apache.org/)
- [Spark Examples on GitHub](https://github.com/apache/spark/tree/master/examples/src/main/java/org/apache/spark/examples)
- [Spark Streaming Examples](https://github.com/apache/spark/tree/master/examples/src/main/java/org/apache/spark/examples/streaming)

## Troubleshooting

### Issue: Out of Memory Errors

If processing large files, increase JVM memory:

```bash
# For PowerShell
$env:MAVEN_OPTS="-Xmx4g --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED ..."
mvn exec:java
```

Or when running the JAR:

```bash
java -Xmx4g --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED -jar target/spark-local-example-1.0.0.jar
```

### Issue: IllegalAccessError with Java 17+

If you see errors like `IllegalAccessError: class org.apache.spark.storage.StorageUtils$ cannot access class sun.nio.ch.DirectBuffer`, make sure the `MAVEN_OPTS` environment variable is set with the `--add-opens` and `--add-exports` flags. The `run-app.bat` script does this automatically.

### Issue: Spark Logging Too Verbose

Create a `log4j.properties` file in `src/main/resources` with:

```properties
log4j.rootCategory=WARN, console
log4j.appender.console=org.apache.log4j.ConsoleAppender
log4j.appender.console.layout=org.apache.log4j.PatternLayout
log4j.appender.console.layout.ConversionPattern=%d{yy/MM/dd HH:mm:ss} %p %c{1}: %m%n
```

## Next Steps

Once you understand this basic example, you can:

1. Try more complex Spark operations (joins, aggregations)
2. Use Spark SQL with DataFrames
3. Process streaming data with Spark Streaming
4. Scale up to a real Spark cluster when needed

The beauty of local mode is that your code will work the same way on a cluster - you just change the `.setMaster()` configuration!e Spark

[Apache Spark](https://spark.apache.org/)

[Examples](https://github.com/apache/spark/tree/master/examples/src/main/java/org/apache/spark/examples)

[Streaming Examples](https://github.com/apache/spark/tree/master/examples/src/main/java/org/apache/spark/examples/streaming)
