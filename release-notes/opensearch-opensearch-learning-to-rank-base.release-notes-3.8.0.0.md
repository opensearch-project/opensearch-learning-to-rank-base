## Version 3.8.0 Release Notes

Compatible with OpenSearch and OpenSearch Dashboards version 3.8.0

### Enhancements

* Onboard code diff analyzer/reviewer and issue dedupe workflows ([#318](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/318))
* Onboard new backport-pr reusable GitHub workflow ([#372](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/372))
* Update maven2 mirror repository URL order ([#375](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/375))

### Bug Fixes

* Honor XGBoost per-node missing/default_left direction at scoring time to fix ranking divergence from offline predictions ([#379](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/379))
* Disable Mustache partial template resolution to prevent file-based partial includes in search templates ([#386](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/386))
* Fix dependency resolution during snapshot builds by scoping snapshot repository to OpenSearch packages only ([#369](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/369))

### Infrastructure

* Update opensearch-build GitHub Actions SHA references to fix security policy failures ([#343](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/343))
* Pin CI tasks to opensearch-build main branch instead of a specific commit ([#344](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/344))
* Update Codecov settings to use base branch coverage as target and add patch coverage requirements ([#339](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/339))

### Maintenance

* Upgrade RankyMcRankFace to 0.3.0 to address external entity DTD vulnerability ([#354](https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/354))
