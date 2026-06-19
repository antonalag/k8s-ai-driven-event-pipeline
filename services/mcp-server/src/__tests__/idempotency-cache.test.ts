import {
  getCache,
  setCache,
  getCacheSize,
  clearCache,
  buildCacheKey,
} from '../middleware/idempotency-cache';

describe('Idempotency Cache', () => {
  const originalEnv = process.env.MCP_IDEMPOTENCY_TTL_SECONDS;

  beforeEach(() => {
    clearCache();
    delete process.env.MCP_IDEMPOTENCY_TTL_SECONDS;
  });

  afterEach(() => {
    if (originalEnv === undefined) {
      delete process.env.MCP_IDEMPOTENCY_TTL_SECONDS;
    } else {
      process.env.MCP_IDEMPOTENCY_TTL_SECONDS = originalEnv;
    }
  });

  describe('buildCacheKey', () => {
    it('should combine correlationId and toolName with colon separator', () => {
      const key = buildCacheKey('abc-123', 'restart_deployment');
      expect(key).toBe('abc-123:restart_deployment');
    });
  });

  describe('getCache / setCache', () => {
    it('should return undefined for non-existent keys', () => {
      expect(getCache('nonexistent:tool')).toBeUndefined();
    });

    it('should store and retrieve a cached result', () => {
      const key = buildCacheKey('corr-1', 'restart_deployment');
      const result = JSON.stringify({ action: 'restart_deployment', status: 'completed' });

      setCache(key, result);
      expect(getCache(key)).toBe(result);
    });

    it('should return undefined for expired entries', () => {
      // Set TTL to 1 second
      process.env.MCP_IDEMPOTENCY_TTL_SECONDS = '1';

      const key = buildCacheKey('corr-2', 'scale_deployment');
      setCache(key, '{"status":"completed"}');

      // Manually expire by manipulating time
      jest.useFakeTimers();
      jest.advanceTimersByTime(1100); // 1.1 seconds

      expect(getCache(key)).toBeUndefined();
      jest.useRealTimers();
    });

    it('should overwrite existing entry with same key', () => {
      const key = buildCacheKey('corr-3', 'restart_deployment');
      setCache(key, 'first-result');
      setCache(key, 'second-result');

      expect(getCache(key)).toBe('second-result');
      expect(getCacheSize()).toBe(1);
    });
  });

  describe('LRU eviction', () => {
    it('should evict oldest entries when max capacity (1000) is exceeded', () => {
      // Fill cache to capacity
      for (let i = 0; i < 1000; i++) {
        setCache(`key-${i}:tool`, `result-${i}`);
      }
      expect(getCacheSize()).toBe(1000);

      // Adding one more should evict the oldest (key-0)
      setCache('key-1000:tool', 'result-1000');
      expect(getCacheSize()).toBe(1000);
      expect(getCache('key-0:tool')).toBeUndefined();
      expect(getCache('key-1000:tool')).toBe('result-1000');
    });

    it('should refresh LRU position on get (accessed entry not evicted)', () => {
      // Fill cache to capacity
      for (let i = 0; i < 1000; i++) {
        setCache(`key-${i}:tool`, `result-${i}`);
      }

      // Access key-0 to refresh its position
      getCache('key-0:tool');

      // Add a new entry — should evict key-1 (now oldest), NOT key-0
      setCache('key-1000:tool', 'result-1000');
      expect(getCache('key-0:tool')).toBe('result-0');
      expect(getCache('key-1:tool')).toBeUndefined();
    });
  });

  describe('TTL configuration', () => {
    it('should use default TTL of 300 seconds when env is unset', () => {
      jest.useFakeTimers();
      const key = buildCacheKey('corr-ttl', 'restart_deployment');
      setCache(key, 'result');

      // After 299 seconds, still available
      jest.advanceTimersByTime(299_000);
      expect(getCache(key)).toBe('result');

      // After 301 seconds total, expired
      jest.advanceTimersByTime(2_000);
      expect(getCache(key)).toBeUndefined();
      jest.useRealTimers();
    });

    it('should use custom TTL from MCP_IDEMPOTENCY_TTL_SECONDS', () => {
      process.env.MCP_IDEMPOTENCY_TTL_SECONDS = '60';
      jest.useFakeTimers();

      const key = buildCacheKey('corr-custom', 'scale_deployment');
      setCache(key, 'result');

      jest.advanceTimersByTime(59_000);
      expect(getCache(key)).toBe('result');

      jest.advanceTimersByTime(2_000);
      expect(getCache(key)).toBeUndefined();
      jest.useRealTimers();
    });

    it('should fall back to 300s for invalid TTL values', () => {
      process.env.MCP_IDEMPOTENCY_TTL_SECONDS = 'invalid';
      jest.useFakeTimers();

      const key = buildCacheKey('corr-invalid', 'restart_deployment');
      setCache(key, 'result');

      jest.advanceTimersByTime(299_000);
      expect(getCache(key)).toBe('result');

      jest.advanceTimersByTime(2_000);
      expect(getCache(key)).toBeUndefined();
      jest.useRealTimers();
    });

    it('should fall back to 300s for negative TTL values', () => {
      process.env.MCP_IDEMPOTENCY_TTL_SECONDS = '-5';
      jest.useFakeTimers();

      const key = buildCacheKey('corr-negative', 'restart_deployment');
      setCache(key, 'result');

      jest.advanceTimersByTime(299_000);
      expect(getCache(key)).toBe('result');
      jest.useRealTimers();
    });
  });

  describe('clearCache', () => {
    it('should remove all entries', () => {
      setCache('key-a:tool', 'result-a');
      setCache('key-b:tool', 'result-b');
      expect(getCacheSize()).toBe(2);

      clearCache();
      expect(getCacheSize()).toBe(0);
      expect(getCache('key-a:tool')).toBeUndefined();
    });
  });
});
