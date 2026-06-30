## Version 2.19.6 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 2.19.6

### Enhancements

* Update maven2 mirror repository URL order to improve build reliability ([#376](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/376))

### Bug Fixes

* Fix float comparison flakiness with ULP precision and hybrid comparison, cache size integration test fix, rescore-only feature SLTR logging fix, and LoggingSearchExtBuilder.toXContent missing field name fix ([#355](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/355))

### Infrastructure

* Fix Windows CI build failure caused by Spotless P2 mirror timeout by removing withP2Mirrors() configuration ([#303](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/303))
* Pin GitHub Actions to commit SHAs and add ci.opensearch.org maven2 mirror to avoid Maven Central throttling ([#355](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/355))

### Maintenance

* Fix CVE-2026-34478 by upgrading log4j dependencies to 2.25.4 ([#330](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/330))
* Add release notes for 2.19.6 ([#370](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/370))
* Upgrade RankyMcRankFace 0.1.1 to 0.3.0 to fix XXE/DTD vulnerability and add issues:write permission to untriaged-label workflow ([#355](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/355))
