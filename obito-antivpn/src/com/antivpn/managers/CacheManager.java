package com.antivpn.managers;

import com.antivpn.Main;
import com.antivpn.database.DatabaseManager;

public class CacheManager {

    private final Main plugin;

    public CacheManager(Main plugin) {
        this.plugin = plugin;
    }

    public void addToCache(String ip, boolean isVPN, String countryCode, String isp) {
        long expireTime = System.currentTimeMillis()
                + (plugin.getConfig().getLong("cache-duration-hours", 24) * 3600000L);
        plugin.getDatabaseManager().saveCache(ip, isVPN, countryCode, isp, expireTime);
    }

    public DatabaseManager.CacheResult getCache(String ip) {
        return plugin.getDatabaseManager().getCache(ip);
    }

    public boolean isCached(String ip) {
        return plugin.getDatabaseManager().getCache(ip) != null;
    }

    public boolean isVPN(String ip) {
        DatabaseManager.CacheResult result = plugin.getDatabaseManager().getCache(ip);
        return result != null && result.isVPN;
    }

    public void removeFromCache(String ip) {
        plugin.getDatabaseManager().deleteCache(ip);
    }

    public void clearCache() {
        plugin.getDatabaseManager().clearCache();
    }

    public int getCacheSize() {
        return plugin.getDatabaseManager().getCacheSize();
    }
}
