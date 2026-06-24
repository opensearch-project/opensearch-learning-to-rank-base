## Version 2.19.6 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 2.19.6

### Bug Fixes

* Fix float-comparison test flakiness with ULP precision and hybrid comparison, fix cache size integration test, fix rescore-only feature SLTR logging, and fix LoggingSearchExtBuilder.toXContent missing field name ([#355](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/355))

### Infrastructure

* Fix Windows CI build failure caused by Spotless P2 mirror timeout by removing withP2Mirrors() configuration ([#303](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/303))
* Pin GitHub Actions to commit SHAs, add ci.opensearch.org maven2 mirror, add issues:write permission to untriaged-label workflow ([#355](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/355))

### Maintenance

* Fix CVE-2026-34478 by upgrading log4j dependencies to 2.25.4 ([#330](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/330))
* Upgrade RankyMcRankFace from 0.1.1 to 0.3.0 to fix XXE/DTD vulnerability ([#355](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/355))
