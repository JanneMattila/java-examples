# Sales Analytics Demo

This demo showcases advanced DataFrame operations with a dataset of 100,000 sales records using Databricks Connect.

## Features

- **CSV Data Processing** - Reads and processes large CSV files
- **Complex Aggregations** - Revenue analysis by category, region, and time period
- **Window Functions** - Customer segmentation and ranking
- **Date/Time Operations** - Monthly trend analysis
- **Multiple Grouping** - Multi-dimensional analytics
- **Serverless Compatible** - Works with Databricks Serverless compute via Databricks Connect
- **Volume Output** - Automatically writes results to Unity Catalog Volumes when input is from a Volume

## Demo Analytics

The demo performs the following analyses on 100k sales records:

1. **Revenue by Product Category** - Total revenue, order count, and average order value per category
2. **Regional Performance** - Sales metrics across different geographic regions
3. **Top Products** - Best-selling products by revenue
4. **Payment Method Distribution** - Transaction analysis by payment type
5. **Monthly Revenue Trend** - Time-series revenue analysis
6. **Customer Segmentation** - Top spending customers and their behavior patterns

## Running the Demo

### Prerequisites

- Databricks workspace configured with Databricks Connect
- CSV data uploaded to DBFS or Unity Catalog Volume
- Environment variables configured (see main README.md for authentication setup)

### Upload Data to Databricks

Upload the CSV file to DBFS or a Volume using Databricks CLI:

```bash
# Upload to DBFS
databricks fs cp data/sales_data.csv dbfs:/FileStore/sales_data.csv

# Or upload to Unity Catalog Volume
databricks fs cp data/sales_data.csv dbfs:/Volumes/my_catalog/my_schema/my_volume/sales_data.csv
```

### Run via Databricks Connect

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

### Output Files

The demo automatically writes results based on the input location:

- **Volume Input**: Results written to same Volume with timestamped output
  - Example: `sales_data_50m-analytics-20251025_181520`
  - Location: Same directory as input file
  
- **DBFS Input**: Results displayed in console only

**Console output is always displayed** regardless of input source.

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
- Databricks workspace with serverless or standard compute
- Access to Unity Catalog Volumes or DBFS

## Key Differences from Word Count Demo

1. **DataFrame vs RDD** - Uses DataFrame API instead of RDD transformations
2. **Structured Data** - Works with structured CSV data instead of text
3. **Complex Aggregations** - Multiple groupBy and aggregation operations
4. **No Caching** - Serverless-compatible (no `.cache()` operations)
5. **Business Analytics** - Real-world business intelligence scenarios
6. **Dual Output** - Console display plus Volume file output (when using Volume input)
7. **Duration Tracking** - Human-readable execution time reporting

## Example Output

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

## Notes

- The demo is optimized for Databricks Serverless compute via Databricks Connect
- All caching operations have been removed for serverless compatibility
- CSV files must be accessible via DBFS or Unity Catalog Volumes
- Results are automatically saved to Volumes when input is from a Volume path
- Execution time is displayed in human-readable format (hours, minutes, seconds)

