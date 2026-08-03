package cn.mhook.mhook.xposed.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.alibaba.fastjson.JSONObject;
import com.tamsiree.rxkit.RxActivityTool;

import cn.mhook.mhook.xposed.utils.H;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Dialog {
    public static void init(){
        if (H.pkg.equals("com.qmuiteam.qmuidemo")){

            XposedBridge.hookAllMethods(Activity.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    Context c =(Context) param.thisObject;
                    if (c!=null){
                        H.aContext = c;
                        startHook();
                    }
                }
            });
        }
    }

    private static void startHook(){
        XposedBridge.hookAllMethods(android.app.Dialog.class, "show", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                int viewId = 0;
                if (param.thisObject instanceof View){
                   View view = (View)param.thisObject;
                   viewId = view.getId();
                }

                String mPackageName="cn.mhook.mhook";
                String mActivityName="cn.mhook.activity.DialogActivity";
                JSONObject data = new JSONObject();
                data.put("pkg",H.pkg);
                data.put("viewId",viewId);
                Activity activity = (Activity)H.aContext;
                Bundle bundle=new Bundle();
                bundle.putString("data",data.toJSONString());
                RxActivityTool.launchActivity(activity,mPackageName, mActivityName,bundle);

            }
        });
    }
}
