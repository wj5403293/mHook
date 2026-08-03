package cn.mhook.mhook.xposed;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import cn.mhook.mhook.xposed.utils.H;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static cn.mhook.mhook.xposed.utils.H.getStackTrace;

public class doJsonHook {
    private ClassLoader classLoader;
    private cn.mhook.mhook.contentprovider.jsonCfg jsonCfg;
    private JSONArray jsonCfgArry;
    public doJsonHook(ClassLoader c){
        classLoader = c;
        initHook();
    }

    private void initHook(){
            jsonCfgArry = Config.getJsonCfgByPatch();
            startHook();
    }

    private void startHook() {
        if (!jsonCfgArry.isEmpty()){
            H.p(H.msg("输出日志","获取到配置文件",""));
            for (Object o:jsonCfgArry) {
                JSONObject config = JSONObject.parseObject(o.toString());
                JSONArray hookList = config.getJSONObject("config").getJSONArray("hooks");
                if (hookList!=null&&hookList.size()>0){
                    for (Object hooks:hookList) {
                        JSONObject cfg = JSONObject.parseObject(hooks.toString());
                        H.p(H.msg("输出日志","HOOK--"+cfg.getString("className")+"--"+cfg.getString("methodName"),cfg));
                        doJsonHook(cfg.getString("className"),cfg.getString("methodName"),cfg.getJSONArray("paramsName"), cfg.getString("returnType"),cfg.getString("returnData"),classLoader);
                    }
                }
            }
        }
    }

    private void doJsonHook(String classes, String method, JSONArray hookParms, String returnType,String returnData, ClassLoader c){

        if (hookParms==null){
            hookParms = new JSONArray();
        }
        Object[] parms = new Object[hookParms.size()+1];
        if (hookParms.size()>0){
            for (int i = 0;i<hookParms.size();i++){
                if (getParmType(hookParms.getString(i))!=null){
                    parms[i] = getParmType(hookParms.getString(i));
                }else {
                    return;
                }
            }
        }
        parms[hookParms.size()]=getReturnHook(classes,returnType,returnData);
        try {
            XposedHelpers.findAndHookMethod(classes, c, method, parms);
        }catch (Throwable e){
            H.p(H.msg("输出日志","Hook-"+classes+"时出现异常："+e.toString(),""));
        }
    }

    private Object getReturnHook(final String className,final String returnType, final String returnData){
        Object ret = new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                String methodName = methodHookParam.method.getName();
                JSONObject j = new JSONObject(true);
                JSONArray ret = new JSONArray();
                if (methodHookParam.args!=null){
                    for (Object o:methodHookParam.args){
                        ret.add(o);
                    }
                }
                j.put("参数",ret);
                j.put("返回值", XposedBridge.invokeOriginalMethod(methodHookParam.method,  methodHookParam.thisObject,  methodHookParam.args));
                j.put("调用",getStackTrace());
                H.p(H.msg("输出日志","修改--"+className+"--"+methodName+"的返回值为--"+returnData,j));
                return getReturn(returnType,returnData);
            }
        };
        return ret;
    }

    private Object getParmType(String parm){
        switch (parm){
            case "J":
                return Long.TYPE;
            case "I":
                return Integer.TYPE;
            case "Z":
                return Boolean.TYPE;
            case "B":
                return Byte.TYPE;
            case "D":
                return Double.TYPE;
            case "C":
                return Character.TYPE;
            case "F":
                return Float.TYPE;
            case "S":
                return Short.TYPE;
            case "[J":
                return Long[].class;
            case "[I":
                return Integer[].class;
            case "[Z":
                return Boolean[].class;
            case "[B":
                return Byte[].class;
            case "[D":
                return Double[].class;
            case "[C":
                return Character[].class;
            case "[F":
                return Float[].class;
            case "[S":
                return Short[].class;
            default:
                try {
                    return XposedHelpers.findClass(parm,classLoader);
                }catch (Throwable e){
                    H.p(H.msg("输出日志","没有找到类--"+parm,""));
                    return null;
                }

        }
    }


    private Object getReturn(final String returnType, final String returnData){
        JSONObject retObject = new JSONObject(true);
        if (returnData.equals("null")){
            return null;
        }
        retObject.put("value",returnData);
        Object ret = retObject.get("value");
        switch (returnType){
            case "I":
                return retObject.getInteger("value");
            case "Z":
                return retObject.getBoolean("value");
            case "java.lang.String":
                return retObject.getString("value");
            case "J":
                return retObject.getLong("value");
            case "B":
                return retObject.getByte("value");
            case "F":
                return retObject.getFloat("value");
            case "D":
                return retObject.getDouble("value");
            case "C":
                return (Character)ret;
            case "S":
                return retObject.getShort("value");
        }
        return ret;
    }
}
