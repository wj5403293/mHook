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





















































































































































