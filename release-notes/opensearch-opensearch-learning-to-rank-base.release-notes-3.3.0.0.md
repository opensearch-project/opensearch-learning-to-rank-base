## Version 3.3.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.3.0

### Features
* Query-phase feature logging with fetch-phase render. Feature values are computed during the query phase (respecting dfs_query_then_fetch global stats) and stored per-document; fetch only renders these cached values without rescoring or creating new Weights. Falls back to bridging DFS-aware Weights when caches are unavailable ([#229](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/229))

### Bug Fixes
* Fix bad inclusion of log4j in this jar when bundled ([#226](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/226))
* Update System.env syntax for Gradle 9 compatibility ([#219](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/219))
* Feature logging now uses DFS/global statistics under `dfs_query_then_fetch` for consistent logged feature values across shards ([#229](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/229))
* TermStatSupplier.entrySet() returned incorrect lists for non-`df` keys; fixed to return the correct underlying statistics for `idf`, `tf`, `ttf`, and `tp`  ([#229](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/229))

### Infrastructure
* Adding code coverage report generation ([#228](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/228))
* Switching to a hybrid method of comparing floats in our assertions ([#221](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/221))

### Maintenance
* Bump SLF4J to 2.0.17 ([#224](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/224))
* Upgrade spotless plugin and address build deprecations ([#222](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/222))
* [AUTO] Increment version to 3.3.0-SNAPSHOT ([#217](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/217))
