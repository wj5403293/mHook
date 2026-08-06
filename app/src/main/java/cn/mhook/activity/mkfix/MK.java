package cn.mhook.activity.mkfix;

import com.alibaba.fastjson.JSONObject;
import com.tamsiree.rxkit.RxFileTool;

import static cn.mhook.mData.mDir;
import static cn.mhook.msu.su.set777;

public class MK {
    public static int getCheck(String pkg){
        String path = mDir+pkg+"/fix/config.json";
        if (RxFileTool.fileExists(path)){
            JSONObject cfg = JSONObject.parseObject(RxFileTool.readFile2String(path,"utf-8"));
            if (cfg!=null&&!cfg.isEmpty()&&cfg.containsKey("fix")){
                int ret = cfg.getIntValue("fix");
                return ret;
            }
            return 2;
        }else {
            return 2;
        }
    }

    public static void setCheck(String pkg,int s){
        String path = mDir+pkg+"/fix/config.json";
        JSONObject set = new JSONObject();
        set.put("fix",s);
        RxFileTool.writeFileFromString(path,set.toJSONString(),false);
        set777();
    }
}
