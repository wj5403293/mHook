package cn.mhook.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * MCP (Model Context Protocol) JSON-RPC 2.0 over streamable HTTP client.
 */
public class McpClient {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final String baseUrl;
    private final String token;
    private String sessionId;
    private boolean initialized;

    public McpClient(String baseUrl, String token) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.token = token == null ? "" : token.trim();
    }

    private String endpoint() {
        String u = baseUrl;
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/mcp")) {
            return u;
        }
        return u + "/mcp";
    }

    public synchronized void connect() throws Exception {
        JSONObject params = new JSONObject(true);
        params.put("protocolVersion", PROTOCOL_VERSION);
        JSONObject caps = new JSONObject(true);
        JSONObject toolsCaps = new JSONObject(true);
        JSONObject lc = new JSONObject(true);
        lc.put("listChanged", true);
        toolsCaps.put("tools", lc);
        caps.put("tools", toolsCaps);
        params.put("capabilities", caps);
        JSONObject clientInfo = new JSONObject(true);
        clientInfo.put("name", "mHook");
        clientInfo.put("version", "1.4.2");
        params.put("clientInfo", clientInfo);

        request("initialize", params, true);
        try {
            request("notifications/initialized", null, false);
        } catch (Throwable ignored) {
        }
        initialized = true;
    }

    public JSONArray listTools() throws Exception {
        ensureInit();
        JSONObject result = request("tools/list", new JSONObject(true), true);
        JSONArray tools = result == null ? null : result.getJSONArray("tools");
        return tools == null ? new JSONArray() : tools;
    }

    public String callTool(String name, JSONObject args) throws Exception {
        ensureInit();
        JSONObject params = new JSONObject(true);
        params.put("name", name);
        if (args != null) {
            params.put("arguments", args);
        }
        JSONObject result = request("tools/call", params, true);
        if (result == null) {
            return "[无返回]";
        }
        StringBuilder sb = new StringBuilder();
        JSONArray content = result.getJSONArray("content");
        if (content != null) {
            for (Object o : content) {
                JSONObject c = (JSONObject) o;
                String type = c.getString("type");
                if ("text".equals(type)) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    String text = c.getString("text");
                    sb.append(text == null ? "" : text);
                } else if ("image".equals(type)) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append("[图片数据已省略]");
                }
            }
        }
        String text = sb.toString().trim();
        if (result.getBooleanValue("isError")) {
            text = "[工具错误] " + text;
        }
        return text;
    }

    private void ensureInit() throws Exception {
        if (!initialized) {
            connect();
        }
    }

    private JSONObject request(String method, JSONObject params, boolean expectResult) throws Exception {
        JSONObject body = new JSONObject(true);
        body.put("jsonrpc", "2.0");
        long id = (System.currentTimeMillis() & 0x7fffffff) + (long) (Math.random() * 10000);
        body.put("id", id);
        body.put("method", method);
        if (params != null) {
            body.put("params", params);
        }
        boolean notification = !expectResult;
        if (notification) {
            body.remove("id");
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint()).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(notification ? 5000 : 120000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json, text/event-stream");
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (sessionId != null) {
                conn.setRequestProperty("Mcp-Session-Id", sessionId);
            }
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            String respSession = conn.getHeaderField("Mcp-Session-Id");
            if (respSession != null && !respSession.isEmpty() && sessionId == null) {
                sessionId = respSession;
            }
            String ct = conn.getHeaderField("Content-Type");
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String raw;
            if (ct != null && ct.contains("text/event-stream")) {
                raw = readSse(is, notification);
            } else {
                raw = readAll(is);
            }
            if (notification) {
                return null;
            }
            if (code >= 400) {
                throw new IOException("HTTP " + code + ": " + (raw.isEmpty() ? "MCP 服务异常" : raw));
            }
            JSONObject resp = JSON.parseObject(raw);
            if (resp == null) {
                throw new IOException("MCP 空响应");
            }
            if (resp.get("error") != null) {
                JSONObject e = resp.getJSONObject("error");
                throw new IOException("MCP 错误[" + method + "]: " + (e == null ? "未知" : e.getString("message")));
            }
            return resp.getJSONObject("result");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readSse(InputStream is, boolean notification) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder events = new StringBuilder();
        StringBuilder cur = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("data:")) {
                cur.append(line.substring(5));
            } else if (line.isEmpty()) {
                if (cur.length() > 0) {
                    events.append(cur).append("\n");
                    cur.setLength(0);
                }
            }
        }
        if (cur.length() > 0) {
            events.append(cur);
        }
        String raw = events.toString().trim();
        if (notification) {
            return "";
        }
        try {
            JSONObject obj = JSON.parseObject(raw);
            if (obj != null) {
                return obj.toJSONString();
            }
        } catch (Throwable ignored) {
        }
        String[] lines = raw.split("\n");
        for (String s : lines) {
            try {
                JSONObject obj = JSON.parseObject(s.trim());
                if (obj != null) {
                    return obj.toJSONString();
                }
            } catch (Throwable ignored) {
            }
        }
        return raw;
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }
}
