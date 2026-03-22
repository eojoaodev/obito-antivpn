package com.antivpn.database;

import com.antivpn.Main;

import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final Main plugin;
    private Connection connection;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            // Cria a pasta do plugin se não existir
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            File dbFile = new File(plugin.getDataFolder(), "antivpn.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            createTables();
            plugin.getLogger().info("[AntiVPN] Banco de dados SQLite conectado!");

        } catch (Exception e) {
            plugin.getLogger().severe("[AntiVPN] Erro ao conectar SQLite: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        // Tabela de cache de IPs
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS ip_cache (" +
                        "ip TEXT PRIMARY KEY," +
                        "is_vpn INTEGER NOT NULL," +
                        "country_code TEXT," +
                        "isp TEXT," +
                        "expire_time INTEGER NOT NULL" +
                        ")"
        );

        // Tabela de whitelist de IPs
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS whitelist_ip (" +
                        "ip TEXT PRIMARY KEY," +
                        "added_by TEXT," +
                        "added_at INTEGER" +
                        ")"
        );

        // Tabela de whitelist de Nicks
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS whitelist_nick (" +
                        "nick TEXT PRIMARY KEY COLLATE NOCASE," +
                        "added_by TEXT," +
                        "added_at INTEGER" +
                        ")"
        );

        stmt.close();
    }

    // ==================== CACHE ====================

    public void saveCache(String ip, boolean isVPN, String countryCode, String isp, long expireTime) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO ip_cache (ip, is_vpn, country_code, isp, expire_time) VALUES (?, ?, ?, ?, ?)"
            );
            ps.setString(1, ip);
            ps.setInt(2, isVPN ? 1 : 0);
            ps.setString(3, countryCode);
            ps.setString(4, isp);
            ps.setLong(5, expireTime);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao salvar cache: " + e.getMessage());
        }
    }

    public CacheResult getCache(String ip) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT is_vpn, country_code, isp, expire_time FROM ip_cache WHERE ip = ?"
            );
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long expireTime = rs.getLong("expire_time");
                // Verifica se expirou
                if (System.currentTimeMillis() > expireTime) {
                    rs.close(); ps.close();
                    deleteCache(ip);
                    return null;
                }
                CacheResult result = new CacheResult(
                        rs.getInt("is_vpn") == 1,
                        rs.getString("country_code"),
                        rs.getString("isp")
                );
                rs.close(); ps.close();
                return result;
            }
            rs.close(); ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao buscar cache: " + e.getMessage());
        }
        return null;
    }

    public void deleteCache(String ip) {
        try {
            PreparedStatement ps = connection.prepareStatement("DELETE FROM ip_cache WHERE ip = ?");
            ps.setString(1, ip);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao deletar cache: " + e.getMessage());
        }
    }

    public void clearCache() {
        try {
            connection.createStatement().execute("DELETE FROM ip_cache");
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao limpar cache: " + e.getMessage());
        }
    }

    public int getCacheSize() {
        try {
            ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM ip_cache");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao contar cache: " + e.getMessage());
        }
        return 0;
    }

    // ==================== WHITELIST IP ====================

    public boolean isWhitelistedIP(String ip) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM whitelist_ip WHERE ip = ?"
            );
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            boolean found = rs.next();
            rs.close(); ps.close();
            return found;
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao verificar whitelist IP: " + e.getMessage());
        }
        return false;
    }

    public void addWhitelistIP(String ip, String addedBy) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO whitelist_ip (ip, added_by, added_at) VALUES (?, ?, ?)"
            );
            ps.setString(1, ip);
            ps.setString(2, addedBy);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao adicionar whitelist IP: " + e.getMessage());
        }
    }

    public void removeWhitelistIP(String ip) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM whitelist_ip WHERE ip = ?"
            );
            ps.setString(1, ip);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao remover whitelist IP: " + e.getMessage());
        }
    }

    // ==================== WHITELIST NICK ====================

    public boolean isWhitelistedNick(String nick) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM whitelist_nick WHERE nick = ?"
            );
            ps.setString(1, nick);
            ResultSet rs = ps.executeQuery();
            boolean found = rs.next();
            rs.close(); ps.close();
            return found;
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao verificar whitelist Nick: " + e.getMessage());
        }
        return false;
    }

    public void addWhitelistNick(String nick, String addedBy) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO whitelist_nick (nick, added_by, added_at) VALUES (?, ?, ?)"
            );
            ps.setString(1, nick);
            ps.setString(2, addedBy);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao adicionar whitelist Nick: " + e.getMessage());
        }
    }

    public void removeWhitelistNick(String nick) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM whitelist_nick WHERE nick = ?"
            );
            ps.setString(1, nick);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao remover whitelist Nick: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao fechar banco: " + e.getMessage());
        }
    }

    // Classe interna para retorno do cache
    public static class CacheResult {
        public final boolean isVPN;
        public final String countryCode;
        public final String isp;

        public CacheResult(boolean isVPN, String countryCode, String isp) {
            this.isVPN = isVPN;
            this.countryCode = countryCode;
            this.isp = isp;
        }
    }
}
