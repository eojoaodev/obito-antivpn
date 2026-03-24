package com.antivpn.managers;

import com.antivpn.Main;
import com.antivpn.database.DatabaseManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CacheManager {

    private final Main plugin;

    private final ConcurrentHashMap<String, DatabaseManager.CacheResult> memoryCache  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long>                         expireMap    = new ConcurrentHashMap<>();

    private final AtomicLong  hits      = new AtomicLong(0);
    private final AtomicLong  misses    = new AtomicLong(0);
    private final AtomicInteger maxSize = new AtomicInteger(0);

    public CacheManager(Main plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    public void addToCache(String ip, boolean isVPN, String countryCode, String isp, int blockValue) {
        long ttlHours = plugin.getConfig().getLong("cache-duration-hours", 24);
        long expireTime = System.currentTimeMillis() + (ttlHours * 3_600_000L);

        int limit = plugin.getConfig().getInt("max-memory-cache-size", 5000);

        // Evicção simples: se ultrapassar o limite, não adiciona no L1
        // O dado ainda vai pro L2 (SQLite)
        if (memoryCache.size() < limit) {
            DatabaseManager.CacheResult result = new DatabaseManager.CacheResult(isVPN, countryCode, isp, blockValue);
            memoryCache.put(ip, result);
            expireMap.put(ip, expireTime);
            maxSize.updateAndGet(v -> Math.max(v, memoryCache.size()));
        }

        // L2 sempre recebe, independente do limite de memória
        final long expire = expireTime;
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getDatabaseManager().saveCache(ip, isVPN, countryCode, isp, blockValue, expire);
            }
        }.runTaskAsynchronously(plugin);
    }

    public DatabaseManager.CacheResult getFromMemory(String ip) {
        Long expireTime = expireMap.get(ip);
        if (expireTime == null) return null;

        if (System.currentTimeMillis() > expireTime) {
            evict(ip);
            misses.incrementAndGet();
            return null;
        }

        DatabaseManager.CacheResult result = memoryCache.get(ip);
        if (result != null) hits.incrementAndGet();
        return result;
    }

    public void promoteToMemory(String ip, DatabaseManager.CacheResult result) {
        int limit = plugin.getConfig().getInt("max-memory-cache-size", 5000);
        if (memoryCache.size() >= limit) return;

        memoryCache.put(ip, result);
        long ttlHours = plugin.getConfig().getLong("cache-duration-hours", 24);
        expireMap.put(ip, System.currentTimeMillis() + (ttlHours * 3_600_000L));
    }

    public void removeFromCache(String ip) {
        evict(ip);
        plugin.getDatabaseManager().deleteCache(ip);
    }

    public void clearCache() {
        memoryCache.clear();
        expireMap.clear();
        hits.set(0);
        misses.set(0);
        plugin.getDatabaseManager().clearCache();
    }

    private void evict(String ip) {
        memoryCache.remove(ip);
        expireMap.remove(ip);
    }

    public int getMemoryCacheSize()  { return memoryCache.size(); }
    public int getDbCacheSize()      { return plugin.getDatabaseManager().getCacheSize(); }
    public long getCacheHits()       { return hits.get(); }
    public long getCacheMisses()     { return misses.get(); }
    public int getMaxMemorySeen()    { return maxSize.get(); }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (hits.get() * 100.0 / total);
    }
    //  LIMPEZA AUTOMÁTICA

    public void shutdown() {
        // Chamado no onDisable — nada a fazer, BukkitRunnable é cancelado pelo Bukkit :)))))
    }

    private void startCleanupTask() {
        long intervalMinutes = plugin.getConfig().getLong("cleanup-interval-minutes", 30);
        long intervalTicks   = intervalMinutes * 60 * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                int removedMemory = 0;

                for (Map.Entry<String, Long> entry : expireMap.entrySet()) {
                    if (now > entry.getValue()) {
                        evict(entry.getKey());
                        removedMemory++;
                    }
                }

                int removedDb = plugin.getDatabaseManager().cleanExpired();

                if (removedMemory > 0 || removedDb > 0) {
                    plugin.getLogger().info(String.format(
                            "[AntiVPN] Limpeza: %d da memória, %d do SQLite | L1: %d entradas | Hit rate: %.1f%%",
                            removedMemory, removedDb, memoryCache.size(), getHitRate()
                    ));
                }
            }
        }.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }
}