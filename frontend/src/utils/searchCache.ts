import type { SearchPage } from '../api/search';

const CACHE_TTL_MS = 60_000;
const MAX_ENTRIES = 48;

type CacheEntry = {
  data: SearchPage;
  at: number;
};

const cache = new Map<string, CacheEntry>();

export function buildSearchCacheKey(params: Record<string, unknown>) {
  return JSON.stringify(params);
}

export function getSearchCache(key: string): SearchPage | null {
  const hit = cache.get(key);
  if (!hit) return null;
  if (Date.now() - hit.at > CACHE_TTL_MS) {
    cache.delete(key);
    return null;
  }
  return hit.data;
}

export function setSearchCache(key: string, data: SearchPage) {
  cache.set(key, { data, at: Date.now() });
  if (cache.size <= MAX_ENTRIES) return;
  const oldest = [...cache.entries()].sort((a, b) => a[1].at - b[1].at)[0];
  if (oldest) cache.delete(oldest[0]);
}

export function clearSearchCache() {
  cache.clear();
}
