/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.o19s.es.ltr.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.protobufs.ObjectMap;
import org.opensearch.protobufs.QueryContainer;
import org.opensearch.protobufs.StoredLtrQuery;
import org.opensearch.transport.grpc.spi.QueryBuilderProtoConverter;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import com.o19s.es.ltr.utils.FeatureStoreLoader;

/**
 * Converts a protobuf {@link StoredLtrQuery} ({@code sltr}) message into a
 * {@link StoredLtrQueryBuilder} so that LTR queries can be issued over the gRPC transport.
 *
 * <p>This converter is registered with the transport-grpc module via the
 * {@link QueryBuilderProtoConverter} SPI (see the {@code META-INF/services} descriptor). Because
 * {@link StoredLtrQueryBuilder} requires a {@link FeatureStoreLoader} and {@link LTRStats} that are
 * only available on the running {@link LtrQueryParserPlugin}, the converter is constructed with the
 * plugin instance (the extension loader supports a single-argument constructor taking the extending
 * plugin) and pulls those dependencies from it.
 */
public class StoredLtrQueryBuilderProtoConverter implements QueryBuilderProtoConverter {

    private final FeatureStoreLoader storeLoader;
    private final LTRStats ltrStats;

    /**
     * Constructs the converter from the LTR plugin, obtaining the feature-store loader and stats
     * needed to build {@link StoredLtrQueryBuilder} instances.
     *
     * @param plugin the LTR plugin instance, injected by the extension loader
     */
    public StoredLtrQueryBuilderProtoConverter(LtrQueryParserPlugin plugin) {
        this.storeLoader = plugin.getFeatureStoreLoader();
        this.ltrStats = plugin.getLtrStats();
    }

    @Override
    public QueryContainer.QueryContainerCase getHandledQueryCase() {
        return QueryContainer.QueryContainerCase.SLTR;
    }

    @Override
    public QueryBuilder fromProto(QueryContainer queryContainer) {
        if (queryContainer == null || queryContainer.getQueryContainerCase() != QueryContainer.QueryContainerCase.SLTR) {
            throw new IllegalArgumentException("QueryContainer does not contain an sltr query");
        }

        StoredLtrQuery proto = queryContainer.getSltr();

        StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(storeLoader);
        builder.ltrStats(ltrStats);

        if (proto.hasModel()) {
            builder.modelName(proto.getModel());
        }
        if (proto.hasFeatureset()) {
            builder.featureSetName(proto.getFeatureset());
        }
        if (proto.hasStore()) {
            builder.storeName(proto.getStore());
        }
        if (proto.hasCache()) {
            builder.featureScoreCacheFlag(proto.getCache());
        }
        if (proto.hasParams()) {
            builder.params(fromObjectMap(proto.getParams()));
        }
        if (proto.getActiveFeaturesCount() > 0) {
            builder.activeFeatures(new ArrayList<>(proto.getActiveFeaturesList()));
        }
        if (proto.hasBoost()) {
            builder.boost(proto.getBoost());
        }
        if (proto.hasXName()) {
            builder.queryName(proto.getXName());
        }

        return builder;
    }

    /**
     * Converts a protobuf {@link ObjectMap} into a plain {@code Map<String, Object>} suitable for the
     * feature templates. The transport-grpc {@code ObjectMapProtoUtils} helper lives in the core module
     * (not the published SPI artifact), so the conversion is reimplemented here.
     */
    private static Map<String, Object> fromObjectMap(ObjectMap objectMap) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, ObjectMap.Value> entry : objectMap.getFieldsMap().entrySet()) {
            map.put(entry.getKey(), fromValue(entry.getValue()));
        }
        return map;
    }

    private static Object fromValue(ObjectMap.Value value) {
        switch (value.getValueCase()) {
            case NULL_VALUE:
                return null;
            case INT32:
                return value.getInt32();
            case INT64:
                return value.getInt64();
            case FLOAT:
                return value.getFloat();
            case DOUBLE:
                return value.getDouble();
            case STRING:
                return value.getString();
            case BOOL:
                return value.getBool();
            case LIST_VALUE:
                List<Object> list = new ArrayList<>();
                for (ObjectMap.Value listEntry : value.getListValue().getValueList()) {
                    list.add(fromValue(listEntry));
                }
                return list;
            case OBJECT_MAP:
                return fromObjectMap(value.getObjectMap());
            default:
                throw new IllegalArgumentException("Unsupported ObjectMap value type: " + value.getValueCase());
        }
    }
}
