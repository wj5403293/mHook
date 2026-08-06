package cn.mhook.ai;

import android.content.Context;
import android.content.SharedPreferences;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class McpSetting {

    private static final String PREFS = "mcp_setting";
    private static final String KEY_SERVERS = "servers";

    public static final String DEFAULT_JSON =
            "[{\"name\":\"MTApkMcp\",\"label\":\"MT管理器·APK分析\",\"url\":\"http://127.0.0.1:8787/mcp\",\"token\":\"\",\"enable\":false},"
                    + "{\"name\":\"XuanXingNieHe\",\"label\":\"玄星逆核·聚合逆向\",\"url\":\"http://127.0.0.1:8000/mcp\",\"token\":\"\",\"enable\":false},"
                    + "{\"name\":\"ProxyPinMcp\",\"label\":\"ProxyPin·抓包分析\",\"url\":\"http://127.0.0.1:9010/mcp\",\"token\":\"\",\"enable\":false}]";

    public static final String[][] PROBE_PORTS = {
            {"MTApkMcp", "8787", "8788", "8080", "9999"},
            {"XuanXingNieHe", "8000", "8001", "8080", "9000"},
            {"ProxyPinMcp", "9010", "9011", "9020"},
    };

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static JSONArray getServers(Context c) {
        String s = sp(c).getString(KEY_SERVERS, "");
        try {
            if (s != null && !s.isEmpty()) {
                return JSON.parseArray(s);
            }
        } catch (Throwable ignored) {
        }
        return JSON.parseArray(DEFAULT_JSON);
    }

    public static void saveServers(Context c, JSONArray arr) {
        sp(c).edit().putString(KEY_SERVERS, arr == null ? "" : arr.toJSONString()).apply();
    }

    public static JSONObject findServer(Context c, String name) {
        JSONArray arr = getServers(c);
        for (Object o : arr) {
            JSONObject j = (JSONObject) o;
            if (name.equals(j.getString("name"))) {
                return j;
            }
        }
        return null;
    }

    public static int enabledCount(Context c) {
        int n = 0;
        JSONArray arr = getServers(c);
        for (Object o : arr) {
            if (((JSONObject) o).getBooleanValue("enable")) {
                n++;
            }
        }
        return n;
    }
}
