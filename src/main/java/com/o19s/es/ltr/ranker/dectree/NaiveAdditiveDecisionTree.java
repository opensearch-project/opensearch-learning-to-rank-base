/*
 * Copyright [2017] Wikimedia Foundation
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
 */

package com.o19s.es.ltr.ranker.dectree;

import java.util.Objects;

import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.SparseFeatureVector;
import com.o19s.es.ltr.ranker.SparseLtrRanker;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;

/**
 * Naive implementation of additive decision tree.
 * May be slow when the number of trees and tree complexity if high comparatively to the number of features.
 */
public class NaiveAdditiveDecisionTree extends SparseLtrRanker implements Accountable {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(Split.class);

    private final Node[] trees;
    private final float[] weights;
    private final int modelSize;
    private final Normalizer normalizer;
    private final boolean missingAsZero;

    /**
     * TODO: Constructor for these classes are strict and not really
     * designed for a fluent building process. We might consider
     * changing this according to model parsers we implement.
     *
     * <p>Missing feature values are routed via each node's {@code default_left} flag (the XGBoost
     * per-node missing direction). Use
     * {@link #NaiveAdditiveDecisionTree(Node[], float[], int, Normalizer, boolean)} to instead treat
     * missing features as {@code 0.0} for parity with models trained on 0-imputed data.
     *
     * @param trees an array of trees
     * @param weights the respective weights
     * @param modelSize the modelSize in number of feature used
     * @param normalizer class to perform any normalization on model score
     */
    public NaiveAdditiveDecisionTree(Node[] trees, float[] weights, int modelSize, Normalizer normalizer) {
        this(trees, weights, modelSize, normalizer, false);
    }

    /**
     * @param missingAsZero when {@code true}, a feature that is missing/unset for a document is
     *                      treated as {@code 0.0} at scoring time (so it routes through each split
     *                      exactly as a real {@code 0.0} would), rather than being routed via the
     *                      per-node {@code default_left} flag. This restores parity with models
     *                      trained where missing values are imputed to {@code 0} (e.g. pandas
     *                      {@code fillna(0)} prior to training). When {@code false} (the default) the
     *                      per-node XGBoost missing direction is honored.
     */
    public NaiveAdditiveDecisionTree(Node[] trees, float[] weights, int modelSize, Normalizer normalizer, boolean missingAsZero) {
        assert trees.length == weights.length;
        this.trees = trees;
        this.weights = weights;
        this.modelSize = modelSize;
        this.normalizer = normalizer;
        this.missingAsZero = missingAsZero;
    }

    /**
     * @return whether missing feature values are treated as {@code 0.0} ({@code true}) or routed via
     *         the per-node {@code default_left} flag ({@code false}).
     */
    public boolean isMissingAsZero() {
        return missingAsZero;
    }

    @Override
    public String name() {
        return "naive_additive_decision_tree";
    }

    /**
     * Produce a feature vector whose missing/unset slots default to either {@link Float#NaN} or
     * {@code 0.0f}, depending on {@link #isMissingAsZero()}.
     *
     * <p>When {@code missingAsZero} is {@code false} (the default) unset slots are {@link Float#NaN},
     * so a feature that does not match a document is routed via the per-node {@code default_left}
     * flag (the XGBoost missing direction).
     *
     * <p>When {@code missingAsZero} is {@code true} unset slots default to {@code 0.0f}. A missing
     * feature then routes through each split exactly as a real {@code 0.0} would, and
     * {@link Split#eval(float[])} never observes {@code NaN} for genuinely-missing features (so the
     * {@code default_left} routing is effectively bypassed). This restores parity with models trained
     * where missing values are imputed to {@code 0}, and the explanation output reports a default
     * value of {@code 0.00} consistently with scoring.
     */
    @Override
    public SparseFeatureVector newFeatureVector(LtrRanker.FeatureVector reuse) {
        if (reuse != null) {
            assert reuse instanceof SparseFeatureVector;
            SparseFeatureVector vector = (SparseFeatureVector) reuse;
            vector.reset();
            return vector;
        }
        return new SparseFeatureVector(size(), missingAsZero ? 0.0f : Float.NaN);
    }

    @Override
    protected float score(SparseFeatureVector vector) {
        float sum = 0;
        float[] scores = vector.scores;
        for (int i = 0; i < trees.length; i++) {
            sum += weights[i] * trees[i].eval(scores);
        }
        return normalizer.normalize(sum);
    }

    @Override
    protected int size() {
        return modelSize;
    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED + RamUsageEstimator.sizeOf(weights) + RamUsageEstimator.sizeOf(trees);
    }

    public interface Node extends Accountable {
        boolean isLeaf();

        float eval(float[] scores);
    }

    public static class Split implements Node {
        private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(Split.class);
        private final Node left;
        private final Node right;
        private final int feature;
        private final float threshold;
        private final boolean defaultLeft;

        /**
         * Backward-compatible constructor. A missing (NaN) feature value is routed to the
         * right child, matching the behavior before per-node missing directions were honored.
         */
        public Split(Node left, Node right, int feature, float threshold) {
            this(left, right, feature, threshold, false);
        }

        /**
         * @param defaultLeft when true a missing (NaN) feature value is routed to the left (yes)
         *                    child, otherwise to the right (no) child. This mirrors XGBoost's
         *                    per-node missing direction (the "missing" pointer / "default_left" flag).
         */
        public Split(Node left, Node right, int feature, float threshold, boolean defaultLeft) {
            this.left = Objects.requireNonNull(left);
            this.right = Objects.requireNonNull(right);
            this.feature = feature;
            this.threshold = threshold;
            this.defaultLeft = defaultLeft;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public float eval(float[] scores) {
            Node n = this;
            while (!n.isLeaf()) {
                assert n instanceof Split;
                Split s = (Split) n;
                float value = scores[s.feature];
                if (Float.isNaN(value)) {
                    n = s.defaultLeft ? s.left : s.right;
                } else if (s.threshold > value) {
                    n = s.left;
                } else {
                    n = s.right;
                }
            }
            assert n instanceof Leaf;
            return n.eval(scores);
        }

        public Node getLeft() {
            return this.left;
        }

        public Node getRight() {
            return this.right;
        }

        public int getFeature() {
            return this.feature;
        }

        public float getThreshold() {
            return this.threshold;
        }

        public boolean getDefaultLeft() {
            return this.defaultLeft;
        }

        /**
         * Return the memory usage of this object in bytes. Negative values are illegal.
         */
        @Override
        public long ramBytesUsed() {
            return BASE_RAM_USED + left.ramBytesUsed() + right.ramBytesUsed();
        }
    }

    public static class Leaf implements Node {
        private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(Split.class);

        private final float output;

        public Leaf(float output) {
            this.output = output;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public float eval(float[] scores) {
            return output;
        }

        /**
         * Return the memory usage of this object in bytes. Negative values are illegal.
         */
        @Override
        public long ramBytesUsed() {
            return BASE_RAM_USED;
        }
    }
}
