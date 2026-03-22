package com.antivpn.managers;

import com.antivpn.Main;
import com.antivpn.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VPNChecker {

    private final Main plugin;

    // ISPs residenciais conhecidos — risco quase zero de VPN
    private static final String[] TRUSTED_ISPS = {
            "vivo", "claro", "tim", "oi ", "net ", "algar", "sercomtel",
            "brisanet", "desktop", "winity", "comcast", "at&t", "verizon",
            "charter", "spectrum", "cox", "bt ", "virgin", "vodafone",
            "telefonica", "telecom"
    };

    public VPNChecker(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Verifica o jogador de forma completamente síncrona
     * dentro do AsyncPlayerPreLoginEvent.
     * Não precisa de runTaskAsynchronously aqui!
     */
    public CheckResult checkSync(String playerName, String ip) {
        DatabaseManager db = plugin.getDatabaseManager();

        // 1. Whitelist por Nick
        if (db.isWhitelistedNick(playerName)) {
            return CheckResult.ALLOWED;
        }

        // 2. Whitelist por IP
        if (db.isWhitelistedIP(ip)) {
            return CheckResult.ALLOWED;
        }

        // 3. Verifica cache do SQLite
        DatabaseManager.CacheResult cached = db.getCache(ip);
        if (cached != null) {
            if (cached.isVPN) {
                return CheckResult.BLOCKED_VPN;
            }
            // Verifica restrição de país pelo cache
            if (plugin.getConfig().getBoolean("brazil-only", false)) {
                if (cached.countryCode != null && !cached.countryCode.isEmpty() && !cached.countryCode.equalsIgnoreCase("BR")) {
                    return CheckResult.BLOCKED_COUNTRY;
                }
            }
            return CheckResult.ALLOWED;
        }

        // 4. Consulta a API (já estamos em thread assíncrono pelo AsyncPlayerPreLoginEvent)
        try {
            APIResult apiResult = queryAPI(ip);

            // Salva no cache SQLite
            plugin.getCacheManager().addToCache(
                    ip,
                    apiResult.isVPN,
                    apiResult.countryCode,
                    apiResult.isp
            );

            // Verifica ISP confiável (camada de inteligência local)
            if (apiResult.isVPN && isTrustedISP(apiResult.isp)) {
                plugin.getLogger().warning("[AntiVPN] IP " + ip + " marcado como VPN mas ISP parece residencial: "
                        + apiResult.isp + " — permitindo com aviso.");
                return CheckResult.ALLOWED;
            }

            if (apiResult.isVPN) return CheckResult.BLOCKED_VPN;

            // Verifica restrição de país
            if (plugin.getConfig().getBoolean("brazil-only", false)) {
                if (apiResult.countryCode != null && !apiResult.countryCode.isEmpty() && !apiResult.countryCode.equalsIgnoreCase("BR")) {
                    return CheckResult.BLOCKED_COUNTRY;
                }
            }

            return CheckResult.ALLOWED;

        } catch (Exception e) {
            plugin.getLogger().warning("[AntiVPN] Erro na API para IP " + ip + ": " + e.getMessage());
            boolean blockOnError = plugin.getConfig().getBoolean("block-on-api-error", false);
            return blockOnError ? CheckResult.BLOCKED_VPN : CheckResult.ALLOWED;
        }
    }

    private boolean isTrustedISP(String isp) {
        if (isp == null || isp.isEmpty()) return false;
        String ispLower = isp.toLowerCase();
        for (String trusted : TRUSTED_ISPS) {
            if (ispLower.contains(trusted)) return true;
        }
        return false;
    }

    private APIResult queryAPI(String ip) throws Exception {
        String provider = plugin.getConfig().getString("api-provider", "iphub");
        if (provider.equalsIgnoreCase("iphub")) {
            return checkIPHub(ip);
        } else if (provider.equalsIgnoreCase("proxycheck")) {
            return checkProxyCheck(ip);
        }
        return new APIResult(false, "", "");
    }

    private APIResult checkIPHub(String ip) throws Exception {
        String apiKey = plugin.getConfig().getString("iphub-key", "");
        HttpURLConnection conn = createConnection("https://v2.api.iphub.info/ip/" + ip);
        if (!apiKey.isEmpty()) conn.setRequestProperty("X-Key", apiKey);

        if (conn.getResponseCode() != 200) return new APIResult(false, "", "");

        String response = readResponse(conn);

        // Extrai countryCode
        String countryCode = extractJSON(response, "countryCode");

        // Extrai ISP
        String isp = extractJSON(response, "isp");

        // Verifica block level
        int blockLevel = plugin.getConfig().getInt("iphub-block-level", 1);
        boolean isVPN;
        if (blockLevel == 2) {
            isVPN = response.contains("\"block\":1") || response.contains("\"block\":2");
        } else {
            isVPN = response.contains("\"block\":1");
        }

        return new APIResult(isVPN, countryCode, isp);
    }

    private APIResult checkProxyCheck(String ip) throws Exception {
        String apiKey = plugin.getConfig().getString("proxycheck-key", "");
        String url = "https://proxycheck.io/v2/" + ip + "?vpn=1&asn=1";
        if (!apiKey.isEmpty()) url += "&key=" + apiKey;

        HttpURLConnection conn = createConnection(url);
        if (conn.getResponseCode() != 200) return new APIResult(false, "", "");

        String response = readResponse(conn);
        boolean isVPN = response.contains("\"proxy\":\"yes\"") || response.contains("\"vpn\":\"yes\"");
        String countryCode = extractJSON(response, "country");
        String isp = extractJSON(response, "provider");

        return new APIResult(isVPN, countryCode, isp);
    }

    /**
     * Extrai valor simples de um JSON sem biblioteca externa
     * Ex: {"countryCode":"BR"} → "BR"
     */
    private String extractJSON(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    private HttpURLConnection createConnection(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "AntiVPN-Plugin/2.0");
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    // Enum de resultado da verificação
    public enum CheckResult {
        ALLOWED,
        BLOCKED_VPN,
        BLOCKED_COUNTRY
    }

    // Classe interna para resultado da API
    private static class APIResult {
        final boolean isVPN;
        final String countryCode;
        final String isp;

        APIResult(boolean isVPN, String countryCode, String isp) {
            this.isVPN = isVPN;
            this.countryCode = countryCode;
            this.isp = isp;
        }
    }
}
