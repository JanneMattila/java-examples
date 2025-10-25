# Sales Analytics Demo

This demo showcases advanced DataFrame operations with a dataset of 100,000 sales records.

## Features

- **CSV Data Processing** - Reads and processes large CSV files
- **Complex Aggregations** - Revenue analysis by category, region, and time period
- **Window Functions** - Customer segmentation and ranking
- **Date/Time Operations** - Monthly trend analysis
- **Multiple Grouping** - Multi-dimensional analytics
- **Serverless Compatible** - Works with Databricks Serverless compute

## Demo Analytics

The demo performs the following analyses on 100k sales records:

1. **Revenue by Product Category** - Total revenue, order count, and average order value per category
2. **Regional Performance** - Sales metrics across different geographic regions
3. **Top Products** - Best-selling products by revenue
4. **Payment Method Distribution** - Transaction analysis by payment type
5. **Monthly Revenue Trend** - Time-series revenue analysis
6. **Customer Segmentation** - Top spending customers and their behavior patterns

## Running the Demo

### Local Execution (with local CSV file)

The demo cannot read local files when connecting to Databricks. You must upload the data to DBFS or a Volume first.

### Azure Databricks Execution

1. **Upload the CSV file** to DBFS or a Volume:
   ```bash
   # Using Databricks CLI
   databricks fs cp data/sales_data.csv dbfs:/FileStore/sales_data.csv
   
   # Or upload via Databricks UI to a Volume
   ```

2. **Run via Databricks Connect**:
   ```bash
   # Set environment variable if not using default paths
   set SALES_DATA_FILE=dbfs:/FileStore/sales_data.csv
   run-sales-demo.bat
   ```

3. **Run in Databricks Workspace**:
   - Upload `SalesAnalyticsDemo.java` and the uber JAR
   - Create a Databricks job with the JAR
   - Pass the file path as an argument: `dbfs:/FileStore/sales_data.csv`

### Command Line Arguments

```bash
# Use custom file path
run-sales-demo.bat dbfs:/FileStore/my_sales_data.csv

# Or use environment variable
set SALES_DATA_FILE=dbfs:/FileStore/sales_data.csv
run-sales-demo.bat
```

## Dataset

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

## Technical Requirements

- Java 21
- Maven 3.9+
- Databricks Connect 17.2.1
- Databricks Runtime 17.2 (for serverless compatibility)

## Key Differences from Word Count Demo

1. **DataFrame vs RDD** - Uses DataFrame API instead of RDD transformations
2. **Structured Data** - Works with structured CSV data instead of text
3. **Complex Aggregations** - Multiple groupBy and aggregation operations
4. **No Caching** - Serverless-compatible (no `.cache()` operations)
5. **Business Analytics** - Real-world business intelligence scenarios

## Example Output

```
======================================================================
Sales Analytics Demo - DataFrame Showcase
======================================================================

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
```

## Notes

- The demo is optimized for Databricks Serverless compute
- All caching operations have been removed for serverless compatibility
- The CSV file must be accessible via DBFS or Unity Catalog Volumes when using Databricks Connect

