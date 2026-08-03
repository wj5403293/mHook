package cn.mhook.mhook.xposed.modulexw;
import android.content.pm.PackageManager;
import android.os.Build;
import com.alibaba.fastjson.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import cn.mhook.mhook.xposed.utils.H;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;
import de.robv.android.xposed.XposedBridge;
import static cn.mhook.mhook.xposed.config.XpCfg.getAllCfg;
import static cn.mhook.mhook.xposed.config.XpCfg.hasCfg;
import static cn.mhook.mhook.xposed.utils.H.loadPackageParam;
import static cn.mhook.mhook.xposed.utils.H.startupparam;
import static cn.mhook.mhook.xposed.utils.H.systemContext;

public class XpXWFX {

    public XpXWFX() throws Throwable {
        if (hasCfg()) init();
    }

    private void init() throws Throwable {
        H.p(H.msg("输出日志","初始化模块行为分析",""));
        new HookXposed();
        JSONArray cfg = getAllCfg();
        for (Object pkg:cfg){
            try {
                loadModule(pkg.toString());
            }catch (Throwable throwable){
                H.p(H.msg("输出日志-","加载模块失败："+pkg+"\n"+throwable.getMessage(),""));
            }
        }
    }

    private void loadModule(String pkg) throws Throwable {
        H.p(H.msg("输出日志-","加载模块："+pkg,""));
        PackageManager pm = systemContext.getPackageManager();
        String path = pm.getApplicationInfo(pkg, 0).publicSourceDir;
        Class<?> cls;
        ClassLoader pluginDexClassLoader;
        if (Build.VERSION.SDK_INT < 26) {
            pluginDexClassLoader = new PathClassLoader(path, ClassLoader.getSystemClassLoader());
        }else {
            pluginDexClassLoader = new DexClassLoader(path, null, null, XposedBridge.BOOTCLASSLOADER);
        }
        String handleHookClass = readXposedInit(pluginDexClassLoader);
        cls = Class.forName(handleHookClass, true, pluginDexClassLoader);
        Object instance = cls.newInstance();
        Method m = findMethodByParam(cls,"initZygote",startupparam);
        if (m!=null) m.invoke(instance, startupparam);
        m = findMethodByParam(cls,"handleLoadPackage",loadPackageParam);
        if (m!=null) m.invoke(instance, loadPackageParam);
    }

    private static String readXposedInit(ClassLoader cl) throws IOException {
        InputStream inputStream = cl.getResourceAsStream("assets/xposed_init");
        if (inputStream==null){
            throw new IOException("无法读取 xposed_init");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer))!=-1){
            out.write(buffer,0,len);
        }
        inputStream.close();
        return new String(out.toByteArray(),"UTF-8").trim();
    }

    private static Method findMethodByParam(Class<?> cls,String name,Object param){
        for (Method method:cls.getDeclaredMethods()){
            if (method.getName().equals(name)&&method.getParameterTypes().length==1
                    &&method.getParameterTypes()[0].isAssignableFrom(param.getClass())){
                return method;
            }
        }
        return null;
    }
}
