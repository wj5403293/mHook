package cn.mhook.mhook.xposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.tamsiree.rxkit.RxFileTool;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class HookLoader implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    
    


    private final static String modulePackageName = "cn.mhook.mhook";

    


    private final String handleHookClass = XposedMain.class.getName();
    


    private final String handleHookMethod = "handleLoadPackage";

    private final String initMethod = "initZygote";

    private IXposedHookZygoteInit.StartupParam startupparam;

    





    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        
        if (loadPackageParam.appInfo == null ||
                (loadPackageParam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 1) {
            return;
        }
        Class<?> cls = getApkClass(modulePackageName);
        Object instance = cls.newInstance();
        try {
            cls.getDeclaredMethod(initMethod, startupparam.getClass()).invoke(instance, startupparam);
        }catch (NoSuchMethodException e){
            
        }
        cls.getDeclaredMethod(handleHookMethod, loadPackageParam.getClass()).invoke(instance, loadPackageParam);

        
















    }

    




    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) {
        this.startupparam = startupParam;
    }

    private Class<?> getApkClass(String modulePackageName) throws Throwable {
        ClassLoader moduleLoader = getClass().getClassLoader();
        if (moduleLoader != null) {
            try {
                return Class.forName(handleHookClass, false, moduleLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
        currentActivityThreadMethod.setAccessible(true);
        Object activityThread = currentActivityThreadMethod.invoke(activityThreadClass);
        Method getSystemContextMethod = activityThread.getClass().getDeclaredMethod("getSystemContext");
        getSystemContextMethod.setAccessible(true);
        Context systemContext = (Context) getSystemContextMethod.invoke(activityThread);
        PackageManager pm = systemContext.getPackageManager();
        String path = pm.getApplicationInfo(modulePackageName, 0).publicSourceDir;

        Class<?> cls;
        if (Build.VERSION.SDK_INT < 26) {
            PathClassLoader pluginDexClassLoader = new PathClassLoader(path, ClassLoader.getSystemClassLoader());
            cls = Class.forName(handleHookClass, true, pluginDexClassLoader);
        }else {
            DexClassLoader pluginDexClassLoader = new DexClassLoader(path, null, null, moduleLoader);
            cls = Class.forName(handleHookClass, true, pluginDexClassLoader);
        }
        return cls;
    }

    private File findApkFile(String packageName){

        String mApkFilePath = null;
        
        if (mApkFilePath == null){
            
            mApkFilePath = String.format("/data/app/%s-%s.apk", packageName, 1);
            if (!new File(mApkFilePath).exists()) {
                
                mApkFilePath = String.format("/data/app/%s-%s.apk", packageName, 2);
                if (!new File(mApkFilePath).exists()) {
                    
                    mApkFilePath = String.format("/data/app/%s-%s/base.apk", packageName, 1);
                    if (!new File(mApkFilePath).exists()) {
                        
                        mApkFilePath = String.format("/data/app/%s-%s/base.apk", packageName, 2);
                        if (!new File(mApkFilePath).exists()) {
                            mApkFilePath = null;
                        }
                    }
                }
            }
        }
        return new File(mApkFilePath);
    }

    






    private File findApkFile(Context context, String modulePackageName) {
        if (context == null) {
            return null;
        }
        try {
            Context moudleContext = context.createPackageContext(modulePackageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            String apkPath = moudleContext.getPackageCodePath();
            return new File(apkPath);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}