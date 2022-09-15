ARG opensearch_version=2.8.0
ARG ltrversion=2.8.0

FROM opensearchproject/opensearch:${opensearch_version}
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/release-v${ltrversion}/opensearch-ltr-${ltrversion}.zip
