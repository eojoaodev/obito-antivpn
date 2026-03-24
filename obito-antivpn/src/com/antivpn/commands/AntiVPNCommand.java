package com.antivpn.commands;

import com.antivpn.Main;
import com.antivpn.database.DatabaseManager;
import com.antivpn.managers.CacheManager;
import com.antivpn.managers.VPNChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AntiVPNCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    // Prefixo colorido para mensagens
    private static final String PREFIX   = "\u00A7b\u00A7lAntiVPN \u00A77\u00BB \u00A7f";
    private static final String SUCCESS  = "\u00A7a";
    private static final String ERROR    = "\u00A7c";
    private static final String INFO     = "\u00A7e";

    public AntiVPNCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("antivpn.admin")) {
            sender.sendMessage(ERROR + "Você não tem permissão para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":    handleReload(sender);          break;
            case "stats":     handleStats(sender);           break;
            case "cache":     handleCache(sender, args);     break;
            case "check":     handleCheck(sender, args);     break;
            case "whitelist": handleWhitelist(sender, args); break;
            default:          sendHelp(sender);              break;
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(PREFIX + SUCCESS + "Configurações recarregadas com sucesso!");
    }

    private void handleStats(CommandSender sender) {
        CacheManager cache = plugin.getCacheManager();
        DatabaseManager db = plugin.getDatabaseManager();

        sender.sendMessage("\u00A7b\u00A7l--- AntiVPN Stats ---");
        sender.sendMessage(INFO + "Cache (memória):  \u00A7f" + cache.getMemoryCacheSize() + " entradas");
        sender.sendMessage(INFO + "Cache (SQLite):   \u00A7f" + cache.getDbCacheSize() + " entradas");
        sender.sendMessage(INFO + "Hit rate:         \u00A7f" + String.format("%.1f%%", cache.getHitRate()));
        sender.sendMessage(INFO + "Hits / Misses:    \u00A7f" + cache.getCacheHits() + " / " + cache.getCacheMisses());
        sender.sendMessage(INFO + "Pico memória:     \u00A7f" + cache.getMaxMemorySeen() + " entradas");
        sender.sendMessage(INFO + "Bloqueios hoje:   \u00A7f" + db.getTodayBlocked());
        sender.sendMessage(INFO + "Bloqueios total:  \u00A7f" + db.getTotalBlocked());
        sender.sendMessage(INFO + "API provider:     \u00A7f" + plugin.getConfig().getString("api-provider", "iphub"));
        sender.sendMessage(INFO + "Fallback API:     \u00A7f" + plugin.getConfig().getBoolean("api-fallback", true));
    }

    private void handleCache(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            plugin.getCacheManager().clearCache();
            sender.sendMessage(PREFIX + SUCCESS + "Cache limpo! (memória + SQLite)");
        } else if (args.length > 2 && args[1].equalsIgnoreCase("remove")) {
            String ip = args[2];
            plugin.getCacheManager().removeFromCache(ip);
            sender.sendMessage(PREFIX + SUCCESS + "IP " + ip + " removido do cache.");
        } else {
            CacheManager cache = plugin.getCacheManager();
            sender.sendMessage(PREFIX + INFO + "Memória: \u00A7f" + cache.getMemoryCacheSize()
                    + INFO + " | SQLite: \u00A7f" + cache.getDbCacheSize()
                    + INFO + " | Hit rate: \u00A7f" + String.format("%.1f%%", cache.getHitRate()));
        }
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ERROR + "Uso: /antivpn check <jogador>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ERROR + "Jogador não encontrado ou offline.");
            return;
        }

        String ip   = target.getAddress().getAddress().getHostAddress();
        String name = target.getName();

        // Remove do cache para forçar nova consulta à API
        plugin.getCacheManager().removeFromCache(ip);
        sender.sendMessage(PREFIX + INFO + "Verificando " + name + " (IP: " + ip + ")...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            VPNChecker.CheckResult result = plugin.getVPNChecker().checkSync(name, ip);

            String resultMsg = result == VPNChecker.CheckResult.ALLOWED
                    ? SUCCESS + "IP LIMPO ✔"
                    : ERROR + "BLOQUEADO — " + result.name();

            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(PREFIX + "Resultado para " + name + ": " + resultMsg));
        });
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ERROR + "Uso: /antivpn whitelist <ip|nick> <add|remove> <valor>");
            return;
        }

        String type    = args[1].toLowerCase();
        String action  = args[2].toLowerCase();
        String value   = args[3];
        String addedBy = sender.getName();
        DatabaseManager db = plugin.getDatabaseManager();

        if (type.equals("ip")) {
            if (action.equals("add")) {
                db.addWhitelistIP(value, addedBy);
                sender.sendMessage(PREFIX + SUCCESS + "IP \u00A7f" + value + SUCCESS + " adicionado à whitelist!");
            } else if (action.equals("remove")) {
                db.removeWhitelistIP(value);
                sender.sendMessage(PREFIX + SUCCESS + "IP \u00A7f" + value + SUCCESS + " removido da whitelist!");
            } else {
                sender.sendMessage(ERROR + "Ação inválida. Use: add ou remove");
            }
        } else if (type.equals("nick")) {
            if (action.equals("add")) {
                db.addWhitelistNick(value, addedBy);
                sender.sendMessage(PREFIX + SUCCESS + "Nick \u00A7f" + value + SUCCESS + " adicionado à whitelist!");
            } else if (action.equals("remove")) {
                db.removeWhitelistNick(value);
                sender.sendMessage(PREFIX + SUCCESS + "Nick \u00A7f" + value + SUCCESS + " removido da whitelist!");
            } else {
                sender.sendMessage(ERROR + "Ação inválida. Use: add ou remove");
            }
        } else {
            sender.sendMessage(ERROR + "Tipo inválido. Use: ip ou nick");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("\u00A7b\u00A7l--- AntiVPN " + plugin.getDescription().getVersion() + " ---");
        sender.sendMessage(INFO + "/antivpn reload                          \u00A77- Recarrega o config");
        sender.sendMessage(INFO + "/antivpn stats                           \u00A77- Estatísticas do plugin");
        sender.sendMessage(INFO + "/antivpn cache                           \u00A77- Info do cache");
        sender.sendMessage(INFO + "/antivpn cache clear                     \u00A77- Limpa o cache");
        sender.sendMessage(INFO + "/antivpn cache remove <ip>               \u00A77- Remove IP do cache");
        sender.sendMessage(INFO + "/antivpn check <jogador>                 \u00A77- Verifica um jogador");
        sender.sendMessage(INFO + "/antivpn whitelist ip add <ip>           \u00A77- Whitelist por IP");
        sender.sendMessage(INFO + "/antivpn whitelist ip remove <ip>        \u00A77- Remove IP da whitelist");
        sender.sendMessage(INFO + "/antivpn whitelist nick add <nick>       \u00A77- Whitelist por Nick");
        sender.sendMessage(INFO + "/antivpn whitelist nick remove <nick>    \u00A77- Remove Nick da whitelist");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("antivpn.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return filter(Arrays.asList("reload", "stats", "cache", "check", "whitelist"), args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "cache":     return filter(Arrays.asList("clear", "remove"), args[1]);
                case "whitelist": return filter(Arrays.asList("ip", "nick"), args[1]);
                case "check":     return getOnlineNames(args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            return filter(Arrays.asList("add", "remove"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getOnlineNames(String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}