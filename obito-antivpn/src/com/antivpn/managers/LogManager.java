package com.antivpn.managers;

import com.antivpn.Main;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

public class LogManager {

    private final Main plugin;
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private File logFile;

    public LogManager(Main plugin) {
        this.plugin = plugin;
        setupLogFile();
        startFlushTask();
    }

    private void setupLogFile() {
        logFile = new File(plugin.getDataFolder(), "blocks.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[AntiVPN] Não foi possível criar blocks.log: " + e.getMessage());
            }
        }
    }

    public void logBlock(String player, String ip, String reason, String country, String isp) {
        if (!plugin.getConfig().getBoolean("log-to-file", true)) return;

        String entry = String.format("[%s] BLOCKED | Player: %-16s | IP: %-15s | Reason: %-15s | Country: %-3s | ISP: %s",
            sdf.format(new Date()), player, ip, reason, country, isp);

        queue.offer(entry);

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getDatabaseManager().saveBlockLog(player, ip, reason, country, isp);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void startFlushTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (queue.isEmpty()) return;

                try (FileWriter fw = new FileWriter(logFile, true);
                     PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {

                    String entry;
                    while ((entry = queue.poll()) != null) {
                        pw.println(entry);
                    }

                } catch (IOException e) {
                    plugin.getLogger().warning("[AntiVPN] Erro ao escrever log: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 200L, 200L); // 200 ticks = 10 segundos
    }

    public void close() {
        if (queue.isEmpty()) return;
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            String entry;
            while ((entry = queue.poll()) != null) {
                pw.println(entry);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao fechar log: " + e.getMessage());
        }
    }
}