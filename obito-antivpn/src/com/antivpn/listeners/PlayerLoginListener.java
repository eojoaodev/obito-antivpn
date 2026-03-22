package com.antivpn.listeners;

import com.antivpn.Main;
import com.antivpn.managers.VPNChecker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class PlayerLoginListener implements Listener {

    private final Main plugin;

    public PlayerLoginListener(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * AsyncPlayerPreLoginEvent:
     * - Roda ANTES de qualquer coisa no login
     * - Já é assíncrono por natureza (sem runTaskAsync manual)
     * - disallow() bloqueia na conexão, sem "efeito sanfona"
     * - Elimina o handleDisconnection() called twice
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();
        String ip = event.getAddress().getHostAddress();

        // Converte IPv6 mapeado pra IPv4
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }

        // Verifica — já estamos em thread assíncrono, pode chamar direto
        VPNChecker.CheckResult result = plugin.getVPNChecker().checkSync(playerName, ip);

        switch (result) {
            case BLOCKED_VPN:
                String kickVPN = plugin.getConfig()
                    .getString("kick-message", "&cVPN detectada!")
                    .replace("&", "\u00A7");
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickVPN);
                plugin.getLogger().info("[AntiVPN] " + playerName + " bloqueado por VPN. IP: " + ip);
                notifyAdmins(playerName, ip, "VPN/Proxy");
                break;

            case BLOCKED_COUNTRY:
                String kickCountry = plugin.getConfig()
                    .getString("kick-message-country",
                        "&cApenas jogadores do Brasil podem entrar!")
                    .replace("&", "\u00A7");
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickCountry);
                plugin.getLogger().info("[AntiVPN] " + playerName + " bloqueado por país. IP: " + ip);
                notifyAdmins(playerName, ip, "País bloqueado");
                break;

            case ALLOWED:
                // Passa direto, não faz nada
                break;
        }
    }

    private void notifyAdmins(String playerName, String ip, String reason) {
        if (!plugin.getConfig().getBoolean("notify-admins", true)) return;

        // Notificação é salva e enviada quando os admins estiverem no thread principal
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            String alert = "\u00A7c[AntiVPN] \u00A77" + playerName
                + " bloqueado (" + reason + "). IP: \u00A7f" + ip;
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("antivpn.notify")) {
                    p.sendMessage(alert);
                }
            }
        });
    }
}
