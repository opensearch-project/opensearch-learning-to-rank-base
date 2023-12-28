/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.o19s.es.ltr.utils;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.index.query.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains a few methods copied from the AbstractQueryBuilder class. These methods are not accessible from sub classes
 * that do not reside in the same package.
 */
public class AbstractQueryBuilderUtils {

    private AbstractQueryBuilderUtils() {
        // Utility class with static methods only
    }

    public static void writeQueries(StreamOutput out, List<? extends QueryBuilder> queries) throws IOException {
        out.writeVInt(queries.size());
        for (QueryBuilder query : queries) {
            out.writeNamedWriteable(query);
        }
    }

    public static List<QueryBuilder> readQueries(StreamInput in) throws IOException {
        int size = in.readVInt();
        List<QueryBuilder> queries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            queries.add(in.readNamedWriteable(QueryBuilder.class));
        }
        return queries;
    }


}
