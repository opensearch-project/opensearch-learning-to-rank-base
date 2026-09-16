## Version 3.9.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.9.0

### Enhancements

* Add model-level `missing_as_zero` flag for XGBoost ranker ([#389](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/389))
* Make `ltr.caches.max_mem` a dynamic setting and scale its default with heap size ([#397](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/397))

### Maintenance

* Bump to 3.9.0-SNAPSHOT and fix `JsonStringEncoder` import for Jackson 3 compatibility ([#411](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/411))
* Move jngz-es to emeritus maintainer status ([#408](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/408))
