## Version 3.6.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.6.0

### Bug Fixes

* Fix `LoggingSearchExtBuilder.toXContent` missing field name, which caused a `JsonGenerationException` when LTR feature logging was used with search pipelines that re-serialize the request ([#290](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/290))

### Infrastructure

* Fix Windows CI build failure by removing Spotless P2 mirror dependency and resolving from Maven Central instead ([#305](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/305))
