package cn.mhook.mhook.xposed;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import cn.mhook.mhook.xposed.utils.H;
import dalvik.system.DexFile;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import static cn.mhook.mhook.xposed.Config.getEnable;
import static cn.mhook.mhook.xposed.utils.H.loadPackageParam;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;


public class StartHook {

    public void init() throws IOException, ClassNotFoundException {

      


























        AppCfg();
    }

    private void AppCfg() throws IOException, ClassNotFoundException {
        if (getEnable("hook+")){
            H.p(H.msg("输出日志","启用HOOK+",""));
            initHookPro();
        }else{
            startHook(loadPackageParam.classLoader);
        }
    }

    private void startHook(ClassLoader c){
        new doJsonHook(c);
        new appFun(c);
    }

    private void initHookPro() throws IOException, ClassNotFoundException {
        
        DexFile dexFile = new DexFile(loadPackageParam.appInfo.sourceDir);
        Enumeration<String> classNames = dexFile.entries();
        while (classNames.hasMoreElements()) {
            final String className = classNames.nextElement();
            final Class clazz = Class.forName(className, false, loadPackageParam.classLoader);
            for (final Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isAbstract(method.getModifiers()) && !Modifier.isNative(method.getModifiers()) && !Modifier.isInterface(method.getModifiers())) {
                    t(method,className);
                }
            }
        }
    }


    private void t(Method method,String ClassName){
        Class<?>[] parms = method.getParameterTypes();
        String RetType = method.getReturnType().getName();
        String Context = Context.class.getName();
        String Application = Application.class.getName();
        String ClassLoader = ClassLoader.class.getName();
        if (RetType!=null&&!RetType.isEmpty()){
            if (RetType.equals(Context)){
                Object sHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Context context = (Context) param.getResult();
                        ClassLoader c = context.getClassLoader();
                        startHook(c);
                    }
                };
                h(method,ClassName,parms,sHook);
            }
            if (RetType.equals(Application)){
                Object sHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        android.app.Application application = (Application) param.getResult();
                        ClassLoader c = application.getClassLoader();
                        startHook(c);
                    }
                };
                h(method,ClassName,parms,sHook);
            }
            if (RetType.equals(ClassLoader)){
                Object sHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        ClassLoader c = (ClassLoader) param.getResult();
                        startHook(c);
                    }
                };
                h(method,ClassName,parms,sHook);
            }
        }

        if (parms!=null&&parms.length>0){
            List<Class<?>> p = Arrays.asList(parms);
            if (p.contains(android.content.Context.class)){
                Object sHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        Context context = (Context)param.args[p.indexOf(android.content.Context.class)];
                        ClassLoader c = context.getClassLoader();
                        startHook(c);
                    }
                };
                h(method,ClassName,parms,sHook);
            }
            if (p.contains(android.app.Application.class)){
                Object sHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        Application application = (Application)param.args[p.indexOf(android.app.Application.class)];
                        ClassLoader c = application.getClassLoader();
                        startHook(c);
                    }
                };
                h(method,ClassName,parms,sHook);
            }
            if (p.contains(java.lang.ClassLoader.class)){
                Object sHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        ClassLoader c = (ClassLoader) param.args[p.indexOf(java.lang.ClassLoader.class)];
                        startHook(c);
                    }
                };
                h(method,ClassName,parms,sHook);
            }
        }
    }

    private void h(Method method,String ClassName,Class<?>[] parms,Object sHook){
        Object[] p = new Object[parms.length+1];
        if (parms.length>0){
            for (int i = 0;i<parms.length;i++){
                p[i] = parms[i];
            }
        }
        p[parms.length]=sHook;
        findAndHookMethod(ClassName, loadPackageParam.classLoader,method.getName(),p);
    }
}
