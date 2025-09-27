## Version 3.3.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.3.0

### Features
* Query-phase feature logging with fetch-phase render. Feature values are computed during the query phase (respecting dfs_query_then_fetch global stats) and stored per-document; fetch only renders these cached values without rescoring or creating new Weights. Falls back to bridging DFS-aware Weights when caches are unavailable.

### Fixes
* Feature logging now uses DFS/global statistics under `dfs_query_then_fetch`, ensuring consistent logged feature values across shards. Global term and collection stats are captured at weight construction and reused during fetch logging.
* TermStatSupplier.entrySet() returned incorrect lists for non-`df` keys; fixed to return the correct underlying statistics for `idf`, `tf`, `ttf`, and `tp`.
* TermStatQuery:
  * Implemented `extractTerms` to add all query terms to the output set.
  * Implemented `toString(String)` to aid debugging/logging.

### Tests
* Added unit tests to verify TermStatQuery `extractTerms` behavior and `toString` output.
* Added unit tests to verify TermStatSupplier `entrySet` mappings align with `get()` for all supported stat keys.

### Maintenance
* General test suite improvements; all tests passing under Gradle 8.14 and JDK 21.
