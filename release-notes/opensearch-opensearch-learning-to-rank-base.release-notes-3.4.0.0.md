## Version 3.4.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.4.0

### Enhancements
* Allow warnings about directly accessing the .plugins-ml-config index ([#256](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/256))
* Feature/ltr system origin avoid warnings ([#259](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/259))

### Bug Fixes
* Use OpenSearch Version.computeID for legacy version IDs ([#264](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/264))
* Bug/ml index warning ([#269](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/269))
* Use implicit wait_for instead of explicit refresh to avoid warnings about touching system indexes ([#271](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/271))
* Fix rescore-only feature SLTR logging ([#266](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/266))

### Infrastructure
* Reduce the required coverage until we can improve it ([#258](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/258))
* Upgrade Gradle to 9.2.0 ([#263](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/263))