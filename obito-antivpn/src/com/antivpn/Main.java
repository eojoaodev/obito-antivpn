package com.antivpn;

import com.antivpn.commands.AntiVPNCommand;
import com.antivpn.database.DatabaseManager;
import com.antivpn.listeners.PlayerLoginListener;
import com.antivpn.managers.CacheManager;
import com.antivpn.managers.VPNChecker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;
    private VPNChecker vpnChecker;
    private CacheManager cacheManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        cacheManager = new CacheManager(this);
        vpnChecker = new VPNChecker(this);

        Bukkit.getPluginManager().registerEvents(new PlayerLoginListener(this), this);
        getCommand("antivpn").setExecutor(new AntiVPNCommand(this));
        getLogger().info("antivpn enabled >> https://obitouchiha.cloud/");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getLogger().info("antivpn disable.");
    }

    public static Main getInstance() { return instance; }
    public VPNChecker getVPNChecker() { return vpnChecker; }
    public CacheManager getCacheManager() { return cacheManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
}
