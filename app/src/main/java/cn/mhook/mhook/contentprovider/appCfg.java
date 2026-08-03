package cn.mhook.mhook.contentprovider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.alibaba.fastjson.JSONObject;
import com.tamsiree.rxkit.RxFileTool;
import com.tamsiree.rxkit.RxShellTool;

import cn.mhook.msu.su;

import static cn.mhook.mData.jsonDir;
import static cn.mhook.msu.su.set777;

public class appCfg {

    public static Context context;

    public static JSONObject getAppCfg(String pkg){
        Uri bookUri = Uri.parse("content://mHookData/appCfg");
        Cursor bookCursor = context.getContentResolver().query(bookUri, new String[]{"_id", "pkg","config"}, "pkg=?", new String[]{pkg}, null);
        if (bookCursor != null) {
            while (bookCursor.moveToNext()) {
                String config = bookCursor.getString(2);
                return JSONObject.parseObject(config);
            }
        }
        if (bookCursor != null) {
            bookCursor.close();
        }
        return null;
    }

    public static void setAppCfg(String pkg,String key,Object value){
        Uri bookUri = Uri.parse("content://mHookData/appCfg");
        ContentValues values = new ContentValues();
        if (getAppCfg(pkg)==null){
            JSONObject set = new JSONObject(true);
            set.put(key, value);
            values.put("pkg",pkg);
            values.put("config",set.toJSONString());
            context.getContentResolver().insert(bookUri,values);
            RxFileTool.writeFileFromString(jsonDir+pkg+"/Setting.json",set.toJSONString(),false);
        }else {
            JSONObject set = getAppCfg(pkg);
            set.put(key, value);
            values.put("config",set.toJSONString());
            context.getContentResolver().update(bookUri,values,"pkg=?",new String[]{pkg});
            RxFileTool.writeFileFromString(jsonDir+pkg+"/Setting.json",set.toJSONString(),false);
        }
        set777();
    }

}
