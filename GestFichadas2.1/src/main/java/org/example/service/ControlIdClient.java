package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Fichada;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Cliente “compat” para Control iD:
 * - Usa HttpURLConnection (estable, sin problemas de chunked).
 * - Login, /users y /access_logs (con WHERE en formato ARRAY).
 * - Ajuste horario vía TIME_OFFSET_MIN (si el reloj guarda UTC).
 */
public class ControlIdClient implements IControlIdClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;     // ej: "http://192.168.88.240"
    private String sessionId;

    /** Ajuste horario en minutos (Argentina: 180). 0 si el reloj ya está en hora local. */
    private static final int TIME_OFFSET_MIN = 180;

    public ControlIdClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /* ======================== Login ======================== */

    public boolean login(String user, String pass) {
        try {
            String json = String.format(Locale.US,
                    "{\"login\":\"%s\",\"password\":\"%s\"}", esc(user), esc(pass));
            HttpResp r = postJson(baseUrl + "/login.fcgi", json, null);

            System.out.println("[login] HTTP " + r.code);
            System.out.println("[login] body: " + r.body);

            if (r.code != 200 || r.body == null || r.body.isBlank()) return false;

            JsonNode node = mapper.readTree(r.body);
            if (node.has("session")) {
                sessionId = node.get("session").asText();
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("Error login: " + e.getMessage());
            return false;
        }
    }

    private String cookie() {
        if (sessionId == null) throw new IllegalStateException("No autenticado");
        return "session=" + sessionId;
    }

    /* ======================== Users ======================== */

    /** Devuelve mapa id->nombre. Si /users falla, devuelve {} (la UI mostrará el id). */
    public Map<Long, String> fetchUsersMap() {
        try {
            HttpResp r = postJson(baseUrl + "/load_objects.fcgi",
                    "{\"object\":\"users\",\"limit\":10000}", cookie());
            System.out.println("[users] HTTP " + r.code);
            if (r.code >= 400) {
                System.out.println("[users] body: " + r.body);
                return Collections.emptyMap();
            }

            JsonNode arr = mapper.readTree(r.body).path("users");
            Map<Long, String> out = new HashMap<>();
            if (arr.isArray()) {
                for (JsonNode u : arr) {
                    long id = u.path("id").asLong();
                    String name = u.path("name").asText(String.valueOf(id));
                    out.put(id, name);
                }
            }
            return out;
        } catch (Exception ex) {
            System.out.println("fetchUsersMap WARNING: " + ex.getMessage());
            return Collections.emptyMap();
        }
    }

    /* ======================== Access Logs ======================== */

    /**
     * Trae fichadas entre fechas usando WHERE (formato ARRAY):
     *   where: [
     *     { "field":"time", "operator":">=", "value": fromEpoch },
     *     { "field":"time", "operator":"<=", "value": toEpoch }
     *   ]
     */
    public List<Fichada> fetchAccessLogs(LocalDate from, LocalDate to) throws Exception {
        ensureLogin();

        long fromEpoch = from.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long toEpoch   = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() - 1;

        String jsonArrayWhere = String.format(Locale.US,
                "{\"object\":\"access_logs\",\"where\":["
                        + "{\"field\":\"time\",\"operator\":\">=\",\"value\":%d},"
                        + "{\"field\":\"time\",\"operator\":\"<=\",\"value\":%d}"
                        + "],\"limit\":2000}", fromEpoch, toEpoch);

        HttpResp r = postJson(baseUrl + "/load_objects.fcgi", jsonArrayWhere, cookie());
        System.out.println("[access_logs ARRAY where] HTTP " + r.code);
        if (r.code >= 400) throw new RuntimeException("HTTP " + r.code + " body=" + r.body);

        JsonNode arr = mapper.readTree(r.body).path("access_logs");
        if (!arr.isArray()) return Collections.emptyList();

        List<Fichada> out = new ArrayList<>();
        for (JsonNode n : arr) {
            long epoch = n.path("time").asLong(0);
            if (epoch <= 0) continue;

            LocalDateTime dt = Instant.ofEpochSecond(epoch)
                    .plusSeconds(TIME_OFFSET_MIN * 60L)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();

            LocalDate d = dt.toLocalDate();
            if (d.isBefore(from) || d.isAfter(to)) continue;

            Long uid = n.hasNonNull("user_id") ? n.get("user_id").asLong() : null;
            long id = n.path("id").asLong();
            out.add(new Fichada(id, dt, uid));
        }

        out.sort(Comparator
                .comparing(Fichada::userId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(Fichada::dateTime));
        return out;
    }

    /* ======================== HTTP helper ======================== */

    private record HttpResp(int code, String body) {}

    private HttpResp postJson(String url, String json, String cookie) throws Exception {
        URL u = URI.create(url).toURL();
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Connection", "close"); // evita chunked/EOF
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        c.setDoOutput(true);
        c.setConnectTimeout(10_000);
        c.setReadTimeout(60_000);

        try (OutputStream os = c.getOutputStream()) {
            os.write(json.getBytes());
        }

        int code = c.getResponseCode();
        String body;
        try (InputStream is = (code >= 400 ? c.getErrorStream() : c.getInputStream())) {
            body = readAll(is);
        }
        c.disconnect();
        return new HttpResp(code, body);
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private void ensureLogin() {
        if (sessionId == null) throw new IllegalStateException("No autenticado.");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
