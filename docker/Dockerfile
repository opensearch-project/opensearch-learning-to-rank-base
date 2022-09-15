ARG build_version=2.7.0

<<<<<<< HEAD
FROM opensearchproject/opensearch:${build_version}
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/${build_version}/ltr-${build_version}-os${build_version}.zip
=======
FROM opensearchproject/opensearch:${opensearch_version}
ARG opensearch_version=2.2.1
ARG ltrversion=2.0.0rc1
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/release-v${ltrversion}/ltr-plugin-v${ltrversion}.zip
>>>>>>> 332cade (would help if we got the version/path right)


#https://github.com/gsingers/opensearch-learning-to-rank-base/releases/download/release-v2.0.0rc1/ltr-plugin-v2.0.0rc1.zip