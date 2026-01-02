

# Data Ingestion Complete
**Total Extracted Records:** 5
**Patterns Discovered:** 1

## Artifacts
- [Structured Data (JSONL)](fileIndex/G-20260101-F9sC/data.jsonl)
- [Structured Data (CSV)](fileIndex/G-20260101-F9sC/data.csv)
- [Search Index (CSV)](fileIndex/G-20260101-F9sC/index.csv)
- [Pattern Registry (JSON)](fileIndex/G-20260101-F9sC/patterns.json)

## Pattern Stats
- **[timestamp, level, logger, message]**: 5 matches
  - Regex: `^(?<timestamp>\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})\s+(?<level>[A-Z]+)\s+(?<logger>[^\s-]+)\s-\s(?<message>.*)$`
