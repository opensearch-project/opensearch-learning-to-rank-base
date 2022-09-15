# Important Notice

This is a fork of https://github.com/o19s/elasticsearch-learning-to-rank to work with OpenSearch. It's a rewrite of some parts to be able to work with OpenSearch 1.x. Please refer to official documentation of [Elasticsearch Learning to Rank](http://elasticsearch-learning-to-rank.readthedocs.io) for usage.

The OpenSearch Learning to Rank plugin uses machine learning to improve search relevance ranking. The original Elasticsearch LTR plugin powers search at places like Wikimedia Foundation and Snagajob.


# Installing

To install, you'd run a command like this but replacing with the appropriate prebuilt version zip:

| OS    | Command                                                                                                                            |
|-------|------------------------------------------------------------------------------------------------------------------------------------|
| 1.0.0 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.0.0/ltr-1.5.4-os1.0.0.zip` |
| 1.1.0 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.1.0/ltr-1.5.4-os1.1.0.zip` |
| 1.2.0 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.0/ltr-1.5.4-os1.2.0.zip` |
| 1.2.2 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.2/ltr-1.5.4-os1.2.2.zip` |
| 1.2.3 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.3/ltr-1.5.4-os1.2.3.zip` |
| 2.2.1 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/2.2.1/ltr-2.0.0-os2.2.1.zip` |


(It's expected you'll confirm some security exceptions, you can pass `-b` to `opensearch-plugin` to automatically install)

If you already are running OpenSearch, don't forget to restart!

# Releases

Releases can be found at https://github.com/gsingers/opensearch-learning-to-rank-base/releases.


# Building

To build, you need to disable the Java security manager

        ./gradlew  -Dtests.security.manager=false clean build  

# Releasing/Packaging


Releases are done through Github Workflows (see `.github/workflows` in the root directory) on an as needed basis.  If you do `./gradlew build` as per above under building,
it will build all the artifacts that are in the release.
   


## About alpha releases

These releases are alpha because some issues with the tests due to securemock that depends on ElasticSearch security stuff.
And there are 14 failing tests.

```
Tests with failures:
- com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests.testExpressionDoubleQueryParameter
- com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests.testExpressionMissingQueryParameter
- com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests.testExpressionIntegerQueryParameter
- com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests.testExpressionShortQueryParameter
- com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests.testExpressionInvalidQueryParameter
- com.o19s.es.termstat.TermStatQueryBuilderTests.testMustRewrite
- com.o19s.es.termstat.TermStatQueryBuilderTests.testToQuery
- com.o19s.es.termstat.TermStatQueryBuilderTests.testCacheability
- com.o19s.es.ltr.feature.store.StoredFeatureParserTests.testExpressionOptimization
- com.o19s.es.termstat.TermStatQueryTests.testEmptyTerms
- com.o19s.es.termstat.TermStatQueryTests.testUniqueCount
- com.o19s.es.termstat.TermStatQueryTests.testBasicFormula
- com.o19s.es.termstat.TermStatQueryTests.testQuery
- com.o19s.es.termstat.TermStatQueryTests.testMatchCount

228 tests completed, 14 failed
```
