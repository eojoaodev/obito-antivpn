package com.antivpn.managers;

import com.antivpn.Main;
import com.antivpn.database.DatabaseManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class VPNChecker {

    private final Main plugin;

    // Contador de falhas consecutivas da API primária
    private final AtomicInteger primaryFailures = new AtomicInteger(0);
    private static final int MAX_FAILURES_BEFORE_WARN = 3;

    public VPNChecker(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Verificação síncrona — segura pois AsyncPlayerPreLoginEvent já roda em thread assíncrona.
     */
    public CheckResult checkSync(String playerName, String ip) {
        DatabaseManager db = plugin.getDatabaseManager();
        CacheManager cache = plugin.getCacheManager();

        // 1. Whitelist Nick
        if (db.isWhitelistedNick(playerName)) {
            return CheckResult.ALLOWED;
        }

        // 2. Whitelist IP
        if (db.isWhitelistedIP(ip)) {
            return CheckResult.ALLOWED;
        }

        // 3. Cache L1 (memória)
        DatabaseManager.CacheResult cached = cache.getFromMemory(ip);

        // 4. Cache L2 (SQLite) — só busca se L1 não achou
        if (cached == null) {
            cached = db.getCache(ip);
            if (cached != null) {
                cache.promoteToMemory(ip, cached);
            }
        }

        if (cached != null) {
            return evaluateResult(cached);
        }

        // 5. Consulta API
        return queryWithFallback(ip);
    }

    /**
     * Avalia o resultado do cache e aplica as regras de bloqueio.
     */
    private CheckResult evaluateResult(DatabaseManager.CacheResult result) {
        if (result.isVPN) return CheckResult.BLOCKED_VPN;

        if (plugin.getConfig().getBoolean("brazil-only", false)) {
            if (!result.countryCode.isEmpty() && !result.countryCode.equalsIgnoreCase("BR")) {
                return CheckResult.BLOCKED_COUNTRY;
            }
        }

        return CheckResult.ALLOWED;
    }

    /**
     * Consulta a API primária, com fallback automático para a secundária.
     */
    private CheckResult queryWithFallback(String ip) {
        String primary   = plugin.getConfig().getString("api-provider", "iphub");
        boolean fallback = plugin.getConfig().getBoolean("api-fallback", true);

        // Tenta API primária
        try {
            APIResult result = query(primary, ip);
            primaryFailures.set(0); // reset contador de falhas
            saveAndReturn(ip, result);
            return evaluateAPIResult(result);

        } catch (Exception primaryError) {
            primaryFailures.incrementAndGet();

            if (primaryFailures.get() >= MAX_FAILURES_BEFORE_WARN) {
                plugin.getLogger().warning(
                        "[AntiVPN] API primária (" + primary + ") falhou " +
                                primaryFailures.get() + "x seguidas: " + primaryError.getMessage()
                );
            }

            // Tenta fallback se habilitado
            if (fallback) {
                String fallbackProvider = primary.equalsIgnoreCase("iphub") ? "proxycheck" : "iphub";
                try {
                    plugin.getLogger().info("[AntiVPN] Tentando fallback: " + fallbackProvider + " para IP: " + ip);
                    APIResult result = query(fallbackProvider, ip);
                    saveAndReturn(ip, result);
                    return evaluateAPIResult(result);

                } catch (Exception fallbackError) {
                    plugin.getLogger().warning(
                            "[AntiVPN] Fallback (" + fallbackProvider + ") também falhou: " + fallbackError.getMessage()
                    );
                }
            }

            // Ambas falharam — aplica política de erro
            boolean blockOnError = plugin.getConfig().getBoolean("block-on-api-error", false);
            plugin.getLogger().warning("[AntiVPN] Sem resposta de API para " + ip +
                    " — " + (blockOnError ? "bloqueando" : "permitindo") + " por política.");
            return blockOnError ? CheckResult.BLOCKED_VPN : CheckResult.ALLOWED;
        }
    }

    private void saveAndReturn(String ip, APIResult result) {
        plugin.getCacheManager().addToCache(ip, result.isVPN, result.countryCode, result.isp, result.blockValue);
    }

    private CheckResult evaluateAPIResult(APIResult result) {
        if (result.isVPN) return CheckResult.BLOCKED_VPN;

        if (plugin.getConfig().getBoolean("brazil-only", false)) {
            if (!result.countryCode.isEmpty() && !result.countryCode.equalsIgnoreCase("BR")) {
                return CheckResult.BLOCKED_COUNTRY;
            }
        }

        return CheckResult.ALLOWED;
    }

    private APIResult query(String provider, String ip) throws Exception {
        switch (provider.toLowerCase()) {
            case "iphub":      return queryIPHub(ip);
            case "proxycheck": return queryProxyCheck(ip);
            default:
                throw new IllegalArgumentException("Provider desconhecido: " + provider);
        }
    }

    private APIResult queryIPHub(String ip) throws Exception {
        String apiKey = plugin.getConfig().getString("iphub-key", "");
        int timeout   = plugin.getConfig().getInt("api-timeout-seconds", 5) * 1000;

        HttpURLConnection conn = openConnection("https://v2.api.iphub.info/ip/" + ip, timeout);
        if (!apiKey.isEmpty()) conn.setRequestProperty("X-Key", apiKey);

        int code = conn.getResponseCode();
        if (code == 429) throw new Exception("IPHub rate limit atingido (429)");
        if (code != 200) throw new Exception("IPHub retornou HTTP " + code);

        String response = readBody(conn);

        String countryCode = extractString(response, "countryCode");
        String isp         = extractString(response, "isp");
        int    blockValue  = extractInt(response, "block");

        int blockLevel = plugin.getConfig().getInt("iphub-block-level", 1);
        boolean isVPN  = blockLevel >= 2
                ? blockValue >= 1
                : blockValue == 1;

        return new APIResult(isVPN, countryCode, isp, blockValue);
    }

    private APIResult queryProxyCheck(String ip) throws Exception {
        String apiKey = plugin.getConfig().getString("proxycheck-key", "");
        int timeout   = plugin.getConfig().getInt("api-timeout-seconds", 5) * 1000;

        String url = "https://proxycheck.io/v2/" + ip + "?vpn=1&asn=1";
        if (!apiKey.isEmpty()) url += "&key=" + apiKey;

        HttpURLConnection conn = openConnection(url, timeout);

        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("ProxyCheck retornou HTTP " + code);

        String response  = readBody(conn);
        boolean isVPN    = response.contains("\"proxy\":\"yes\"") || response.contains("\"vpn\":\"yes\"");
        String country   = extractString(response, "country");
        String isp       = extractString(response, "provider");

        return new APIResult(isVPN, country, isp, isVPN ? 1 : 0);
    }


    private HttpURLConnection openConnection(String urlStr, int timeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("User-Agent", "AntiVPN/" + plugin.getDescription().getVersion());
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf('"', start);
        return end == -1 ? "" : json.substring(start, end);
    }

    private int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return 0;
        start += search.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public enum CheckResult {
        ALLOWED,
        BLOCKED_VPN,
        BLOCKED_COUNTRY
    }

    private static class APIResult {
        final boolean isVPN;
        final String  countryCode;
        final String  isp;
        final int     blockValue;

        APIResult(boolean isVPN, String countryCode, String isp, int blockValue) {
            this.isVPN       = isVPN;
            this.countryCode = countryCode != null ? countryCode : "";
            this.isp         = isp != null ? isp : "";
            this.blockValue  = blockValue;
        }
    }
}