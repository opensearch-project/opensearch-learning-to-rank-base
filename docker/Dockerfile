ARG opensearch_version=2.17.1

FROM opensearchproject/opensearch:${opensearch_version}
ARG opensearch_version=2.17.1
ARG ltrversion=2.17.1
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/release-v${ltrversion}/ltr-plugin-v${ltrversion}.zip
