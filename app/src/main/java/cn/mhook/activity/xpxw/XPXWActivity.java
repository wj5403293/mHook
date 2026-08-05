package cn.mhook.activity.xpxw;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chad.library.adapter.base.entity.node.BaseNode;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxFileTool;

import java.util.ArrayList;
import java.util.List;
import cn.mhook.BaseActivity;
import cn.mhook.activity.xpxw.node.FirstNode;
import cn.mhook.activity.xpxw.node.SecondNode;
import cn.mhook.mhook.R;

import static cn.mhook.mData.mDir;

public class XPXWActivity extends BaseActivity {

    private static Handler handler;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private XPXWAdapter adapter = new XPXWAdapter();
    private static String path = mDir+"mHookApp/module.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xp_xw);
        handler = new Handler();
        initListView();
    }

    private void initListView(){
        recyclerView = (RecyclerView) findViewById(R.id.config_recycler_view);
        refreshLayout=(SwipeRefreshLayout)findViewById(R.id.refresh_layout);
        refreshLayout.setEnabled(false);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        initList();
    }

    private void initList(){
        new Thread(new Runnable(){
            @Override
            public void run(){
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(true);
                    }
                }, 0);
                List<BaseNode> l = getEntity();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        adapter.setList(l);
                        adapter.notifyDataSetChanged();
                        refreshLayout.setRefreshing(false);
                    }
                }, 0);
            }
        }).start();
    }


    private List<BaseNode> getEntity() {
        List<RxAppTool.AppInfo> list;
        List<BaseNode> ret = new ArrayList<>();
        PackageManager packageManager =XPXWActivity.this.getPackageManager();
        List<String> libXposedModules = getLibXposedModules(packageManager);
        list = RxAppTool.getAllAppsInfo(XPXWActivity.this);
        for (RxAppTool.AppInfo info:list) {
                PackageInfo packageInfo = null;
                try {
                    packageInfo = packageManager.getPackageInfo(info.getPackageName(), PackageManager.GET_META_DATA);
                    ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                    boolean isLspModule = applicationInfo.metaData!=null&&applicationInfo.metaData.getBoolean("xposedmodule");
                    boolean isLibXposedModule = libXposedModules.contains(info.getPackageName());
                    if (!isLspModule&&!isLibXposedModule){
                        continue;
                    }
                    if (info.getPackageName().equals(getPackageName())) continue;
                } catch (PackageManager.NameNotFoundException e) {
                    continue;
                }catch (Throwable throwable){
                    continue;
                }
            List<BaseNode> secondNodeList = new ArrayList<>();
            if (getData(info.getPackageName())!=null&&getData(info.getPackageName()).containsKey("appList")){
                JSONArray jsonArray = getData(info.getPackageName()).getJSONArray("appList");
                for (Object o:jsonArray){
                    String appPkg = (String)o;
                    SecondNode seNode = new SecondNode(RxAppTool.getAppName(XPXWActivity.this,appPkg),RxAppTool.getAppIcon(XPXWActivity.this,appPkg),appPkg,info.getPackageName());
                    secondNodeList.add(seNode);
                }
            }
            FirstNode entity = new FirstNode(secondNodeList, info.getName(),info.getPackageName(),info.getIcon());
            // 模拟 默认第0个是展开的
           // entity.setExpanded(i == 0);
            ret.add(entity);
       }
        return ret;
    }

    private List<String> getLibXposedModules(PackageManager packageManager){
        List<String> ret = new ArrayList<>();
        try {
            for (android.content.pm.ProviderInfo providerInfo:packageManager.queryContentProviders(null,0,0)){
                if ("io.github.libxposed.service.XposedProvider".equals(providerInfo.name)){
                    ret.add(providerInfo.packageName);
                }
            }
        }catch (Throwable throwable){
        }
        return ret;
    }

    public static JSONObject getData(String xpkg){
        if (RxFileTool.fileExists(path)){
           JSONObject j = JSONObject.parseObject(RxFileTool.readFile2String(path,"utf-8"));
           if (j==null||!j.containsKey(xpkg))return null;
            return j.getJSONObject(xpkg);
        }else {
            return null;
        }
    }

}

/*
public class XPXWActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private Handler handler;
    private List<SelectAppItem> datas = new ArrayList<>();
    private SetectAppAdapter adapter;
    private FloatingSearchView floatingSearchView;
    private String path = mDir+"mHookApp/module.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_xw);
        handler = new Handler();
        initListView();
        initBoomMenu();
    }

    private void initListView(){
        recyclerView = (RecyclerView) findViewById(R.id.config_recycler_view);
        refreshLayout=(SwipeRefreshLayout)findViewById(R.id.refresh_layout);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        refreshLayout.setRefreshing(true);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                initList("");
            }
        });
        initList("");
        adapter = new SetectAppAdapter(R.layout.activity_xw_item, datas);
        adapter.setEmptyView(LayoutInflater.from(this).inflate(R.layout.view_empty, null));
        adapter.addChildClickViewIds(R.id.appInfoItem);
        adapter.addChildLongClickViewIds(R.id.appInfoItem);
        adapter.setOnItemChildClickListener(new OnItemChildClickListener() {
            @Override
            public void onItemChildClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                new FloatActivity(XPXWActivity.this,XPXWActivity.this);
            }
        });
        adapter.setOnItemChildLongClickListener(new OnItemChildLongClickListener() {
            @Override
            public boolean onItemChildLongClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                new QMUIDialog.MessageDialogBuilder(XPXWActivity.this)
                        .setTitle("提示")
                        .setMessage("确定要移除该应用吗？")
                        .setSkinManager(QMUISkinManager.defaultInstance(XPXWActivity.this))
                        .addAction("取消", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                dialog.dismiss();
                            }
                        })
                        .addAction(0, "确定", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                JSONObject j = getXpCfg();
                                j.remove(datas.get(position).getPkg());
                                RxFileTool.writeFileFromString(path,j.toJSONString(),false);
                                initList("");
                                dialog.dismiss();
                            }
                        })
                        .create().show();
                return true;
            }
        });
        recyclerView.setAdapter(adapter);
        floatingSearchView = findViewById(R.id.floating_search_view);
        floatingSearchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {
            @Override
            public void onSearchTextChanged(String oldQuery, String newQuery) {
                initList(newQuery);
            }
        });
    }


    private void initBoomMenu(){
        BoomMenuButton bmb = findViewById(R.id.bmb);
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("添加分析模块")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        Bundle bundle=new Bundle();
                        bundle.putString("appType","xp");
                        RxActivityTool.skipActivityForResult(XPXWActivity.this, SelectActivity.class,bundle,9008);
                    }
                })
                .subNormalText("添加需要分析的Xp模块"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==9008&&resultCode==RESULT_OK){
            String pkg = data.getStringExtra("pkg");
            JSONObject j = getXpCfg();
            if (j.containsKey(pkg)){
                RxToast.warning("该模块已添加");
            }else {
                j.put(pkg,new JSONObject());
                RxFileTool.writeFileFromString(path,j.toJSONString(),false);
                initList("");
            }
        }
    }


    private  void initList(final String query){
        new Thread(new Runnable(){
            @Override
            public void run(){
                if (datas.size()>0){
                    datas.clear();
                }
                JSONObject jsonObject = getXpCfg();
                    for (String pkg:jsonObject.keySet()){
                        if (pkg.contains(query)||RxAppTool.getAppName(XPXWActivity.this,pkg).contains(query)){
                            datas.add(new SelectAppItem(pkg,RxAppTool.getAppVersionName(XPXWActivity.this,pkg), RxAppTool.getAppName(XPXWActivity.this,pkg)));
                        }
                    }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                        refreshLayout.setRefreshing(false);
                    }
                }, 0);
            }
        }).start();
    }

    private JSONObject getXpCfg(){
        if (!RxFileTool.fileExists(path)){
            return new JSONObject();
        }
        String jsonCfg = RxFileTool.readFile2String(path,"utf-8");
        return JSONObject.parseObject(jsonCfg);
    }

}
*/