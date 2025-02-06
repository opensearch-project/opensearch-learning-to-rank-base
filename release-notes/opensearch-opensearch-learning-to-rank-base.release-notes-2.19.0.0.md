## Version 2.19.0.0 Release Notes

Compatible with OpenSearch 2.19.0

### Enhancements
* Add .ltrstore* as system index and configure test suite to add Permissions to delete system indices (#125) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/125]
* feat: #87 implemented rest endpoint to make stats available (#90) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/90]
* 81 supplier plugin health and store usage after revert (#89) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/89]
* 78 collect stats for usage and health (#79) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/79]
* Implemented Settings (#76) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/76]
* feat: Implemented circuit breaker (#71) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/71]

### Bug Fixes
* [Backport to 2.19] Refactor index refresh logic in ITs #135 (#136) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/136]
* Modify ITs to ignore transient warning (#132) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/132]
* Stashed context for GET calls (#129) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/129]
* [Backport 2.19] Modified Rest Handlers to stash context before modifying system indices #126 (#127) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/127]
* fix: added initialization of ltr settings in LtrQueryParserPlugin (#85) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/85]
* fix: fixed namings of test classes (#83) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/83]

### Maintenance
* 2.x issue93 (#96) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/96]
* fix: disabled gradle tasks forbiddenApisTest and testingConventions (#94) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/94]
* feat: Added JohannesDaniel in MAINTAINERS.md (#53) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/53]
* Update CODEOWNERS (#69) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/69]

### Infrastructure
* Added builds against JAVA 11 and 17 (#124) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/124]
* [Backport to 2.x] Support Integration Tests against an external test cluster with security plugin enabled (#122) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/122]
* Backporting commits from main to 2.x (#116) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/116]
* Modified build scripts to onboard LTR to OpenSearch (#98) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/98]
* Merge main into 2.x (#91) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/91]

### Refactoring
* [Backport to 2.x] Deprecating Redundant and duplicated API and package. Refactor with the other package (#118) [https://github.com/opensearch-project/opensearch-learning-to-rank-base/pull/118]
