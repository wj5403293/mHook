package cn.mhook.mhook.xposed.config;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tamsiree.rxkit.RxFileTool;

import cn.mhook.mhook.xposed.utils.H;

import static cn.mhook.mhook.xposed.utils.mHookCfg.mXpCfgDir;
import static cn.mhook.mhook.xposed.utils.mHookCfg.xpCfgDir;

public class XpCfg {
    public static Boolean hasCfg(){
        if(!getAllCfg().isEmpty()){
            return true;
        }
        return false;
    }

    public static JSONArray getAllCfg() {
        JSONArray ret = new JSONArray();
        try {
            if (!RxFileTool.fileExists(mXpCfgDir)) { //无全局模块
               return ret;
            } else {//有全局模块
                String mjson = RxFileTool.readFile2String(mXpCfgDir, "utf-8");
                JSONObject mXpCfg = JSONObject.parseObject(mjson);
                if (mXpCfg==null) return ret;
                for (String key:mXpCfg.keySet()){
                    try {
                        JSONObject cfg = mXpCfg.getJSONObject(key);
                        if (cfg==null){
                            H.p(H.msg("XP模块配置错误","模块配置为空："+key,""));
                            continue;
                        }
                        if (cfg.containsKey("allApp")&&cfg.getBoolean("allApp")&&cfg.containsKey("enable")&&cfg.getBoolean("enable")){
                            ret.add(key);
                            continue;
                        }
                        if (cfg.containsKey("appList")){
                            if (cfg.getJSONArray("appList").contains(H.pkg)){
                                if (cfg.containsKey("enable")&&cfg.getBoolean("enable")) ret.add(key);
                            }
                        }
                    }catch (Throwable throwable){
                        H.p(H.msg("XP模块配置错误","解析模块配置失败："+key+"\n"+throwable.getMessage(),""));
                    }
                }
            }
        }catch (Throwable throwable){
            H.p(H.msg("XP模块配置错误","读取全局配置失败\n"+throwable.getMessage(),""));
        }
        return ret;
    }
}
