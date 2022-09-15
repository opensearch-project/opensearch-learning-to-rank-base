ARG opensearch_version=${opensearch_version}

FROM opensearchproject/opensearch:${opensearch_version}
#ARG opensearch_version=2.5.0
#ARG ltrversion=2.1.0
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases/download/release-v${ltrversion}/ltr-plugin-v${ltrversion}.zip
