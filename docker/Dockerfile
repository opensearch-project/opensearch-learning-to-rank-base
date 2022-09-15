ARG opensearch_version=2.2.1
ARG ltrversion=2.0.0
ARG plugin=https://github.com/gsingers/opensearch-learning-to-rank/releases/download/${opensearch_version}/ltr-${ltrversion}-os${opensearch_version}.zip

FROM opensearchproject/opensearch:${opensearch_version}
RUN /usr/share/opensearch/bin/opensearch-plugin install -b ${plugin}


