package com.antivpn.database;

import com.antivpn.Main;

import java.io.File;
import java.sql.*;

/**
 * DatabaseManager — gerencia persistência SQLite com WAL mode.
 *
 * Decisão de design: usamos SQLite simples (sem HikariCP) porque:
 *  - SQLite não suporta múltiplas conexões de escrita simultâneas de forma eficiente
 *  - O WAL mode já resolve concorrência leitura/escrita
 *  - Todo acesso ao DB já acontece em threads assíncronas
 *  - HikariCP só traria overhead real com MySQL/PostgreSQL
 */
public class DatabaseManager {

    private final Main plugin;
    private Connection connection;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            File dbFile = new File(plugin.getDataFolder(), "antivpn.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                // WAL: melhora leitura/escrita simultânea
                stmt.execute("PRAGMA journal_mode=WAL");
                // Aumenta timeout pra evitar "database is locked" em carga alta
                stmt.execute("PRAGMA busy_timeout=5000");
                // Melhora performance de escrita
                stmt.execute("PRAGMA synchronous=NORMAL");
                // Cache de páginas em memória (4MB)
                stmt.execute("PRAGMA cache_size=-4000");
            }

            createTables();
            createIndexes();
            plugin.getLogger().info("[AntiVPN] Banco de dados SQLite conectado!");

        } catch (Exception e) {
            plugin.getLogger().severe("[AntiVPN] Erro fatal ao conectar SQLite: " + e.getMessage());
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Cache de IPs verificados
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS ip_cache (" +
                            "  ip           TEXT    PRIMARY KEY," +
                            "  is_vpn       INTEGER NOT NULL," +
                            "  country_code TEXT," +
                            "  isp          TEXT," +
                            "  block_value  INTEGER DEFAULT 0," +
                            "  expire_time  INTEGER NOT NULL," +
                            "  checked_at   INTEGER NOT NULL" +
                            ")"
            );

            // Whitelist de IPs
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS whitelist_ip (" +
                            "  ip        TEXT PRIMARY KEY," +
                            "  added_by  TEXT NOT NULL," +
                            "  added_at  INTEGER NOT NULL" +
                            ")"
            );

            // Whitelist de Nicks
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS whitelist_nick (" +
                            "  nick      TEXT PRIMARY KEY COLLATE NOCASE," +
                            "  added_by  TEXT NOT NULL," +
                            "  added_at  INTEGER NOT NULL" +
                            ")"
            );

            // Log de bloqueios
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS block_log (" +
                            "  id         INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "  player     TEXT    NOT NULL," +
                            "  ip         TEXT    NOT NULL," +
                            "  reason     TEXT    NOT NULL," +
                            "  country    TEXT," +
                            "  isp        TEXT," +
                            "  blocked_at INTEGER NOT NULL" +
                            ")"
            );
        }
    }

    private void createIndexes() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Índice para limpeza por tempo de expiração
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_expire ON ip_cache(expire_time)");
            // Índice para busca de log por player
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_player ON block_log(player)");
            // Índice para busca de log por IP
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_ip ON block_log(ip)");
        }
    }

    // ==================== CACHE ====================

    public void saveCache(String ip, boolean isVPN, String countryCode, String isp, int blockValue, long expireTime) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO ip_cache (ip, is_vpn, country_code, isp, block_value, expire_time, checked_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, ip);
            ps.setInt(2, isVPN ? 1 : 0);
            ps.setString(3, countryCode);
            ps.setString(4, isp);
            ps.setInt(5, blockValue);
            ps.setLong(6, expireTime);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao salvar cache: " + e.getMessage());
        }
    }

    public CacheResult getCache(String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT is_vpn, country_code, isp, block_value, expire_time FROM ip_cache WHERE ip = ?"
        )) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (System.currentTimeMillis() > rs.getLong("expire_time")) {
                        deleteCache(ip);
                        return null;
                    }
                    return new CacheResult(
                            rs.getInt("is_vpn") == 1,
                            rs.getString("country_code"),
                            rs.getString("isp"),
                            rs.getInt("block_value")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao buscar cache: " + e.getMessage());
        }
        return null;
    }

    public void deleteCache(String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM ip_cache WHERE ip = ?"
        )) {
            ps.setString(1, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao deletar cache: " + e.getMessage());
        }
    }

    public void clearCache() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM ip_cache");
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao limpar cache: " + e.getMessage());
        }
    }

    public int getCacheSize() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ip_cache")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao contar cache: " + e.getMessage());
        }
        return 0;
    }

    public int cleanExpired() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM ip_cache WHERE expire_time < ?"
        )) {
            ps.setLong(1, System.currentTimeMillis());
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao limpar expirados: " + e.getMessage());
        }
        return 0;
    }

    // ==================== WHITELIST IP ====================

    public boolean isWhitelistedIP(String ip) {
        return existsIn("whitelist_ip", "ip", ip);
    }

    public void addWhitelistIP(String ip, String addedBy) {
        insertWhitelist("INSERT OR IGNORE INTO whitelist_ip (ip, added_by, added_at) VALUES (?, ?, ?)", ip, addedBy);
    }

    public void removeWhitelistIP(String ip) {
        deleteFrom("DELETE FROM whitelist_ip WHERE ip = ?", ip);
    }

    // ==================== WHITELIST NICK ====================

    public boolean isWhitelistedNick(String nick) {
        return existsIn("whitelist_nick", "nick", nick);
    }

    public void addWhitelistNick(String nick, String addedBy) {
        insertWhitelist("INSERT OR IGNORE INTO whitelist_nick (nick, added_by, added_at) VALUES (?, ?, ?)", nick, addedBy);
    }

    public void removeWhitelistNick(String nick) {
        deleteFrom("DELETE FROM whitelist_nick WHERE nick = ?", nick);
    }

    // ==================== BLOCK LOG ====================

    public void saveBlockLog(String player, String ip, String reason, String country, String isp) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO block_log (player, ip, reason, country, isp, blocked_at) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, player);
            ps.setString(2, ip);
            ps.setString(3, reason);
            ps.setString(4, country);
            ps.setString(5, isp);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao salvar log: " + e.getMessage());
        }
    }

    public int getTotalBlocked() {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM block_log")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao contar bloqueios: " + e.getMessage());
        }
        return 0;
    }

    public int getTodayBlocked() {
        long startOfDay = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000L);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM block_log WHERE blocked_at >= ?"
        )) {
            ps.setLong(1, startOfDay);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao contar bloqueios de hoje: " + e.getMessage());
        }
        return 0;
    }

    // ==================== HELPERS ====================

    private boolean existsIn(String table, String column, String value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + column + " = ?"
        )) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao verificar " + table + ": " + e.getMessage());
        }
        return false;
    }

    private void insertWhitelist(String sql, String value, String addedBy) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, addedBy);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao inserir whitelist: " + e.getMessage());
        }
    }

    private void deleteFrom(String sql, String value) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[AntiVPN] Erro ao deletar: " + e.getMessage());
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

    // ==================== DATA CLASSES ====================

    public static class CacheResult {
        public final boolean isVPN;
        public final String countryCode;
        public final String isp;
        public final int blockValue;

        public CacheResult(boolean isVPN, String countryCode, String isp, int blockValue) {
            this.isVPN = isVPN;
            this.countryCode = countryCode != null ? countryCode : "";
            this.isp = isp != null ? isp : "";
            this.blockValue = blockValue;
        }
    }
}