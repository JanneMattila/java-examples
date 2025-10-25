#!/usr/bin/env python3
"""
Sales Data Generator

Generates realistic sales data CSV files for testing and demos.
Supports generating large datasets with "k" (thousands) and "M" (millions) suffixes.

Usage:
    python generate_sales_data.py 100k          # Generate 100,000 rows
    python generate_sales_data.py 1M            # Generate 1,000,000 rows
    python generate_sales_data.py 100M          # Generate 100,000,000 rows
    python generate_sales_data.py 50k output.csv  # Specify output file
    
Examples:
    python generate_sales_data.py 100k
    python generate_sales_data.py 10M data/large_sales.csv
"""

import csv
import random
import sys
from datetime import datetime, timedelta
from pathlib import Path

# Product data
CATEGORIES = ['Electronics', 'Clothing', 'Home & Garden', 'Books', 'Sports', 'Toys', 'Beauty', 'Food']

PRODUCTS = {
    'Electronics': ['Laptop', 'Smartphone', 'Tablet', 'Headphones', 'Smart Watch', 'Camera', 'Wireless Mouse', 'Monitor', 'Keyboard', 'Webcam'],
    'Clothing': ['T-Shirt', 'Jeans', 'Jacket', 'Sneakers', 'Dress', 'Sweater', 'Shorts', 'Coat', 'Socks', 'Hat'],
    'Home & Garden': ['Coffee Maker', 'Blender', 'Vacuum Cleaner', 'Toaster', 'Air Purifier', 'Lamp', 'Rug', 'Curtains', 'Plant Pot', 'Tool Set'],
    'Books': ['Novel', 'Cookbook', 'Science Fiction', 'Biography', 'Self-Help', 'History', 'Mystery', 'Poetry', 'Comics', 'Art Book'],
    'Sports': ['Running Shoes', 'Yoga Mat', 'Dumbbell Set', 'Tennis Racket', 'Basketball', 'Bicycle', 'Swimming Goggles', 'Gym Bag', 'Water Bottle', 'Resistance Bands'],
    'Toys': ['Board Game', 'Puzzle', 'Action Figure', 'Doll', 'Building Blocks', 'RC Car', 'Plush Toy', 'Educational Game', 'Card Game', 'Robot'],
    'Beauty': ['Moisturizer', 'Shampoo', 'Perfume', 'Lipstick', 'Face Mask', 'Nail Polish', 'Body Lotion', 'Eye Cream', 'Sunscreen', 'Hair Gel'],
    'Food': ['Organic Honey', 'Coffee Beans', 'Dark Chocolate', 'Olive Oil', 'Green Tea', 'Granola', 'Pasta', 'Spice Set', 'Nuts', 'Energy Bars']
}

PRICE_RANGES = {
    'Electronics': (99.99, 1999.99),
    'Clothing': (19.99, 299.99),
    'Home & Garden': (29.99, 399.99),
    'Books': (9.99, 49.99),
    'Sports': (29.99, 499.99),
    'Toys': (9.99, 149.99),
    'Beauty': (14.99, 199.99),
    'Food': (4.99, 79.99)
}

REGIONS = ['North America', 'Europe', 'Asia', 'South America', 'Africa', 'Oceania']
PAYMENT_METHODS = ['Credit Card', 'Debit Card', 'PayPal', 'Apple Pay', 'Google Pay']

def parse_row_count(count_str):
    """
    Parse row count from string with k/M suffix.
    
    Examples:
        "100k" -> 100000
        "1M" -> 1000000
        "50" -> 50
    """
    count_str = count_str.strip().upper()
    
    if count_str.endswith('M'):
        return int(float(count_str[:-1]) * 1_000_000)
    elif count_str.endswith('K'):
        return int(float(count_str[:-1]) * 1_000)
    else:
        return int(count_str)

def format_number(num):
    """Format number with appropriate suffix for display."""
    if num >= 1_000_000:
        return f"{num / 1_000_000:.1f}M"
    elif num >= 1_000:
        return f"{num / 1_000:.0f}k"
    else:
        return str(num)

def generate_sales_data(num_rows, output_file):
    """Generate sales data CSV file with specified number of rows."""
    
    print(f"Generating {format_number(num_rows)} rows of sales data...")
    print(f"Output file: {output_file}")
    
    start_date = datetime(2023, 1, 1)
    date_range = 639  # ~21 months of data
    
    # Create output directory if it doesn't exist
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_file, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        
        # Write header
        writer.writerow([
            'order_id', 'customer_id', 'product_category', 'product_name', 
            'quantity', 'unit_price', 'order_date', 'region', 'payment_method'
        ])
        
        # Progress reporting
        report_interval = max(1, num_rows // 20)  # Report 20 times during generation
        
        # Generate rows
        for i in range(1, num_rows + 1):
            order_id = i
            customer_id = random.randint(1000, 9999)
            category = random.choice(CATEGORIES)
            product = random.choice(PRODUCTS[category])
            quantity = random.randint(1, 5)
            unit_price = round(random.uniform(PRICE_RANGES[category][0], PRICE_RANGES[category][1]), 2)
            order_date = (start_date + timedelta(days=random.randint(0, date_range))).strftime('%Y-%m-%d')
            region = random.choice(REGIONS)
            payment_method = random.choice(PAYMENT_METHODS)
            
            writer.writerow([
                order_id, customer_id, category, product, quantity, 
                unit_price, order_date, region, payment_method
            ])
            
            # Progress reporting
            if i % report_interval == 0:
                progress = (i / num_rows) * 100
                print(f"  Progress: {format_number(i)} / {format_number(num_rows)} ({progress:.0f}%)")
    
    # File size reporting
    file_size = output_path.stat().st_size
    if file_size >= 1_000_000_000:
        size_str = f"{file_size / 1_000_000_000:.2f} GB"
    elif file_size >= 1_000_000:
        size_str = f"{file_size / 1_000_000:.2f} MB"
    elif file_size >= 1_000:
        size_str = f"{file_size / 1_000:.2f} KB"
    else:
        size_str = f"{file_size} bytes"
    
    print(f"\n✓ Successfully generated {format_number(num_rows)} rows")
    print(f"✓ File size: {size_str}")
    print(f"✓ Output: {output_file}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python generate_sales_data.py <row_count> [output_file]")
        print()
        print("Row count examples:")
        print("  100k  = 100,000 rows")
        print("  1M    = 1,000,000 rows")
        print("  10M   = 10,000,000 rows")
        print("  100M  = 100,000,000 rows")
        print()
        print("Examples:")
        print("  python generate_sales_data.py 100k")
        print("  python generate_sales_data.py 10M ../data/large_sales.csv")
        sys.exit(1)
    
    try:
        # Parse row count
        row_count_str = sys.argv[1]
        num_rows = parse_row_count(row_count_str)
        
        if num_rows <= 0:
            print(f"Error: Row count must be positive, got: {num_rows}")
            sys.exit(1)
        
        # Determine output file
        if len(sys.argv) >= 3:
            output_file = sys.argv[2]
        else:
            # Default output file based on row count
            output_file = f"../data/sales_data_{row_count_str.lower()}.csv"
        
        # Generate the data
        generate_sales_data(num_rows, output_file)
        
    except ValueError as e:
        print(f"Error parsing row count '{sys.argv[1]}': {e}")
        print("Use format like: 100k, 1M, 10M, or a plain number")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n\nGeneration interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
