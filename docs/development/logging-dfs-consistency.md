# Logging DFS Consistency

Context: Feature logging should be consistent across shards when using dfs_query_then_fetch. This document summarizes the implementation approach and relevant code paths.

What changed
- Option C: Query-phase feature logging + fetch render. Feature values are computed during the query phase (under dfs_query_then_fetch, global stats already apply) and stored per-document; fetch only renders these values, with no rescoring and no Weight creation.
- Removed legacy manual DFS “priming” during fetch logging.
- Capture global (DFS) term and collection statistics at Weight construction and reuse them during logging/scoring.
- Fallback path: when per-doc caches are unavailable, logging fetch bridges DFS-aware Weights from the query phase via a per-request Weight cache and builds one Weight for the combined logging query (previous Option A behavior).

Key code paths
- com.o19s.es.ltr.logging.LoggingFetchSubPhase
  - If all referenced RankerQueries expose non-empty per-doc feature caches, returns CachedLoggingFetchSubPhaseProcessor which renders logs directly from caches (no Weights/Scorers created in fetch).
  - If caches are missing, falls back to bridging DFS-aware Weights from the query phase via RankerQuery.setPerRequestWeightCache(Map<Query, Weight>) and creates a single Weight for the combined rewritten query (legacy Option A).
- com.o19s.es.ltr.query.RankerQuery
  - Enables a per-doc feature score cache by default during query-phase scoring (unless explicitly disabled by featureScoreCacheFlag=false).
  - getFeatureScoreCache() exposes the per-doc cache keyed by absolute shard docId (leaf docId + docBase).
  - createWeightInternal() builds feature Weights, reusing DFS-aware Weights when a per-request cache is present; RankerWeight/RankerScorer populate and read the per-doc feature cache.
- com.o19s.es.termstat.TermStatQuery / TermStatScorer / TermStatSupplier
  - TermStatQuery.TermStatWeight builds TermStates and captures TermStatistics (docFreq, totalTermFreq) and field docCount via IndexSearcher.collectionStatistics() under the correct DFS context.
  - TermStatScorer delegates to TermStatSupplier.bumpPrecomputed(...) to compute df/idf/tf/ttf/tp using precomputed global stats.
- com.o19s.es.ltr.feature.store.ScriptFeature
  - For term stats injection into script features, builds TermStates and calls IndexSearcher.collectionStatistics()/termStatistics() in the Weight constructor. Under dfs_query_then_fetch, these statistics reflect global (DFS) values. Uses TermStatSupplier.bump(...) at scoring time.

Tests
- LoggingDfsConsistencyIT verifies that df/idf-based feature logs are identical across shards under dfs_query_then_fetch with asymmetric shard-level DFs.
- TermStatQuery tests cover basic expression behavior, empty terms, matchCount/unique counts, extractTerms, and toString.
- TermStatSupplier tests verify correct mapping of entrySet() to the underlying stats (df/idf/tf/ttf/tp).

Notable fixes related to this change
- TermStatSupplier.entrySet() returned the df list for all keys; fixed to return the appropriate lists for idf/tf/ttf/tp.
- TermStatQuery.TermStatWeight.extractTerms(...) now correctly adds all query terms to the output set.
- TermStatQuery.toString(String) implemented for improved debugging.

Developer notes
- Per-request Weight cache is intentionally short-lived (created/reset within LoggingFetchSubPhase.getProcessor) to avoid memory leaks.
- TermStatWeight.isCacheable(...) returns true for per-leaf reuse; higher-level query caching is not relied upon for logging.
- When adding new term-stat-based features, ensure they follow the pattern of capturing DFS/global stats at Weight construction for correctness under dfs_query_then_fetch.
