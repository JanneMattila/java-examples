# Data Generation Scripts

This directory contains scripts for generating test datasets for the Databricks examples.

## Sales Data Generator

The `generate_sales_data.py` script generates realistic sales data CSV files for testing and demonstrations.

### Features

- **Flexible row counts** with k/M suffixes (e.g., 100k, 1M, 100M)
- **Realistic data** including:
  - 8 product categories with 10 products each
  - 6 geographic regions
  - 5 payment methods
  - Date range spanning ~21 months
  - Appropriate price ranges per category
- **Progress reporting** for large datasets
- **Configurable output** file location

### Usage

```bash
# Basic usage (generates to ../data/)
python generate_sales_data.py 100k          # 100,000 rows
python generate_sales_data.py 1M            # 1,000,000 rows
python generate_sales_data.py 10M           # 10,000,000 rows

# Specify custom output file
python generate_sales_data.py 100k output.csv
python generate_sales_data.py 50M ../data/huge_sales.csv
```

### Row Count Format

The script supports flexible row count notation:

| Format | Rows | Description |
|--------|------|-------------|
| `100` | 100 | Plain number |
| `100k` | 100,000 | k = thousands |
| `1M` | 1,000,000 | M = millions |
| `10M` | 10,000,000 | 10 million |
| `100M` | 100,000,000 | 100 million |
| `1.5M` | 1,500,000 | Decimals supported |

### Examples

```bash
# Generate 100k rows (default location: ../data/sales_data_100k.csv)
python generate_sales_data.py 100k

# Generate 1M rows to specific file
python generate_sales_data.py 1M ../data/large_sales.csv

# Generate 100M rows (takes several minutes)
python generate_sales_data.py 100M ../data/huge_sales.csv
```

### Output

The script generates CSV files with the following columns:

- `order_id` - Unique order identifier (sequential)
- `customer_id` - Customer identifier (random 1000-9999)
- `product_category` - Product category (Electronics, Clothing, etc.)
- `product_name` - Specific product name
- `quantity` - Order quantity (1-5)
- `unit_price` - Price per unit (varies by category)
- `order_date` - Order date (2023-01-01 to 2024-09-24)
- `region` - Geographic region
- `payment_method` - Payment method used

### Performance

Approximate generation times on modern hardware:

| Rows | Time | File Size |
|------|------|-----------|
| 100k | ~2 seconds | ~6.8 MB |
| 1M | ~20 seconds | ~68 MB |
| 10M | ~3 minutes | ~680 MB |
| 100M | ~30 minutes | ~6.8 GB |

### Requirements

- Python 3.6+
- No external dependencies (uses only standard library)

### Data Categories

**Product Categories (8):**
- Electronics (Laptop, Smartphone, Tablet, etc.)
- Clothing (T-Shirt, Jeans, Jacket, etc.)
- Home & Garden (Coffee Maker, Blender, etc.)
- Books (Novel, Cookbook, Science Fiction, etc.)
- Sports (Running Shoes, Yoga Mat, etc.)
- Toys (Board Game, Puzzle, Action Figure, etc.)
- Beauty (Moisturizer, Shampoo, Perfume, etc.)
- Food (Organic Honey, Coffee Beans, etc.)

**Regions (6):**
- North America
- Europe
- Asia
- South America
- Africa
- Oceania

**Payment Methods (5):**
- Credit Card
- Debit Card
- PayPal
- Apple Pay
- Google Pay

### Notes

- The script creates output directories automatically if they don't exist
- Progress is reported every ~5% for large datasets
- File size and final row count are displayed upon completion
- Use Ctrl+C to interrupt generation if needed
