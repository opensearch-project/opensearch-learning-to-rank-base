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

## Releasing/Packaging


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


# Building

To build, you need to disable the Java security manager

./gradlew  -Dtests.security.manager=false clean build  


# OpenSearch with Learning to Rank Docker Image

A custom image of [OpenSearch](https://hub.docker.com/r/opensearchproject/opensearch) with the [OpenSearch Learning to Rank plugin](https://github.com/gsingers/opensearch-learning-to-rank-base) installed.

This image was created for the [Search with Machine Learning](https://corise.com/course/search-with-machine-learning?utm_source=daniel) course and [Search Fundamentals](https://corise.com/course/search-fundamentals?utm_source=daniel) taught by Grant Ingersoll and Daniel Tunkelang.

See the [Elasticsearch Learning to Rank](https://elasticsearch-learning-to-rank.readthedocs.io/en/latest/index.html) documentation for details on how to us.

## Building

Building the docker image is triggered via the Github Actions workflows automatically (for releases) or via the commands below.

Note, we are use Docker ARGs to pass through variables via the --build-arg.  All args have defaults

### Using local artifacts

        docker build -f docker/local.Dockerfile 

### Using official releases, built locally

#### Using defaults

        docker build -f docker/Dockerfile --tag=YOUR/IMAGE_NAME 

#### From Versions

        docker build -f docker/Dockerfile --tag=YOUR/IMAGE_NAME --build-arg opensearch_version=2.2.1 --build-arg ltrversion=2.0.0 .


#### From a URL

        docker build -f docker/Dockerfile --tag=YOUR/IMAGE_NAME --build-arg plugin="https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/release-test-release/ltr-plugin-test-release.zip" .


## Running the docker image

See the OpenSearch docs for official instructions, but this should work:

        docker run -p 9200:9200 -p 9600:9600 -e "discovery.type=single-node" YOUR/IMAGE_NAME:latest