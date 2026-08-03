package cn.mhook.mhook.xposed;

import android.content.Context;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import cn.mhook.mhook.contentprovider.PrintData;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public  class H {
    public static String pkg;
    public static XC_LoadPackage.LoadPackageParam loadPackageParam;
    public static JSONArray waitSend;
    public static Context context;
    public static Context aContext;

    public static void p(String msg){
        XposedBridge.log("test---"+msg);
        if (waitSend==null){
            waitSend = new JSONArray();
        }
        if (context==null&&aContext==null){
            waitSend.add(msg);
        }else if (context!=null){
            if (waitSend.size()>0){
                for (Object o:waitSend){
                    PrintData.putData(context,o.toString());
                }
                waitSend.clear();
                PrintData.putData(context,msg);
            }else {
                PrintData.putData(context,msg);
            }
        }else if (aContext!=null){
            if (waitSend.size()>0){
                for (Object o:waitSend){
                    PrintData.putData(aContext,o.toString());
                }
                waitSend.clear();
                PrintData.putData(aContext,msg);
            }else {
                PrintData.putData(aContext,msg);
            }
        }
    }

    public static String msg(String type,Object msg,Object other){
        JSONObject ret = new JSONObject();
        ret.put("type",type);
        ret.put("msg",msg);
        ret.put("other",other);
        return ret.toJSONString();
    }
}
