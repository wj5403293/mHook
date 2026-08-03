package cn.mhook.mhook.xposed.modulexw;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import cn.mhook.mhook.xposed.utils.H;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static cn.mhook.mhook.xposed.utils.H.getStackTrace;

public class HookXposed {

    public HookXposed(){
        try {
            hookFindAndHookMethod(XposedHelpers.class);
            hookcallMethod(XposedHelpers.class);
            hookAllMethod(XposedBridge.class);
        }catch (Throwable throwable){

        }
    }

    private void hookAllMethod(Class<?> c){
        XposedHelpers.findAndHookMethod(c, "hookAllMethods", Class.class, String.class, XC_MethodHook.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                Class<?> c =(Class<?> ) param.args[0];
                String methodName = (String) param.args[1];
                JSONArray parms = new JSONArray();
                H.p(H.msg("主动HOOK方法-hookAllMethods",c.getName()+"-"+methodName,getAdd(methodName,parms)));
            }
        });
        XposedHelpers.findAndHookMethod(c, "hookAllConstructors", Class.class, XC_MethodHook.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                Class<?> c =(Class<?> ) param.args[0];
                JSONArray parms = new JSONArray();
                H.p(H.msg("主动HOOK构造方法-hookAllConstructors",c.getName(),getStackTrace()));
            }
        });
    }

    private void hookFindAndHookMethod(Class<?> c){
        XposedHelpers.findAndHookMethod(c, "findAndHookMethod", Class.class, String.class, Object[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                Class<?> c =(Class<?> ) param.args[0];
                String methodName = (String) param.args[1];
                Object[] params = (Object[]) param.args[2];
                JSONArray parms = safeParams(params);
                H.p(H.msg("主动HOOK方法-findAndHookMethod",c.getName()+"-"+methodName,getAdd(methodName,parms)));
            }
        });
        XposedHelpers.findAndHookMethod(c, "findAndHookMethod", String.class,ClassLoader.class, String.class, Object[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                String c =(String) param.args[0];
                String methodName = (String) param.args[2];
                Object[] params = (Object[]) param.args[3];
                JSONArray parms = safeParams(params);
                H.p(H.msg("主动HOOK方法-findAndHookMethod",c+"-"+methodName,getAdd(methodName,parms)));
            }
        });
    }
    private void hookcallMethod(Class<?> c){
        XposedHelpers.findAndHookMethod(c, "callMethod", Object.class, String.class, Object[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                String method = (String)param.args[1];
                Object[] params = (Object[]) param.args[2];
                JSONArray parms = safeParams(params);
                H.p(H.msg("主动调用方法-callMethod",c.getName()+"-"+method,getAdd(method,parms)));
            }
        });
    }

    private JSONArray safeParams(Object[] params){
        try {
            JSONArray parms = JSONArray.parseArray(JSONArray.toJSONString(params));
            parms.remove(params.length-1);
            return parms;
        }catch (Throwable throwable){
            JSONArray parms = new JSONArray();
            parms.add("<参数序列化失败:"+throwable.getMessage()+">");
            return parms;
        }
    }

    private JSONObject getAdd(String method, JSONArray parms){
        JSONObject ret = new JSONObject(true);
        ret.put("方法",method);
        ret.put("参数",parms);
        ret.put("调用",getStackTrace());
        return ret;
    }
}
