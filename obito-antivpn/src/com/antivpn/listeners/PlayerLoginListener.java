package com.antivpn.listeners;

import com.antivpn.Main;
import com.antivpn.managers.VPNChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class PlayerLoginListener implements Listener {

    private final Main plugin;

    public PlayerLoginListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();
        String ip         = normalizeIP(event.getAddress().getHostAddress());

        VPNChecker.CheckResult result = plugin.getVPNChecker().checkSync(playerName, ip);

        switch (result) {
            case BLOCKED_VPN:
                handleBlock(event, playerName, ip, "VPN/Proxy",
                        plugin.getConfig().getString("kick-message",
                                "&c&lVPN / Proxy Detectado!\n&7Desative sua VPN para entrar."));
                break;

            case BLOCKED_COUNTRY:
                handleBlock(event, playerName, ip, "País bloqueado",
                        plugin.getConfig().getString("kick-message-country",
                                "&c&lAcesso Bloqueado!\n&7Apenas jogadores do Brasil podem entrar."));
                break;

            case ALLOWED:
                // Não faz nada — login prossegue normalmente
                break;
        }
    }

    private void handleBlock(AsyncPlayerPreLoginEvent event, String playerName, String ip, String reason, String message) {
        String kickMsg = colorize(message);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMsg);

        plugin.getLogger().info(String.format(
                "[AntiVPN] Bloqueado | Player: %s | IP: %s | Motivo: %s",
                playerName, ip, reason
        ));

        // Log em arquivo + SQLite (assíncrono, já estamos em thread async)
        plugin.getLogManager().logBlock(playerName, ip, reason, "", "");

        // Notifica admins na main thread
        if (plugin.getConfig().getBoolean("notify-admins", true)) {
            String alert = colorize("&c[AntiVPN] &7" + playerName +
                    " bloqueado &c(" + reason + ")&7. IP: &f" + ip);

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("antivpn.notify")) {
                        p.sendMessage(alert);
                    }
                }
            });
        }
    }

    /**
     * Converte IPv6 mapeado em IPv4.
     * Ex: ::ffff:1.2.3.4 → 1.2.3.4
     */
    private String normalizeIP(String ip) {
        return ip.startsWith("::ffff:") ? ip.substring(7) : ip;
    }

    private String colorize(String text) {
        return text.replace("&", "\u00A7");
    }
}