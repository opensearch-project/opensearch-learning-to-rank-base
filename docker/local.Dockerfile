# Build an image from a local distribution

ARG build_version=2.7.0
FROM opensearchproject/opensearch:${build_version}
ARG zip_file=ltr-${build_version}-os${build_version}.zip
ARG plugin_file=/usr/share/opensearch/${zip_file}

COPY --chown=opensearch:opensearch build/distributions/${zip_file} ${plugin_file}
RUN /usr/share/opensearch/bin/opensearch-plugin install -b file:${plugin_file}
# check in to see if there is a better way to do this so we don't have duplicate RUN commands

