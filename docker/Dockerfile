ARG opensearch_version=2.2.1
ARG ltrversion=2.0.0rc1

FROM opensearchproject/opensearch:${opensearch_version}
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/${opensearch_version}/ltr-${ltrversion}-os${opensearch_version}.zip


