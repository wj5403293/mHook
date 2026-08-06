package cn.mhook.ai;

import android.content.Context;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McpManager {

    public static final int MAX_TOOL_OUTPUT = 30000;

    public static class McpTool {
        public String serverName;
        public String toolName;
        public String description;
        public JSONObject parameters;

        public String fullName() {
            return "mcp__" + serverName + "__" + toolName;
        }

        public JSONObject toFunction() {
            JSONObject fn = new JSONObject(true);
            fn.put("name", fullName());
            fn.put("description", description == null ? "" : description);
            fn.put("parameters", parameters == null ? new JSONObject(true) : parameters);
            JSONObject wrapper = new JSONObject(true);
            wrapper.put("type", "function");
            wrapper.put("function", fn);
            return wrapper;
        }
    }

    private static final Map<String, McpClient> clients = new HashMap<String, McpClient>();

    public static synchronized McpClient getClient(String name, String url, String token) {
        McpClient c = clients.get(name);
        if (c == null) {
            c = new McpClient(url, token);
            clients.put(name, c);
        }
        return c;
    }

    public static synchronized void invalidate(String name) {
        clients.remove(name);
    }

    public static synchronized void resetClients() {
        clients.clear();
    }

    public static List<McpTool> collectTools(Context ctx, List<String> errors) {
        List<McpTool> out = new ArrayList<McpTool>();
        JSONArray servers = McpSetting.getServers(ctx);
        for (Object o : servers) {
            JSONObject s = (JSONObject) o;
            if (!s.getBooleanValue("enable")) {
                continue;
            }
            String name = s.getString("name");
            String url = s.getString("url");
            String token = s.getString("token");
            try {
                McpClient c = getClient(name, url, token);
                JSONArray tools = c.listTools();
                for (Object t : tools) {
                    JSONObject tj = (JSONObject) t;
                    McpTool mt = new McpTool();
                    mt.serverName = name;
                    mt.toolName = tj.getString("name");
                    mt.description = tj.getString("description");
                    mt.parameters = tj.getJSONObject("inputSchema");
                    if (mt.parameters == null) {
                        mt.parameters = tj.getJSONObject("input_schema");
                    }
                    out.add(mt);
                }
            } catch (Throwable t) {
                invalidate(name);
                errors.add(name + " 不可用（" + shortMsg(t) + "）");
            }
        }
        return out;
    }

    public static String callTool(Context ctx, String fullName, JSONObject args) throws Exception {
        String[] parts = fullName.split("__", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("非法工具名: " + fullName);
        }
        String server = parts[1];
        String tool = parts[2];
        JSONObject s = McpSetting.findServer(ctx, server);
        if (s == null) {
            throw new IllegalArgumentException("未找到 MCP 服务器: " + server);
        }
        if (!s.getBooleanValue("enable")) {
            throw new IllegalArgumentException("服务器 " + server + " 未启用（请先在 AI→MCP 设置中启用后再试）");
        }
        try {
            McpClient c = getClient(server, s.getString("url"), s.getString("token"));
            return c.callTool(tool, args);
        } catch (Throwable t) {
            invalidate(server);
            throw t;
        }
    }

    public static void probeAndEnable(Context ctx, JSONArray servers) {
        for (Object o : servers) {
            JSONObject s = (JSONObject) o;
            String name = s.getString("name");
            for (String[] row : McpSetting.PROBE_PORTS) {
                if (name == null || !name.equals(row[0])) {
                    continue;
                }
                for (int i = 1; i < row.length; i++) {
                    int port;
                    try {
                        port = Integer.parseInt(row[i]);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (isPortOpen("127.0.0.1", port)) {
                        s.put("url", "http://127.0.0.1:" + port + "/mcp");
                        s.put("enable", true);
                        break;
                    }
                }
                break;
            }
        }
    }

    public static boolean isPortOpen(String host, int port) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n...[已截断，剩余 " + (s.length() - max) + " 字符]";
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        if (m != null && m.length() > 60) {
            m = m.substring(0, 60);
        }
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
    }
}
