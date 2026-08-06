package cn.mhook.activity.hook;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nightonke.boommenu.BoomButtons.HamButton;
import com.nightonke.boommenu.BoomButtons.OnBMClickListener;
import com.nightonke.boommenu.BoomMenuButton;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.tamsiree.rxkit.RxActivityTool;
import com.tamsiree.rxkit.RxClipboardTool;
import com.tamsiree.rxkit.view.RxToast;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.mhook.BaseActivity;
import cn.mhook.activity.ai.AiActivity;
import cn.mhook.activity.editcfg.EditHookActivity;
import cn.mhook.mhook.EventMessage;
import cn.mhook.mhook.R;
import cn.mhook.mhook.contentprovider.jsonCfg;

public class HookActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private List<HookActivityItem> datas = new ArrayList<>();
    private HookActivityAdapter adapter;
    private Handler handler;
    private final Set<String> selectedKeys = new HashSet<>();
    private android.widget.LinearLayout selectBar;
    private android.widget.TextView selectAllText;

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter!=null){
            initList();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hook);
        handler = new Handler();
        EventBus.getDefault().register(this);
        initListView();
    }

    private void initListView(){
        recyclerView = (RecyclerView)findViewById(R.id.config_recycler_view);
        refreshLayout=(SwipeRefreshLayout)findViewById(R.id.refresh_layout);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        refreshLayout.setRefreshing(true);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                initList();
            }
        });
        adapter = new HookActivityAdapter(R.layout.activity_hook_item, datas,HookActivity.this);
        adapter.setEmptyView(LayoutInflater.from(this).inflate(R.layout.view_empty, null));
        adapter.setSelected(selectedKeys);
        adapter.setSelectListener(new HookActivityAdapter.OnSelectListener() {
            @Override
            public void onSelectToggle(HookActivityItem item) {
                toggleSelect(item);
            }

            @Override
            public void onLongPress(HookActivityItem item) {
                enterSelectMode(item);
            }
        });
        recyclerView.setAdapter(adapter);
        initSelectBar();
        initList();
        initBoomMenu();
    }

    private void initSelectBar(){
        selectBar = findViewById(R.id.selectBar);
        selectAllText = findViewById(R.id.selectAll);
        findViewById(R.id.selectAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll();
            }
        });
        findViewById(R.id.shareSelected).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSelected();
            }
        });
        findViewById(R.id.deleteSelected).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelected();
            }
        });
        findViewById(R.id.exitSelect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSelectMode();
            }
        });
    }

    private void enterSelectMode(HookActivityItem item){
        selectedKeys.clear();
        selectedKeys.add(item.getCfgKey());
        adapter.setSelectMode(true);
        selectBar.setVisibility(View.VISIBLE);
        updateSelectAllText();
    }

    private void toggleSelect(HookActivityItem item){
        if (!selectedKeys.remove(item.getCfgKey())){
            selectedKeys.add(item.getCfgKey());
        }
        adapter.setSelectMode(adapter.isSelectMode());
        updateSelectAllText();
    }

    private void selectAll(){
        if (!datas.isEmpty() && selectedKeys.size() == datas.size()){
            selectedKeys.clear();
        }else {
            selectedKeys.clear();
            for (HookActivityItem d : datas){
                selectedKeys.add(d.getCfgKey());
            }
        }
        adapter.setSelectMode(adapter.isSelectMode());
        updateSelectAllText();
    }

    private List<HookActivityItem> getSelectedItems(){
        List<HookActivityItem> list = new ArrayList<>();
        for (HookActivityItem d : datas){
            if (selectedKeys.contains(d.getCfgKey())){
                list.add(d);
            }
        }
        return list;
    }

    private void updateSelectAllText(){
        boolean all = !datas.isEmpty() && selectedKeys.size() == datas.size();
        selectAllText.setText(all ? "取消全选" : "全选");
    }

    private void shareSelected(){
        List<HookActivityItem> sel = getSelectedItems();
        if (sel.isEmpty()){
            RxToast.warning("请先勾选配置");
            return;
        }
        JSONArray arr = new JSONArray();
        for (HookActivityItem item : sel){
            try {
                JSONObject cfg = jsonCfg.getCfgByKey(item.getCfgKey());
                if (cfg != null){
                    arr.add(HookImport.exportConfig(cfg, cfg.getJSONArray("hooks")));
                }
            }catch (Throwable ignored){
            }
        }
        if (arr.isEmpty()){
            RxToast.error("分享失败");
            return;
        }
        RxClipboardTool.copyText(this, arr.toJSONString());
        RxToast.success("已复制 " + arr.size() + " 个配置到剪贴板");
        exitSelectMode();
    }

    private void deleteSelected(){
        final List<HookActivityItem> sel = getSelectedItems();
        if (sel.isEmpty()){
            RxToast.warning("请先勾选配置");
            return;
        }
        new QMUIDialog.MessageDialogBuilder(HookActivity.this)
                .setTitle("删除")
                .setMessage("确定要删除选中的 " + sel.size() + " 个配置吗？")
                .setSkinManager(QMUISkinManager.defaultInstance(HookActivity.this))
                .addAction("取消", new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int index) {
                        dialog.dismiss();
                    }
                })
                .addAction(0, "删除", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int index) {
                        for (HookActivityItem item : sel){
                            jsonCfg.delConfig(item.getPkg(), item.getCfgKey());
                        }
                        dialog.dismiss();
                        exitSelectMode();
                        initList();
                    }
                })
                .create().show();
    }

    private void exitSelectMode(){
        selectedKeys.clear();
        adapter.setSelectMode(false);
        selectBar.setVisibility(View.GONE);
    }

    private void initBoomMenu(){
        BoomMenuButton bmb = findViewById(R.id.bmb);
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("添加配置")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        RxActivityTool.skipActivity(HookActivity.this, EditHookActivity.class);
                    }
                })
                .subNormalText("添加配置文件"));
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("剪贴板导入")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        importFromClipboard();
                    }
                })
                .subNormalText("从剪贴板导入Hook配置"));
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("网络导入")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        importFromNetwork();
                    }
                })
                .subNormalText("从链接导入Hook配置"));
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("AI 分析")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        RxActivityTool.skipActivity(HookActivity.this, AiActivity.class);
                    }
                })
                .subNormalText("AI生成Hook配置/热修复包"));
    }

    private void importFromClipboard(){
        CharSequence text = RxClipboardTool.getText(this);
        if (text == null || text.toString().trim().isEmpty()){
            RxToast.warning("剪贴板内容为空");
            return;
        }
        HookImport.importFromJson(HookActivity.this, text.toString(), new Runnable() {
            @Override
            public void run() {
                initList();
            }
        });
    }

    private void importFromNetwork(){
        final QMUIDialog.EditTextDialogBuilder builder = new QMUIDialog.EditTextDialogBuilder(HookActivity.this);
        builder.setTitle("网络导入")
                .setPlaceholder("输入Hook配置分享链接")
                .setInputType(InputType.TYPE_CLASS_TEXT)
                .addAction("取消", new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int index) {
                        dialog.dismiss();
                    }
                })
                .addAction(0, "导入", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                    @Override
                    public void onClick(QMUIDialog dialog, int index) {
                        CharSequence text = builder.getEditText().getText();
                        dialog.dismiss();
                        if (text == null || text.toString().trim().isEmpty()){
                            RxToast.warning("链接不能为空");
                            return;
                        }
                        importByNetwork(text.toString().trim());
                    }
                })
                .create().show();
    }

    private void importByNetwork(final String url){
        RxToast.info("正在从网络导入...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String json = download(url);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            HookImport.importFromJson(HookActivity.this, json, new Runnable() {
                                @Override
                                public void run() {
                                    initList();
                                }
                            });
                        }
                    });
                }catch (final Throwable e){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            RxToast.error("网络导入失败："+e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private String download(String urlStr) throws Throwable {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL){
            throw new Exception("HTTP "+code);
        }
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1){
            bos.write(buf, 0, len);
        }
        is.close();
        conn.disconnect();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private  void initList(){

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (adapter.isSelectMode()){
                            exitSelectMode();
                        }
                        if (datas.size()>0){
                            datas.clear();
                        }
                        JSONArray jsonArray = jsonCfg.getAllCfg();
                        if (jsonArray.size()>0){
                            for (Object o:jsonArray) {
                                JSONObject jsonObject = JSONObject.parseObject(o.toString());
                                JSONObject cfg = jsonObject.getJSONObject("config");
                                datas.add(new HookActivityItem(cfg.getString("appPkg"),
                                        cfg.getString("appName"),
                                        cfg.getString("detail"),
                                        cfg.getString("author"),
                                        cfg.getString("time"),
                                        cfg.getString("appVer"),
                                        cfg.getString("keyStr"),
                                        cfg.containsKey("cfgId")?cfg.getString("cfgId"):"",
                                        jsonObject.getBoolean("canUse"),
                                        jsonObject.getBoolean("enable")));
                            }
                        }

                        adapter.notifyDataSetChanged();
                        refreshLayout.setRefreshing(false);
                    }
                }, 0);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReceiveMsg(final EventMessage message) {
        if (message.getType().equals("sync")){
            refreshLayout.setRefreshing(true);
            initList();
        }
    }
}
