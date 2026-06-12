# Ad Performance Aggregator

## Overview

This program processes an advertising CSV file, aggregates campaign performance by `campaign_id`, and generates two result files:

* `top10_ctr.csv`: top 10 campaigns with the highest CTR
* `top10_cpa.csv`: top 10 campaigns with the lowest CPA

Input columns:

```csv
campaign_id,date,impressions,clicks,spend,conversions
```

Output columns:

```csv
campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA
```

## Setup instructions

This solution is implemented in Java 11.

Requirements:

* Java 11 or later
* Docker, optional

Compile the program:



## How to run the program

Run with Java:

```bash
javac -d out src/main/java/AggregatorApp.java
```

```bash
java -cp out AggregatorApp --input ad_data.csv --output results
```

Example on Windows:

```bash
javac  javac -d out src/main/java/AggregatorApp.java
java -cp out -Xms512m -Xmx512m AggregatorApp --input C:/Users/nhant/OneDrive/Desktop/ad_data.csv/ad_data.csv --output C:/Users/nhant/OneDrive/Desktop/ad_data.csv/result```

The output files will be generated in the output directory:

```text
results/top10_ctr.csv
results/top10_cpa.csv
```

## Run with Docker

Build the Docker image:

```bash
docker build -t ad-aggregator .
```

Run on Windows PowerShell:

```bash
docker run --rm `
  -v "C:/Users/nhant/OneDrive/Desktop/ad_data.csv:/data" `
  ad-aggregator `
  --input /data/ad_data.csv `
  --output /data/results
```

Run on Linux/macOS or Git Bash:

```bash
docker run --rm \
  -v "$(pwd)":/data \
  ad-aggregator \
  --input /data/ad_data.csv \
  --output /data/results
```

The output files will be written to:

```text
results/top10_ctr.csv
results/top10_cpa.csv
```

## Libraries used

No external libraries are used.

The solution uses only Java 11 standard libraries, including:

* `BufferedReader` / `BufferedWriter`
* `Files` / `Path`
* `HashMap`
* Java Stream API
* `DateTimeFormatter`
* `DecimalFormat`



## Design decisions

### Streaming file processing

The input CSV file can be large, so the program reads it line by line using `BufferedReader` instead of loading the entire file into memory.

This keeps memory usage mainly dependent on the number of unique campaigns, not the total number of rows.

### Aggregation strategy

The program aggregates data by `campaign_id` using a `HashMap<String, CampaignStatus>`.

For each valid row, it updates the campaign totals:

* `total_impressions`
* `total_clicks`
* `total_spend`
* `total_conversions`

CTR and CPA are calculated only after aggregation because they are derived metrics.

### Spend representation

The `spend` field is stored as a fixed-point `long` in micros.

For example:

```text
12.345678 -> 12345678
```

This avoids floating-point accumulation errors from `double` while keeping the implementation lighter than using `BigDecimal` for every row.

### Date validation

The `date` column is not used for aggregation, but it is still validated because it is part of the input format.

The expected format is:

```text
yyyy-MM-dd
```

Rows with future dates are treated as invalid and skipped.

### Malformed rows

Malformed rows are skipped instead of stopping the program.

A row is considered malformed if it has:

* the wrong number of columns
* an empty `campaign_id`
* an invalid date
* negative numeric values
* an invalid `spend` value

## Performance

Tested with the provided 1GB CSV file.

Environment:

```text
OS: Windows
Java version: Java 11
Machine: Intel i5-12400F / 16GB RAM
```

Processing time:

```text
Around 11 seconds
```

Peak memory usage:

```text
Around 260 MB used heap
```

Memory was measured using:

```java
Runtime runtime = Runtime.getRuntime();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();
```

Example memory log format:

```text
[start] Used: 3.76 MB | Total allocated: 254.00 MB | Max heap: 4052.00 MB
[aggregate] Used: 260.62 MB | Total allocated: 442.00 MB | Max heap: 4052.00 MB
[end] Used: 261.62 MB | Total allocated: 442.00 MB | Max heap: 4052.00 MB
```

Note: JVM memory usage may vary between runs because garbage collection timing is not deterministic.

## Tests

The project includes tests to verify the correctness of the solution.

The tests cover:

* aggregation by `campaign_id`
* CTR calculation
* CPA calculation
* invalid row skipping
* spend parsing into micros
* date validation

Run tests:

```bash
javac -d out src/main/java/AggregatorApp.java src/test/java/AggregatorAppTest.java
java -cp out AggregatorAppTest
```
