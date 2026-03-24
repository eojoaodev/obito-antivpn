package com.antivpn;

import com.antivpn.commands.AntiVPNCommand;
import com.antivpn.database.DatabaseManager;
import com.antivpn.listeners.PlayerLoginListener;
import com.antivpn.managers.CacheManager;
import com.antivpn.managers.LogManager;
import com.antivpn.managers.VPNChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;
    private VPNChecker vpnChecker;
    private CacheManager cacheManager;
    private DatabaseManager databaseManager;
    private LogManager logManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Ordem importa: DB → Cache → Checker → Log → Listener → Command IUUUUUUUUUUUP! não esquecer
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        cacheManager = new CacheManager(this);
        vpnChecker = new VPNChecker(this);
        logManager = new LogManager(this);

        Bukkit.getPluginManager().registerEvents(new PlayerLoginListener(this), this);

        AntiVPNCommand cmd = new AntiVPNCommand(this);
        getCommand("antivpn").setExecutor(cmd);
        getCommand("antivpn").setTabCompleter(cmd);

        printBanner();
    }

    @Override
    public void onDisable() {
        if (cacheManager != null) cacheManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        if (logManager != null) logManager.close();
        getLogger().info("[AntiVPN] Plugin desabilitado.");
    }

    private void printBanner() {
        getLogger().info("§a╔══════════════════════════════════╗");
        getLogger().info("§a║     §fAntiVPN §7v" + getDescription().getVersion() + " §a║");
        getLogger().info("§a║  §7github.com/eojoaodev           §a║");
        getLogger().info("§a║  §7obitouchiha.cloud              §a║");
        getLogger().info("§a╚══════════════════════════════════╝");
        getLogger().info("[AntiVPN] API provider: " + getConfig().getString("api-provider", "iphub"));
        getLogger().info("[AntiVPN] Brazil-only: " + getConfig().getBoolean("brazil-only", false));
        getLogger().info("[AntiVPN] Cache TTL: " + getConfig().getLong("cache-duration-hours", 24) + "h");
        getLogger().info("[AntiVPN] Fallback API: " + getConfig().getBoolean("api-fallback", true));
    }

    public static Main getInstance() { return instance; }
    public VPNChecker getVPNChecker() { return vpnChecker; }
    public CacheManager getCacheManager() { return cacheManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LogManager getLogManager() { return logManager; }
}