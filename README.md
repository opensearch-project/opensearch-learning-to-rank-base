# Important Notice

This is a fork of https://github.com/o19s/elasticsearch-learning-to-rank to work with OpenSearch. It's a rewrite of some parts to be able to work with OpenSearch. Please refer to official documentation of [Elasticsearch Learning to Rank](http://elasticsearch-learning-to-rank.readthedocs.io) for usage.

The OpenSearch Learning to Rank plugin uses machine learning to improve search relevance ranking. The original Elasticsearch LTR plugin powers search at places like Wikimedia Foundation and Snagajob.

# Installing

To install, you'd run a command like this but replacing with the appropriate prebuilt version zip:

| OS    | Command                                                                                                                                    |
|-------|--------------------------------------------------------------------------------------------------------------------------------------------|
| 1.0.0 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.0.0/ltr-1.5.4-os1.0.0.zip`         |
| 1.1.0 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.1.0/ltr-1.5.4-os1.1.0.zip`         |
| 1.2.0 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.0/ltr-1.5.4-os1.2.0.zip`         |
| 1.2.2 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.2/ltr-1.5.4-os1.2.2.zip`         |
| 1.2.3 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.3/ltr-1.5.4-os1.2.3.zip`         |
| 2.2.1 | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/2.2.1/ltr-2.0.0-os2.2.1.zip`         |
| 2.5.0 | `bin/opensearch-plugin install https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/release-v2.1.0/ltr-plugin-v2.1.0.zip` |
| 2.11.1 | `bin/opensearch-plugin install https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/release-v2.11.1/ltr-plugin-v2.11.1.zip` |

(It's expected you'll confirm some security exceptions, you can pass `-b` to `opensearch-plugin` to automatically install)

If you already are running OpenSearch, don't forget to restart!

# Releases

Releases can be found at https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases.

## Releasing/Packaging

Releases are done through GitHub Workflows (see `.github/workflows` in the root directory) on an as needed basis.  If you do `./gradlew build` as per above under building,
it will build all the artifacts that are in the release.

# Development

To build, you need to disable the Java security manager

```
./gradlew -Dtests.security.manager=false clean build
```

# Upgrading the OpenSearch Versions

1. Edit `gradle.properties` to have the appropriate versions (it's often easiest to go download the latest tarball from OpenSearch and simply check the versions that ship) and to increment the version of this plugin
2. Build and test as above
3. Update this README with the version info in the table above
4. Upgrade the Docker file versions in the `docker` directory
5. Test the docker image, per below.

## Development Notes

# Docker

A custom image of [OpenSearch](https://hub.docker.com/r/opensearchproject/opensearch) with the [OpenSearch Learning to Rank plugin](https://github.com/opensearch-project/opensearch-learning-to-rank-base) installed.

This image was created for the [Search with Machine Learning](https://corise.com/course/search-with-machine-learning?utm_source=daniel) course and [Search Fundamentals](https://corise.com/course/search-fundamentals?utm_source=daniel) taught by Grant Ingersoll and Daniel Tunkelang.

See the [Elasticsearch Learning to Rank](https://elasticsearch-learning-to-rank.readthedocs.io/en/latest/index.html) documentation for details on how to us.

## Building

Building the docker image is triggered via the GitHub Actions workflows automatically (for releases) or via the commands below.

Note, we are use Docker ARGs to pass through variables via the `--build-arg`.  All args have defaults

### Using local artifacts

```
docker build -f docker/local.Dockerfile .
```

### Using official releases, built locally

#### Using defaults

```
docker build -f docker/Dockerfile --tag=YOUR/IMAGE_NAME .
```

#### From Versions

```
docker build -f docker/Dockerfile --tag=YOUR/IMAGE_NAME --build-arg opensearch_version=2.2.1 --build-arg ltrversion=2.0.0 .
```

#### From a URL

```
docker build -f docker/Dockerfile --tag=YOUR/IMAGE_NAME --build-arg plugin="https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/release-test-release/ltr-plugin-test-release.zip" .
```

## Running the docker image

See the OpenSearch docs for official instructions, but this should work:

```
docker run -p 9200:9200 -p 9600:9600 -e "discovery.type=single-node" YOUR/IMAGE_NAME:latest
```

## Publishing the Docker Image
                                                
To publish the Docker image to Docker Hub, you need to kick off the Docker action workflow:

```
gh workflow run .github/workflows/docker.yml         
```
