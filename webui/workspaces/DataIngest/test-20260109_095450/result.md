

# Data Ingestion Complete
**Total Extracted Records:** 5
**Patterns Discovered:** 1

## Artifacts
- [Structured Data (JSONL)](fileIndex/G-20260109-xuik/data.jsonl)
- [Structured Data (CSV)](fileIndex/G-20260109-xuik/data.csv)
- [Search Index (CSV)](fileIndex/G-20260109-xuik/index.csv)
- [Pattern Registry (JSON)](fileIndex/G-20260109-xuik/patterns.json)

## Pattern Stats
- **[timestamp, level, logger, message]**: 5 matches
  - Regex: `^(?<timestamp>\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2})\s+(?<level>[A-Z]+)\s+(?<logger>[a-zA-Z0-9]+)\s-\s(?<message>.*)$`
