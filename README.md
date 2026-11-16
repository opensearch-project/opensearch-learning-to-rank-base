<img src="https://opensearch.org/assets/img/opensearch-logo-themed.svg" height="64px">

[![Build and Test LTR](https://github.com/opensearch-project/opensearch-learning-to-rank-base/actions/workflows/CI.yml/badge.svg)](https://github.com/opensearch-project/opensearch-learning-to-rank-base/actions/workflows/CI.yml)
[![codecov](https://codecov.io/gh/opensearch-project/opensearch-learning-to-rank-base/branch/main/graph/badge.svg)](https://codecov.io/gh/opensearch-project/opensearch-learning-to-rank-base)
[![Documentation](https://img.shields.io/badge/doc-reference-blue)](https://docs.opensearch.org/latest/search-plugins/ltr/index/)
[![Chat](https://img.shields.io/badge/chat-on%20slack-purple)](https://opensearch.slack.com/archives/C0539F41Z5X)
[![Chat](https://img.shields.io/badge/chat-on%20forums-blue)](https://forum.opensearch.org)
![PRs welcome!](https://img.shields.io/badge/PRs-welcome!-success)

## OpenSearch Learning to Rank

**OpenSearch Learning to Rank** is an OpenSearch plugin that uses machine learning to improve search relevance ranking.
It lets you store features (templated queries), log feature values to build training data, upload models trained
with tools such as RankLib or XGBoost, and rescore search results with those models at query time.

This project is a fork of the [Elasticsearch Learning to Rank plugin](https://github.com/o19s/elasticsearch-learning-to-rank),
rewritten in parts to work with OpenSearch. The original Elasticsearch LTR plugin powers search at places like
Wikimedia Foundation and Snagajob.

For usage, see the [OpenSearch Learning to Rank documentation](https://docs.opensearch.org/latest/search-plugins/ltr/index/).

## Installation

Starting with OpenSearch 2.19, the Learning to Rank plugin is included in the default OpenSearch distribution.
No separate installation is needed.

For the minimal distribution or custom builds, install the plugin zip that matches your OpenSearch version.
The plugin zips are published to Maven Central:

| OpenSearch | Location                                                                                           | Example                                                                                                                          |
|------------|----------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| 3.x        | [`org.opensearch:ltr`](https://repo1.maven.org/maven2/org/opensearch/ltr/)                       | `bin/opensearch-plugin install https://repo1.maven.org/maven2/org/opensearch/ltr/3.9.0.0/ltr-3.9.0.0.zip`                       |
| 2.19.x     | [`org.opensearch:opensearch-ltr`](https://repo1.maven.org/maven2/org/opensearch/opensearch-ltr/) | `bin/opensearch-plugin install https://repo1.maven.org/maven2/org/opensearch/opensearch-ltr/2.19.0.0/opensearch-ltr-2.19.0.0.zip` |

Zips for releases before 2.19 are available on the [releases page](https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases).

If you are already running OpenSearch, don't forget to restart it after installing the plugin.

<details>
<summary>Legacy releases</summary>

| OpenSearch | Command                                                                                                                                    |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| 1.0.0      | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.0.0/ltr-1.5.4-os1.0.0.zip`         |
| 1.1.0      | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.1.0/ltr-1.5.4-os1.1.0.zip`         |
| 1.2.0      | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.0/ltr-1.5.4-os1.2.0.zip`         |
| 1.2.2      | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.2/ltr-1.5.4-os1.2.2.zip`         |
| 1.2.3      | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/1.2.3/ltr-1.5.4-os1.2.3.zip`         |
| 2.2.1      | `bin/opensearch-plugin install https://github.com/aparo/opensearch-learning-to-rank/releases/download/2.2.1/ltr-2.0.0-os2.2.1.zip`         |
| 2.5.0      | `bin/opensearch-plugin install https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/release-v2.1.0/ltr-plugin-v2.1.0.zip` |
| 2.11.1     | `bin/opensearch-plugin install https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/release-v2.11.1/ltr-plugin-v2.11.1.zip` |
| 2.17.1     | `bin/opensearch-plugin install https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/2.17.1/ltr-2.17.1-os2.17.1.zip` |

(You'll be asked to confirm some security exceptions; pass `-b` to `opensearch-plugin` to accept them automatically.)

</details>

## Project Resources

* [Project Website](https://opensearch.org/)
* [Downloads](https://opensearch.org/downloads.html)
* [Documentation](https://docs.opensearch.org/latest/search-plugins/ltr/index/)
* Need help? Try the [Forums](https://forum.opensearch.org) or the `#search` channel on [Slack](https://opensearch.slack.com/archives/C0539F41Z5X)
* [Project Principles](https://opensearch.org/#principles)
* [Contributing to OpenSearch Learning to Rank](CONTRIBUTING.md)
* [Developer Guide](DEVELOPER_GUIDE.md)
* [Maintainer Responsibilities](MAINTAINERS.md)
* [Admin Responsibilities](ADMIN.md)
* [Security](SECURITY.md)
* [Known Issues](KNOWN_ISSUES.md)
* [Release Notes](release-notes)
* [Code of Conduct](#code-of-conduct)
* [License](#license)
* [Copyright](#copyright)

## Code of Conduct

This project has adopted the [Amazon Open Source Code of Conduct](CODE_OF_CONDUCT.md). For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq), or contact [opensource-codeofconduct@amazon.com](mailto:opensource-codeofconduct@amazon.com) with any additional questions or comments.

## License

This project is licensed under the [Apache v2.0 License](LICENSE.txt).

## Copyright

Copyright OpenSearch Contributors. See [NOTICE](NOTICE.txt) for details.
