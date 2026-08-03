package cn.mhook.mhook.xposed.res_fix;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;


import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import cn.mhook.mhook.xposed.fix.ShareConstants;
import cn.mhook.mhook.xposed.fix.ShareReflectUtil;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.KITKAT;
import static cn.mhook.mhook.xposed.fix.ShareReflectUtil.findField;
import static cn.mhook.mhook.xposed.fix.ShareReflectUtil.findMethod;





class TinkerResourcePatcher {
    private static final String TAG = "Tinker.ResourcePatcher";
    private static final String TEST_ASSETS_VALUE = "only_use_to_test_tinker_resource.txt";

    
    private static Collection<WeakReference<Resources>> references = null;
    private static Object currentActivityThread = null;
    private static AssetManager newAssetManager = null;

    
    private static Method addAssetPathMethod = null;
    private static Method ensureStringBlocksMethod = null;

    
    private static Field assetsFiled = null;
    private static Field resourcesImplFiled = null;
    private static Field resDir = null;
    private static Field packagesFiled = null;
    private static Field resourcePackagesFiled = null;
    private static Field publicSourceDirField = null;
    private static Field stringBlocksField = null;

    @SuppressWarnings("unchecked")
    public static void isResourceCanPatch(Context context) throws Throwable {
        
        
        

        
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        currentActivityThread = ShareReflectUtil.getActivityThread(context, activityThread);

        
        Class<?> loadedApkClass;
        try {
            loadedApkClass = Class.forName("android.app.LoadedApk");
        } catch (ClassNotFoundException e) {
            loadedApkClass = Class.forName("android.app.ActivityThread$PackageInfo");
        }

        resDir = findField(loadedApkClass, "mResDir");
        packagesFiled = findField(activityThread, "mPackages");
        if (Build.VERSION.SDK_INT < 27) {
            resourcePackagesFiled = findField(activityThread, "mResourcePackages");
        }

        
        final AssetManager assets = context.getAssets();
        addAssetPathMethod = findMethod(assets, "addAssetPath", String.class);

        
        
        try {
            stringBlocksField = findField(assets, "mStringBlocks");
            ensureStringBlocksMethod = findMethod(assets, "ensureStringBlocks");
        } catch (Throwable ignored) {
            
        }

        
        
        newAssetManager = (AssetManager) ShareReflectUtil.findConstructor(assets).newInstance();

        
        if (SDK_INT >= KITKAT) {
            
            
            final Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
            final Method mGetInstance = findMethod(resourcesManagerClass, "getInstance");
            final Object resourcesManager = mGetInstance.invoke(null);
            try {
                Field fMActiveResources = findField(resourcesManagerClass, "mActiveResources");
                final ArrayMap<?, WeakReference<Resources>> activeResources19 =
                        (ArrayMap<?, WeakReference<Resources>>) fMActiveResources.get(resourcesManager);
                references = activeResources19.values();
            } catch (NoSuchFieldException ignore) {
                
                final Field mResourceReferences = findField(resourcesManagerClass, "mResourceReferences");
                references = (Collection<WeakReference<Resources>>) mResourceReferences.get(resourcesManager);
            }
        } else {
            final Field fMActiveResources = findField(activityThread, "mActiveResources");
            final HashMap<?, WeakReference<Resources>> activeResources7 =
                    (HashMap<?, WeakReference<Resources>>) fMActiveResources.get(currentActivityThread);
            references = activeResources7.values();
        }
        
        if (references == null) {
            throw new IllegalStateException("resource references is null");
        }

        final Resources resources = context.getResources();

        
        
        if (SDK_INT >= 24) {
            try {
                
                resourcesImplFiled = findField(resources, "mResourcesImpl");
            } catch (Throwable ignore) {
                
                assetsFiled = findField(resources, "mAssets");
            }
        } else {
            assetsFiled = findField(resources, "mAssets");
        }

        try {
            publicSourceDirField = findField(ApplicationInfo.class, "publicSourceDir");
        } catch (NoSuchFieldException ignore) {
            
        }
    }

    




    public static void monkeyPatchExistingResources(Context context, String externalResourceFile) throws Throwable {
        if (externalResourceFile == null) {
            return;
        }

        final ApplicationInfo appInfo = context.getApplicationInfo();

        final Field[] packagesFields;
        if (Build.VERSION.SDK_INT < 27) {
            packagesFields = new Field[]{packagesFiled, resourcePackagesFiled};
        } else {
            packagesFields = new Field[]{packagesFiled};
        }
        for (Field field : packagesFields) {
            final Object value = field.get(currentActivityThread);

            for (Map.Entry<String, WeakReference<?>> entry
                    : ((Map<String, WeakReference<?>>) value).entrySet()) {
                final Object loadedApk = entry.getValue().get();
                if (loadedApk == null) {
                    continue;
                }
                final String resDirPath = (String) resDir.get(loadedApk);
                if (appInfo.sourceDir.equals(resDirPath)) {
                    resDir.set(loadedApk, externalResourceFile);
                }
            }
        }

        
        if (((Integer) addAssetPathMethod.invoke(newAssetManager, externalResourceFile)) == 0) {
            throw new IllegalStateException("Could not create new AssetManager");
        }

        
        
        if (stringBlocksField != null && ensureStringBlocksMethod != null) {
            stringBlocksField.set(newAssetManager, null);
            ensureStringBlocksMethod.invoke(newAssetManager);
        }

        for (WeakReference<Resources> wr : references) {
            final Resources resources = wr.get();
            if (resources == null) {
                continue;
            }
            
            try {
                
                assetsFiled.set(resources, newAssetManager);
            } catch (Throwable ignore) {
                
                final Object resourceImpl = resourcesImplFiled.get(resources);
                
                final Field implAssets = findField(resourceImpl, "mAssets");
                implAssets.set(resourceImpl, newAssetManager);
            }

            clearPreloadTypedArrayIssue(resources);

            resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
        }

        
        
        
        
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                if (publicSourceDirField != null) {
                    publicSourceDirField.set(context.getApplicationInfo(), externalResourceFile);
                }
            } catch (Throwable ignore) {
                
            }
        }

    }

    




    private static void clearPreloadTypedArrayIssue(Resources resources) {
        
        
        
        
        
        Log.w(TAG, "try to clear typedArray cache!");
        
        try {
            final Field typedArrayPoolField = findField(Resources.class, "mTypedArrayPool");
            final Object origTypedArrayPool = typedArrayPoolField.get(resources);
            final Method acquireMethod = findMethod(origTypedArrayPool, "acquire");
            while (true) {
                if (acquireMethod.invoke(origTypedArrayPool) == null) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            Log.e(TAG, "clearPreloadTypedArrayIssue failed, ignore error: " + ignored);
        }
    }
}