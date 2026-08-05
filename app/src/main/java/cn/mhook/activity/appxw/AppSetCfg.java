package cn.mhook.activity.appxw;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import com.alibaba.fastjson.JSONObject;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.view.RxToast;
import cn.mhook.BaseActivity;
import cn.mhook.floatprint.FloatActivity;
import cn.mhook.mhook.R;
import static cn.mhook.mhook.contentprovider.appCfg.setAppCfg;

public class AppSetCfg extends BaseActivity {

    private Handler handler;
    QMUIGroupListView mGroupListView;
    String pkg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xw_set);
        handler = new Handler();
        mGroupListView = findViewById(R.id.groupListView);
        pkg = getIntent().getStringExtra("pkg");
        new Thread(new Runnable(){
            @Override
            public void run(){
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initView();
                    }
                }, 0);
            }
        }).start();
    }

    private void initView(){
        QMUICommonListItemView appName = mGroupListView.createItemView("应用 (点击启动)");
        appName.setDetailText(RxAppTool.getAppName(this,pkg));
        appName.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_NONE);
        QMUIGroupListView.newSection(this)
                .setTitle("基本")
                .addItemView(appName, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (RxAppTool.isInstallApp(AppSetCfg.this,pkg)){
                            new FloatActivity(AppSetCfg.this,AppSetCfg.this);
                            if (PermissionUtils.checkPermission(AppSetCfg.this)){
                                RxAppTool.launchApp(AppSetCfg.this,pkg);
                            }
                        }else {
                            RxToast.error("未安装该应用");
                        }
                    }
                })
                .addItemView(getItem("总开关","appCfgEnable"),getOnClick())
                .addTo(mGroupListView);
        QMUIGroupListView.newSection(this)
                .setTitle("UI")
                .addItemView(getItem("对话框","dialog"),getOnClick())
                .addItemView(getItem("Toast","toast"),getOnClick())
                .addItemView(getItem("弹窗","show_view"),getOnClick())
                .addItemView(getItem("界面跳转","activity_goto"),getOnClick())
                .addItemView(getItem("界面关闭","activity_finish"),getOnClick())
                .addItemView(getItem("点击事件","button"),getOnClick())
                .addTo(mGroupListView);
        QMUIGroupListView.newSection(this)
                .setTitle("数据")
                .addItemView(getItem("访问存储操作","file"),getOnClick())
                .addItemView(getItem("JSON添加","putJson"),getOnClick())/*
                .addItemView(getItem("SharedPreference操作","sp"),getOnClick())
                .addItemView(getItem("SQLite读取操作","sql_read"),getOnClick())
                .addItemView(getItem("SQLite写入操作","sql_write"),getOnClick())
                .addItemView(getItem("SQLite删除操作","sql_del"),getOnClick())
                .addItemView(getItem("SQLite更新操作","sql_update"),getOnClick())*/
                .addTo(mGroupListView);
                /*
        QMUIGroupListView.newSection(this)
                .setTitle("敏感")
                .addItemView(getItem("读取剪切板","read_clip"),getOnClick())
                .addItemView(getItem("写入剪切板","write_clip"),getOnClick())
                .addItemView(getItem("获取手机信息","read_phone_info"),getOnClick())
                .addItemView(getItem("获取位置信息","read_pos"),getOnClick())
                .addItemView(getItem("读取短信","read_sms"),getOnClick())
                .addItemView(getItem("发送短信","send_sms"),getOnClick())
                .addTo(mGroupListView);*/
        QMUIGroupListView.newSection(this)
                .setTitle("网络")
                .addItemView(getItem("代理检测及屏蔽","cProperty"),getOnClick())
               /* .addItemView(getItem("创建VPN","new_vpn"),getOnClick())
                .addItemView(getItem("Okhttp3","okhttp3"),getOnClick())
                .addItemView(getItem("UDP发送","send_udp"),getOnClick())
                .addItemView(getItem("UDP监听","bind_udp"),getOnClick())
                .addItemView(getItem("TCP发送","send_tcp"),getOnClick())
                .addItemView(getItem("TCP监听","bind_tcp"),getOnClick())
                .addItemView(getItem("WebView访问","webview"),getOnClick())*/
                .addTo(mGroupListView);/*
        QMUIGroupListView.newSection(this)
                .setTitle("加解密")
                .addItemView(getItem("常用算法","crypto"),getOnClick())
                .addTo(mGroupListView);
        QMUIGroupListView.newSection(this)
                .setTitle("Xposed相关")
                .addItemView(getItem("被hook检测", "beHook"),getHookClick())
                .addTo(mGroupListView);*/
    }

    private void setHookEnable(String key,Boolean enable){

    }

    private View.OnClickListener getHookClick(){
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                QMUICommonListItemView q =  (QMUICommonListItemView)v;
                setAppCfg(pkg,q.getTag().toString(),!getEnable(q.getTag().toString()));
                q.setDetailText(getEnable(q.getTag().toString())?"已开启":"未开启");
                q.getDetailTextView().setTextColor(getResources().getColor(getEnable(q.getTag().toString())?R.color.green:R.color.qmui_config_color_75_pure_black));
                if (getEnable(q.getTag().toString())) {
                    setHookEnable(q.getTag().toString(), true);
                } else {
                    setHookEnable(q.getTag().toString(), false);
                }
            }
        };
    }

    private View.OnClickListener getOnClick(){
        return new View.OnClickListener(){

            /**
             * Called when a view has been clicked.
             *
             * @param v The view that was clicked.
             */
            @Override
            public void onClick(View v) {
                QMUICommonListItemView q =  (QMUICommonListItemView)v;
                setAppCfg(pkg,q.getTag().toString(),!getEnable(q.getTag().toString()));
                q.setDetailText(getEnable(q.getTag().toString())?"已开启":"未开启");
                q.getDetailTextView().setTextColor(getResources().getColor(getEnable(q.getTag().toString())?R.color.green:R.color.qmui_config_color_75_pure_black));
            }
        };
    }

    private QMUICommonListItemView getItem(String name,String tag){
        QMUICommonListItemView statusCheck = mGroupListView.createItemView(name);
        statusCheck.setTag(tag);
        statusCheck.setDetailText(getEnable(tag)?"已开启":"未开启");
        statusCheck.getDetailTextView().setTextColor(getResources().getColor(getEnable(tag)?R.color.green:R.color.qmui_config_color_75_pure_black));
        statusCheck.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_NONE);
        return statusCheck;
    }

    public  JSONObject getAppCfg(){
        return cn.mhook.mhook.contentprovider.appCfg.getAppCfg(pkg);
    }

    public  Boolean getEnable(String key){
        if (getAppCfg()!=null&&getAppCfg().containsKey(key)&&getAppCfg().getBoolean(key)){
            return true;
        }
        return false;
    }
}
