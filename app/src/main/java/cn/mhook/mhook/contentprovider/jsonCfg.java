package cn.mhook.mhook.contentprovider;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxFileTool;
import com.tamsiree.rxkit.view.RxToast;

import cn.mhook.msu.su;

import static cn.mhook.mData.jsonDir;
import static cn.mhook.msu.su.set777;

public class jsonCfg {

    public static Context context;

    public static Boolean addCfg(String pkg,Boolean enable,Boolean canUse,String keyStr,JSONObject config,Boolean useSdcard){
        if (getCfgByKey(keyStr)==null){
            Uri bookUri = Uri.parse("content://mHookData/jsonCfg");
            ContentValues values = new ContentValues();
            values.put("pkg",pkg);
            values.put("enable",false);
            values.put("config",config.toJSONString());
            values.put("canUse",canUse);
            values.put("keyStr", keyStr);
            values.put("useSdcard",useSdcard);
            context.getContentResolver().insert(bookUri,values);
            return true;
        }else {
            return false;
        }
    }

    public static JSONObject getCfgByKey(String keyStr){
        Uri bookUri = Uri.parse("content://mHookData/jsonCfg");
        Cursor bookCursor = context.getContentResolver().query(bookUri, new String[]{"_id", "keyStr","config"}, "keyStr=?", new String[]{keyStr}, null);
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


    public static JSONArray getAllCfg(){
        JSONArray ret = new JSONArray();
        Uri bookUri = Uri.parse("content://mHookData/jsonCfg");
        Cursor bookCursor = context.getContentResolver().query(bookUri, new String[]{"_id", "pkg", "config", "KeyStr","enable","canUse","useSdcard"}, "", null, null);
        if (bookCursor != null) {
            while (bookCursor.moveToNext()) {
                JSONObject j = new JSONObject(true);
                String pkg = bookCursor.getString(1);
                String KeyStr = bookCursor.getString(3);
                j.put("KeyStr",KeyStr);
                j.put("pkg",pkg);
                j.put("config",JSONObject.parseObject(bookCursor.getString(2)));
                j.put("enable",bookCursor.getInt(4));
                j.put("canUse",bookCursor.getInt(5));
                j.put("useSdcard",bookCursor.getInt(6));
                ret.add(j);
                if (j.getBoolean("enable")){
                    RxFileTool.writeFileFromString(jsonDir+pkg+"/jsonCfg/"+KeyStr+".json",j.toJSONString(),false);
                }else {
                    RxFileTool.deleteFile(jsonDir+pkg+"/jsonCfg/"+KeyStr+".json");
                }
                set777();
            }
        }
        if (bookCursor != null) {
            bookCursor.close();
        }

        return ret;
    }

    public static JSONArray getAllCfgByPkg(String pkg){
        JSONArray ret = new JSONArray();
        Uri bookUri = Uri.parse("content://mHookData/jsonCfg");
        Cursor bookCursor = context.getContentResolver().query(bookUri, new String[]{"_id", "pkg", "config", "KeyStr","enable"}, "pkg=?", new String[]{pkg}, null);
        if (bookCursor != null) {
            while (bookCursor.moveToNext()) {
                JSONObject j = new JSONObject(true);
                if (bookCursor.getInt(4)<1){
                    continue;
                }
                j.put("pkg",bookCursor.getString(1));
                j.put("config",JSONObject.parseObject(bookCursor.getString(2)));
                ret.add(j);
            }
        }
        if (bookCursor != null) {
            bookCursor.close();
        }
        return ret;
    }

    public static void setEnable(Boolean enable,String KeyStr){
        Uri bookUri = Uri.parse("content://mHookData/jsonCfg");
        ContentValues values = new ContentValues();
        values.put("enable",enable);
        context.getContentResolver().update(bookUri,values,"KeyStr=?",new String[]{KeyStr});
        getAllCfg();
    }

    public static void delConfig(String pkg,String key){
        Uri bookUri = Uri.parse("content://mHookData/jsonCfg");
        RxFileTool.deleteFile(jsonDir+pkg+"/jsonCfg/"+key+".json");
        context.getContentResolver().delete(bookUri,"KeyStr=?",new String[]{key});
    }

}

