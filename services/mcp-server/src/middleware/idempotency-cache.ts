/**
 * In-memory LRU idempotency cache for write-back tool operations.
 *
 * Key format: `${correlationId}:${toolName}`
 * Prevents double-execution of the same remediation action.
 *
 * Configuration:
 * - MCP_IDEMPOTENCY_TTL_SECONDS: Cache entry TTL (default: 300s / 5 minutes)
 * - Max entries: 1000 (LRU eviction on overflow)
 */

interface CacheEntry {
  result: string;
  createdAt: number;
}

const MAX_ENTRIES = 1000;

/** Internal cache store */
const cache = new Map<string, CacheEntry>();

/**
 * Returns the TTL in milliseconds from environment configuration.
 */
function getTtlMs(): number {
  const ttlSeconds = parseInt(process.env.MCP_IDEMPOTENCY_TTL_SECONDS || '300', 10);
  return (isNaN(ttlSeconds) || ttlSeconds <= 0 ? 300 : ttlSeconds) * 1000;
}

/**
 * Checks if a cache entry has expired.
 */
function isExpired(entry: CacheEntry): boolean {
  return Date.now() - entry.createdAt > getTtlMs();
}

/**
 * Builds the idempotency cache key.
 */
export function buildCacheKey(correlationId: string, toolName: string): string {
  return `${correlationId}:${toolName}`;
}

/**
 * Retrieves a cached result if it exists and has not expired.
 * Returns undefined if no valid cache entry exists.
 *
 * Refreshes the entry position in the LRU on hit (delete + re-insert).
 */
export function getCache(key: string): string | undefined {
  const entry = cache.get(key);
  if (!entry) {
    return undefined;
  }

  if (isExpired(entry)) {
    cache.delete(key);
    return undefined;
  }

  // LRU refresh: move to end (most recently accessed)
  cache.delete(key);
  cache.set(key, entry);
  return entry.result;
}

/**
 * Stores a result in the idempotency cache.
 * Evicts the oldest entry (LRU) if the cache exceeds MAX_ENTRIES.
 */
export function setCache(key: string, result: string): void {
  if (cache.has(key)) {
    cache.delete(key);
  }

  if (cache.size >= MAX_ENTRIES) {
    const oldestKey = cache.keys().next().value;
    if (oldestKey !== undefined) {
      cache.delete(oldestKey);
    }
  }

  cache.set(key, {
    result,
    createdAt: Date.now(),
  });
}

/**
 * Returns the current cache size. Used for testing/monitoring.
 */
export function getCacheSize(): number {
  return cache.size;
}

/**
 * Clears all entries from the cache. Used for testing.
 */
export function clearCache(): void {
  cache.clear();
}
