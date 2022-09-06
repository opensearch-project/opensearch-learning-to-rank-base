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

package com.o19s.es.ltr.ranker.normalizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that manages Normalizer implementations
 */
public class Normalizers {
    private static final Map<String, Normalizer> NORMALIZERS = Collections.unmodifiableMap(new HashMap<String, Normalizer>() {{
        put(NOOP_NORMALIZER_NAME, new NoopNormalizer());
        put(SIGMOID_NORMALIZER_NAME, new SigmoidNormalizer());
    }});
    public static final String NOOP_NORMALIZER_NAME = "noop";
    public static final String SIGMOID_NORMALIZER_NAME = "sigmoid";

    public static Normalizer get(String name) {
        Normalizer normalizer = NORMALIZERS.get(name);
        if (normalizer == null) {
            throw new IllegalArgumentException(name + " is not a valid Normalizer");
        }
        return normalizer;
    }

    public static boolean exists(String name) {
        return NORMALIZERS.containsKey(name);
    }

    static class NoopNormalizer implements Normalizer {
        @Override
        public float normalize(float val) {
            return val;
        }
    }

    static class SigmoidNormalizer implements Normalizer {
        @Override
        public float normalize(float val) {
            return sigmoid(val);
        }

        float sigmoid(float x) {
            return (float) (1 / (1 + Math.exp(-x)));
        }
    }
}
