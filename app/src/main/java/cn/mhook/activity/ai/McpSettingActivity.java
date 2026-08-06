package cn.mhook.activity.ai;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;
import com.tamsiree.rxkit.view.RxToast;

import java.util.regex.Pattern;

import cn.mhook.BaseActivity;
import cn.mhook.ai.McpClient;
import cn.mhook.ai.McpManager;
import cn.mhook.ai.McpSetting;
import cn.mhook.mhook.R;

public class McpSettingActivity extends BaseActivity {

    QMUIGroupListView mGroupListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mcp_setting);
        mGroupListView = findViewById(R.id.groupListView);
        refresh();
    }

    private void refresh() {
        mGroupListView.removeAllViews();
        final JSONArray servers = McpSetting.getServers(this);

        QMUIGroupListView.Section section = QMUIGroupListView.newSection(this).setTitle("服务器列表");
        for (Object o : servers) {
            final JSONObject s = (JSONObject) o;
            section.addItemView(getServerItem(servers, s), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleEnable(servers, s);
                }
            });
        }
        section.addTo(mGroupListView);

        QMUIGroupListView.Section ops = QMUIGroupListView.newSection(this).setTitle("操作");
        ops.addItemView(getOpItem("添加自定义服务器", "新增任意 MCP 后端，支持自定义地址与 Token"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addServer();
            }
        });
        ops.addItemView(getOpItem("一键探测并启用", "自动匹配 MT/玄星逆核/ProxyPin 候选端口"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                probeAll();
            }
        });
        ops.addItemView(getOpItem("使用说明", "需先在 MT管理器/玄星逆核/ProxyPin 内启动 MCP 服务"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RxToast.info("请先在对应 App 内启动 MCP 服务并保持运行");
            }
        });
        ops.addTo(mGroupListView);
    }

    private QMUICommonListItemView getServerItem(final JSONArray servers, final JSONObject s) {
        String title = labelOf(s);
        boolean enabled = s.getBooleanValue("enable");
        String detail = (s.getString("url") == null ? "" : s.getString("url"))
                + "\n" + (enabled ? "已启用" : "未启用") + "（点击切换，长按管理）";
        final QMUICommonListItemView item = mGroupListView.createItemView(
                null, title, detail,
                QMUICommonListItemView.VERTICAL,
                QMUICommonListItemView.ACCESSORY_TYPE_NONE,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        item.getDetailTextView().setTextColor(getResources().getColor(
                enabled ? R.color.green : R.color.qmui_config_color_75_pure_black));
        applyItemSpacing(item);
        item.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showServerMenu(servers, s);
                return true;
            }
        });
        return item;
    }

    private QMUICommonListItemView getOpItem(String title, String detail) {
        QMUICommonListItemView item = mGroupListView.createItemView(
                null, title, detail,
                QMUICommonListItemView.VERTICAL,
                QMUICommonListItemView.ACCESSORY_TYPE_NONE,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        applyItemSpacing(item);
        return item;
    }

    private void applyItemSpacing(QMUICommonListItemView item) {
        TextView detailView = item.getDetailTextView();
        detailView.setLineSpacing(0f, 1.0f);
        ConstraintLayout.LayoutParams dlp =
                (ConstraintLayout.LayoutParams) detailView.getLayoutParams();
        dlp.topMargin = (int) (getResources().getDisplayMetrics().density * 2);
        detailView.setLayoutParams(dlp);
        int density = (int) getResources().getDisplayMetrics().density;
        item.setPadding(item.getPaddingLeft(),
                item.getPaddingTop() + density * 10,
                item.getPaddingRight(),
                item.getPaddingBottom() + density * 12);
    }

    private String labelOf(JSONObject s) {
        String label = s.getString("label");
        String name = s.getString("name");
        return (label == null || label.isEmpty()) ? (name == null ? "" : name) : label;
    }

    private void toggleEnable(final JSONArray servers, final JSONObject s) {
        boolean en = !s.getBooleanValue("enable");
        s.put("enable", en);
        McpSetting.saveServers(this, servers);
        McpManager.resetClients();
        refresh();
        RxToast.info((en ? "已启用 " : "已禁用 ") + labelOf(s));
    }

    private void showServerMenu(final JSONArray servers, final JSONObject s) {
        QMUIDialog.MenuDialogBuilder builder = new QMUIDialog.MenuDialogBuilder(this)
                .setTitle(labelOf(s))
                .setSkinManager(QMUISkinManager.defaultInstance(this));
        builder.addItem("编辑", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                editServer(servers, s);
            }
        });
        builder.addItem("测试连接", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                testServer(servers, s);
            }
        });
        builder.addItem("删除", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                deleteServer(servers, s);
            }
        });
        builder.create().show();
    }

    private void editServer(final JSONArray servers, final JSONObject s) {
        final JSONObject draft = new JSONObject(true);
        draft.put("name", s.getString("name"));
        draft.put("url", s.getString("url"));
        draft.put("token", s.getString("token"));
        promptField(0, draft, s);
    }

    private void addServer() {
        final JSONObject draft = new JSONObject(true);
        draft.put("enable", false);
        promptField(0, draft, null);
    }

    private void promptField(final int step, final JSONObject draft, final JSONObject target) {
        switch (step) {
            case 0:
                showInput("服务器名称", "字母/数字/下划线，用于工具前缀",
                        target == null ? "" : target.getString("name"), new InputCallback() {
                            @Override
                            public void onResult(String v) {
                                String val = v.trim();
                                if (val.isEmpty()) {
                                    return;
                                }
                                if (!Pattern.matches("[A-Za-z0-9_]+", val)) {
                                    RxToast.error("名称只能包含字母/数字/下划线");
                                    promptField(0, draft, target);
                                    return;
                                }
                                draft.put("name", val);
                                promptField(1, draft, target);
                            }
                        });
                break;
            case 1:
                showInput("服务器地址", "如 http://127.0.0.1:8000/mcp",
                        target == null ? "" : target.getString("url"), new InputCallback() {
                            @Override
                            public void onResult(String v) {
                                String val = v.trim();
                                if (val.isEmpty()) {
                                    RxToast.error("地址不能为空");
                                    promptField(1, draft, target);
                                    return;
                                }
                                draft.put("url", val);
                                promptField(2, draft, target);
                            }
                        });
                break;
            case 2:
                showInput("Token（可留空）", "Bearer 令牌",
                        target == null ? "" : target.getString("token"), new InputCallback() {
                            @Override
                            public void onResult(String v) {
                                draft.put("token", v.trim());
                                finishAdd(draft, target);
                            }
                        });
                break;
        }
    }

    private void finishAdd(final JSONObject draft, final JSONObject target) {
        JSONArray servers = McpSetting.getServers(this);
        if (target != null) {
            String oldName = target.getString("name");
            String newName = draft.getString("name");
            JSONObject stored = findInArray(servers, oldName);
            if (stored == null) {
                RxToast.error("目标服务器不存在，请返回重试");
                return;
            }
            if (!newName.equals(oldName) && findInArray(servers, newName) != null) {
                RxToast.error("已存在同名服务器");
                return;
            }
            stored.put("name", newName);
            stored.put("url", draft.getString("url"));
            stored.put("token", draft.getString("token"));
            if (stored.getString("label") == null || stored.getString("label").isEmpty()) {
                stored.put("label", newName);
            }
        } else {
            if (McpSetting.findServer(this, draft.getString("name")) != null) {
                RxToast.error("已存在同名服务器");
                return;
            }
            if (draft.getString("label") == null || draft.getString("label").isEmpty()) {
                draft.put("label", draft.getString("name"));
            }
            servers.add(draft);
        }
        McpSetting.saveServers(this, servers);
        McpManager.resetClients();
        refresh();
        RxToast.success(target != null ? "已保存" : "已添加 " + draft.getString("name"));
    }

    private static JSONObject findInArray(JSONArray arr, String name) {
        for (Object o : arr) {
            JSONObject j = (JSONObject) o;
            if (name != null && name.equals(j.getString("name"))) {
                return j;
            }
        }
        return null;
    }

    private void deleteServer(final JSONArray servers, final JSONObject s) {
        new QMUIDialog.MessageDialogBuilder(this)
                .setTitle("删除服务器")
                .setMessage("确定要删除 " + labelOf(s) + " 吗？")
                .setSkinManager(QMUISkinManager.defaultInstance(this))
                .addAction("取消", new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int index) {
                        dialog.dismiss();
                    }
                })
                .addAction(0, "删除", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int index) {
                        servers.remove(s);
                        McpSetting.saveServers(McpSettingActivity.this, servers);
                        McpManager.resetClients();
                        refresh();
                        dialog.dismiss();
                    }
                })
                .create().show();
    }

    private void testServer(final JSONArray servers, final JSONObject s) {
        final String name = s.getString("name");
        final String url = s.getString("url");
        final String token = s.getString("token");
        RxToast.info("正在测试 " + name + " …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String msg = doTest(name, url, token);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (msg.startsWith("连接成功")) {
                            RxToast.success(msg);
                        } else {
                            RxToast.error(msg);
                        }
                    }
                });
            }
        }).start();
    }

    private String doTest(String name, String url, String token) {
        try {
            McpClient c = McpManager.getClient(name, url, token);
            int count = c.listTools().size();
            return "连接成功，" + name + " 提供 " + count + " 个工具";
        } catch (Throwable t) {
            McpManager.invalidate(name);
            return "连接失败：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
    }

    private void probeAll() {
        RxToast.info("探测中…");
        final JSONArray servers = McpSetting.getServers(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                McpManager.probeAndEnable(McpSettingActivity.this, servers);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        McpSetting.saveServers(McpSettingActivity.this, servers);
                        McpManager.resetClients();
                        refresh();
                        RxToast.success("探测完成，可用后端已启用");
                    }
                });
            }
        }).start();
    }

    private void showInput(String title, String placeholder, String def, final InputCallback cb) {
        final QMUIDialog.EditTextDialogBuilder builder = new QMUIDialog.EditTextDialogBuilder(this);
        builder.setTitle(title)
                .setSkinManager(QMUISkinManager.defaultInstance(this))
                .setPlaceholder(placeholder)
                .setDefaultText(def == null ? "" : def);
        if (title != null && title.contains("Token")) {
            builder.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        builder.addAction("取消", new QMUIDialogAction.ActionListener() {
            @Override
            public void onClick(QMUIDialog dialog, int index) {
                dialog.dismiss();
            }
        });
        builder.addAction("确定", new QMUIDialogAction.ActionListener() {
            @Override
            public void onClick(QMUIDialog dialog, int index) {
                CharSequence t = builder.getEditText().getText();
                dialog.dismiss();
                cb.onResult(t == null ? "" : t.toString());
            }
        });
        builder.create().show();
    }

    private interface InputCallback {
        void onResult(String v);
    }
}
