package com.antivpn.commands;

import com.antivpn.Main;
import com.antivpn.database.DatabaseManager;
import com.antivpn.managers.VPNChecker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AntiVPNCommand implements CommandExecutor {

    private final Main plugin;

    public AntiVPNCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("antivpn.admin")) {
            sender.sendMessage("\u00A7cSem permissão.");
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        DatabaseManager db = plugin.getDatabaseManager();

        switch (args[0].toLowerCase()) {

            // /antivpn reload
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage("\u00A7a[AntiVPN] Config recarregado!");
                break;

            // /antivpn cache | /antivpn cache clear
            case "cache":
                if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                    plugin.getCacheManager().clearCache();
                    sender.sendMessage("\u00A7a[AntiVPN] Cache limpo!");
                } else {
                    sender.sendMessage("\u00A7e[AntiVPN] Entradas no cache: \u00A7f"
                            + plugin.getCacheManager().getCacheSize());
                }
                break;

            // /antivpn check <jogador>
            case "check":
                if (args.length < 2) {
                    sender.sendMessage("\u00A7cUso: /antivpn check <jogador>");
                    return true;
                }
                org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("\u00A7cJogador não encontrado ou offline.");
                    return true;
                }
                String ip = target.getAddress().getAddress().getHostAddress();
                plugin.getCacheManager().removeFromCache(ip); // Força nova verificação
                sender.sendMessage("\u00A7e[AntiVPN] Verificando \u00A7f" + args[1]
                        + "\u00A7e (IP: \u00A7f" + ip + "\u00A7e)...");
                org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    VPNChecker.CheckResult result = plugin.getVPNChecker().checkSync(args[1], ip);
                    String res = result == VPNChecker.CheckResult.ALLOWED
                            ? "\u00A7aIP LIMPO" : "\u00A7cBLOQUEADO (" + result.name() + ")";
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage("\u00A7e[AntiVPN] Resultado: " + res));
                });
                break;

            // /antivpn whitelist ip add/remove <ip>
            // /antivpn whitelist nick add/remove <nick>
            case "whitelist":
                if (args.length < 4) {
                    sender.sendMessage("\u00A7cUso: /antivpn whitelist <ip|nick> <add|remove> <valor>");
                    return true;
                }
                String type = args[1].toLowerCase();
                String action = args[2].toLowerCase();
                String value = args[3];
                String addedBy = sender.getName();

                if (type.equals("ip")) {
                    if (action.equals("add")) {
                        db.addWhitelistIP(value, addedBy);
                        sender.sendMessage("\u00A7a[AntiVPN] IP \u00A7f" + value + "\u00A7a adicionado à whitelist!");
                    } else if (action.equals("remove")) {
                        db.removeWhitelistIP(value);
                        sender.sendMessage("\u00A7a[AntiVPN] IP \u00A7f" + value + "\u00A7a removido da whitelist!");
                    }
                } else if (type.equals("nick")) {
                    if (action.equals("add")) {
                        db.addWhitelistNick(value, addedBy);
                        sender.sendMessage("\u00A7a[AntiVPN] Nick \u00A7f" + value + "\u00A7a adicionado à whitelist!");
                    } else if (action.equals("remove")) {
                        db.removeWhitelistNick(value);
                        sender.sendMessage("\u00A7a[AntiVPN] Nick \u00A7f" + value + "\u00A7a removido da whitelist!");
                    }
                } else {
                    sender.sendMessage("\u00A7cTipo inválido. Use: ip ou nick");
                }
                break;

            default:
                sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("\u00A7b\u00A7l--- AntiVPN v2.0 ---");
        sender.sendMessage("\u00A7e/antivpn reload \u00A77- Recarrega o config");
        sender.sendMessage("\u00A7e/antivpn cache \u00A77- Ver tamanho do cache");
        sender.sendMessage("\u00A7e/antivpn cache clear \u00A77- Limpa o cache");
        sender.sendMessage("\u00A7e/antivpn check <jogador> \u00A77- Verifica um jogador");
        sender.sendMessage("\u00A7e/antivpn whitelist ip add <ip> \u00A77- Whitelist por IP");
        sender.sendMessage("\u00A7e/antivpn whitelist ip remove <ip> \u00A77- Remove IP");
        sender.sendMessage("\u00A7e/antivpn whitelist nick add <nick> \u00A77- Whitelist por Nick");
        sender.sendMessage("\u00A7e/antivpn whitelist nick remove <nick> \u00A77- Remove Nick");
    }
}
