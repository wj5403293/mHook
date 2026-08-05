package cn.mhook.activity.hook;

import android.app.Activity;
import android.content.DialogInterface;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.tamsiree.rxkit.RxEncryptTool;
import com.tamsiree.rxkit.RxTimeTool;
import com.tamsiree.rxkit.view.RxToast;

import java.util.ArrayList;
import java.util.List;

import cn.mhook.mhook.contentprovider.jsonCfg;

public class HookImport {

    public static void importFromJson(final Activity activity, final String text) {
        importFromJson(activity, text, null);
    }

    public static void importFromJson(final Activity activity, final String text, final Runnable onDone) {
        final ImportResult result = new ImportResult();
        result.onDone = onDone;
        if (text == null || text.trim().isEmpty()) {
            RxToast.warning("导入内容为空");
            return;
        }
        final JSONArray list = new JSONArray();
        try {
            Object obj = JSON.parse(text.trim());
            if (obj instanceof JSONArray) {
                list.addAll((JSONArray) obj);
            } else if (obj instanceof JSONObject) {
                list.add(obj);
            } else {
                RxToast.error("无法解析的导入格式");
                return;
            }
        } catch (Throwable e) {
            RxToast.error("解析失败：" + e.getMessage());
            return;
        }
        if (list.isEmpty()) {
            RxToast.warning("导入内容为空");
            return;
        }
        processNext(activity, list, 0, result);
    }

    private static void processNext(final Activity activity, final JSONArray list, final int index, final ImportResult result) {
        if (index >= list.size()) {
            showResult(activity, result);
            return;
        }
        final JSONObject item = JSONObject.parseObject(list.getJSONObject(index).toJSONString());
        final String pkg = item.getString("packageName");
        if (pkg == null || pkg.isEmpty()) {
            processNext(activity, list, index + 1, result);
            return;
        }
        final JSONObject config = toInnerConfig(item);
        final String keyStr = RxEncryptTool.encryptMD5ToString(config.toJSONString());
        config.put("keyStr", keyStr);
        final String fPkg = pkg;

        if (jsonCfg.getCfgByKey(keyStr) != null) {
            if (result.overwriteAll) {
                jsonCfg.delConfig(fPkg, keyStr);
                doAdd(activity, list, index, result, config, fPkg, keyStr);
            } else if (result.skipAll) {
                result.duplicate++;
                processNext(activity, list, index + 1, result);
            } else {
                showIdenticalDialog(activity, list, index, result, config, fPkg, keyStr);
            }
            return;
        }

        final List<JSONObject> samePkg = getCfgByPkg(fPkg);
        if (samePkg.isEmpty()) {
            doAdd(activity, list, index, result, config, fPkg, keyStr);
        } else if (samePkg.size() == 1) {
            showSingleDialog(activity, list, index, result, config, fPkg, keyStr, samePkg);
        } else {
            showMultiDialog(activity, list, index, result, config, fPkg, keyStr, samePkg);
        }
    }

    private static void doAdd(final Activity activity, final JSONArray list, final int index, final ImportResult result, final JSONObject config, final String pkg, final String keyStr) {
        if (jsonCfg.addCfg(pkg, true, false, keyStr, config, false)) {
            result.success++;
        } else {
            result.duplicate++;
        }
        processNext(activity, list, index + 1, result);
    }

    private static void showResult(Activity activity, ImportResult result) {
        if (result.success > 0) {
            if (result.duplicate > 0) {
                RxToast.success("成功导入" + result.success + "个配置，" + result.duplicate + "个已存在");
            } else {
                RxToast.success("成功导入" + result.success + "个配置");
            }
        } else if (result.duplicate > 0) {
            RxToast.warning("已存在" + result.duplicate + "个相同配置");
        } else {
            RxToast.warning("未导入任何配置");
        }
        if (result.onDone != null) {
            result.onDone.run();
        }
    }

    private static void showIdenticalDialog(final Activity activity, final JSONArray list, final int index, final ImportResult result, final JSONObject config, final String fPkg, final String keyStr) {
        new QMUIDialog.MenuDialogBuilder(activity)
                .setTitle("已存在相同配置，如何处理？\n应用：" + fPkg)
                .setSkinManager(QMUISkinManager.defaultInstance(activity))
                .addItem("跳过当前", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        result.duplicate++;
                        processNext(activity, list, index + 1, result);
                    }
                })
                .addItem("覆盖当前", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        jsonCfg.delConfig(fPkg, keyStr);
                        doAdd(activity, list, index, result, config, fPkg, keyStr);
                    }
                })
                .addItem("跳过全部", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        result.skipAll = true;
                        result.duplicate++;
                        processNext(activity, list, index + 1, result);
                    }
                })
                .addItem("覆盖全部", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        result.overwriteAll = true;
                        jsonCfg.delConfig(fPkg, keyStr);
                        doAdd(activity, list, index, result, config, fPkg, keyStr);
                    }
                })
                .create().show();
    }

    private static void showSingleDialog(final Activity activity, final JSONArray list, final int index, final ImportResult result, final JSONObject config, final String fPkg, final String keyStr, final List<JSONObject> samePkg) {
        new QMUIDialog.MessageDialogBuilder(activity)
                .setTitle("重复配置")
                .setMessage("该软件已存在 1 个配置，是否覆盖？\n应用：" + fPkg)
                .setSkinManager(QMUISkinManager.defaultInstance(activity))
                .addAction("跳过", new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int which) {
                        dialog.dismiss();
                        result.duplicate++;
                        processNext(activity, list, index + 1, result);
                    }
                })
                .addAction(0, "覆盖", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int which) {
                        dialog.dismiss();
                        jsonCfg.delConfig(fPkg, samePkg.get(0).getString("KeyStr"));
                        doAdd(activity, list, index, result, config, fPkg, keyStr);
                    }
                })
                .create().show();
    }

    private static void showMultiDialog(final Activity activity, final JSONArray list, final int index, final ImportResult result, final JSONObject config, final String fPkg, final String keyStr, final List<JSONObject> samePkg) {
        new QMUIDialog.MenuDialogBuilder(activity)
                .setTitle("该软件已存在 " + samePkg.size() + " 个配置，请选择\n应用：" + fPkg)
                .setSkinManager(QMUISkinManager.defaultInstance(activity))
                .addItem("跳过当前", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        result.duplicate++;
                        processNext(activity, list, index + 1, result);
                    }
                })
                .addItem("覆盖当前", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        jsonCfg.delConfig(fPkg, samePkg.get(0).getString("KeyStr"));
                        doAdd(activity, list, index, result, config, fPkg, keyStr);
                    }
                })
                .addItem("跳过全部", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        result.duplicate++;
                        processNext(activity, list, index + 1, result);
                    }
                })
                .addItem("覆盖全部", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        for (JSONObject c : samePkg) {
                            jsonCfg.delConfig(fPkg, c.getString("KeyStr"));
                        }
                        doAdd(activity, list, index, result, config, fPkg, keyStr);
                    }
                })
                .create().show();
    }

    private static List<JSONObject> getCfgByPkg(String pkg) {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray all = jsonCfg.getAllCfg();
            if (all == null) {
                return list;
            }
            for (Object o : all) {
                if (o instanceof JSONObject) {
                    JSONObject cfg = (JSONObject) o;
                    if (pkg.equals(cfg.getString("pkg"))) {
                        list.add(cfg);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    private static JSONObject toInnerConfig(JSONObject item) {
        JSONObject config = new JSONObject(true);
        config.put("appPkg", item.getString("packageName"));
        config.put("appName", item.getString("appName"));
        config.put("appVer", item.getString("versionName"));
        config.put("detail", item.getString("description"));
        config.put("author", item.containsKey("author") && item.getString("author") != null ? item.getString("author") : "导入");
        config.put("time", RxTimeTool.getCurTimeString());
        if (item.containsKey("id")) {
            config.put("cfgId", item.getString("id"));
        }
        JSONArray hooks = new JSONArray();
        Object configs = item.get("configs");
        String configsStr = null;
        if (configs instanceof String) {
            configsStr = (String) configs;
        } else if (configs instanceof JSONArray) {
            configsStr = ((JSONArray) configs).toJSONString();
        } else if (configs instanceof JSONObject) {
            configsStr = ((JSONObject) configs).toJSONString();
        }
        if (configsStr != null && !configsStr.isEmpty()) {
            try {
                Object cobj = JSON.parse(configsStr);
                JSONArray hookArr = cobj instanceof JSONArray ? (JSONArray) cobj : new JSONArray();
                for (Object ho : hookArr) {
                    JSONObject hc = JSONObject.parseObject(ho.toString());
                    JSONObject hook = new JSONObject(true);
                    hook.put("hookType", "setRet");
                    hook.put("className", hc.getString("className"));
                    hook.put("methodName", hc.getString("methodName"));
                    hook.put("paramsName", new JSONArray());
                    String returnData = hc.containsKey("resultValues") ? hc.getString("resultValues") : hc.getString("resultData");
                    hook.put("returnData", returnData);
                    hook.put("returnType", guessType(returnData));
                    hooks.add(hook);
                }
            } catch (Throwable ignored) {
            }
        }
        config.put("hooks", hooks);
        return config;
    }

    public static JSONObject exportConfig(JSONObject config, JSONArray hooks) {
        JSONObject share = new JSONObject(true);
        share.put("packageName", config.getString("appPkg"));
        share.put("appName", config.getString("appName"));
        share.put("versionName", config.getString("appVer"));
        share.put("description", config.getString("detail"));
        share.put("author", config.containsKey("author") && config.getString("author") != null ? config.getString("author") : "导入");
        if (config.containsKey("cfgId") && config.getString("cfgId") != null) {
            share.put("id", config.getString("cfgId"));
        }
        JSONArray configs = new JSONArray();
        if (hooks != null) {
            for (Object o : hooks) {
                JSONObject h = JSONObject.parseObject(o.toString());
                JSONObject c = new JSONObject(true);
                c.put("className", h.getString("className"));
                c.put("methodName", h.getString("methodName"));
                c.put("resultValues", h.containsKey("returnData") ? h.getString("returnData") : "");
                configs.add(c);
            }
        }
        share.put("configs", configs);
        return share;
    }

    private static String guessType(String data) {
        if (data == null) {
            return "java.lang.String";
        }
        String t = data.trim();
        if ("true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t)) {
            return "Z";
        }
        if ("null".equalsIgnoreCase(t)) {
            return "java.lang.String";
        }
        try {
            long l = Long.parseLong(t);
            return (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) ? "I" : "J";
        } catch (Throwable ignored) {
        }
        try {
            Double.parseDouble(t);
            return "D";
        } catch (Throwable ignored) {
        }
        return "java.lang.String";
    }

    public static class ImportResult {
        public int success;
        public int duplicate;
        public boolean skipAll;
        public boolean overwriteAll;
        public Runnable onDone;
    }
}
