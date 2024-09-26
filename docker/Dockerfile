ARG opensearch_version=2.15.0

FROM opensearchproject/opensearch:${opensearch_version}
ARG opensearch_version=2.15.0
ARG ltrversion=2.15.0
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/release-v${ltrversion}/ltr-plugin-v${ltrversion}.zip


#https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/release-v2.0.0rc1/ltr-plugin-v2.0.0rc1.zip
