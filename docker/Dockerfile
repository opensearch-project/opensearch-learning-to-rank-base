ARG build_version=2.7.0

FROM opensearchproject/opensearch:${build_version}
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/${build_version}/ltr-${build_version}-os${build_version}.zip


