# Build an image from a local distribution

ARG opensearch_version=2.17.1
FROM opensearchproject/opensearch:${opensearch_version}
ARG ltrversion=2.17.1
ARG opensearch_version=2.17.1
ARG zip_file=ltr-${ltrversion}-os${opensearch_version}.zip
ARG plugin_file=/usr/share/opensearch/${zip_file}

COPY --chown=opensearch:opensearch build/distributions/${zip_file} ${plugin_file}
RUN /usr/share/opensearch/bin/opensearch-plugin install -b file:${plugin_file}
# check in to see if there is a better way to do this so we don't have duplicate RUN commands

