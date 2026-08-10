---
transforms: (.*)analysis.json -> $1packages.json
folder: ../../..
related:
  - ./analysis_schema.ts
---

Roll up the per-file analysis findings below into package-level summaries.
Group the `files` entries by the package/folder each file belongs to
(typically the file's immediate parent directory, or a coarser grouping
where that is more meaningful), and produce one `PackageAnalysis` entry per
package.

For each package, capture:

- the `files` (paths) that contributed to the rollup
- a `summary` of the shared/recurring findings across the package
- package-wide `findings` (issues not specific to a single file, e.g.
  inconsistent patterns across files)
- an optional `overall_severity` for the package

Output a single JSON document that strictly conforms to the
`PackageAnalysisPlan` schema defined in `analysis_schema.ts` (see related
file). Do not include any commentary, markdown formatting, or explanation
outside of the JSON structure itself — the output file must be valid,
parseable JSON.