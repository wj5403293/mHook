package cn.mhook.mhook.xposed.fix;


import android.content.Context;

import java.io.File;
import java.lang.reflect.Array;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;

public class HotfixHelper {

    public static void applyPatch(Context context, File optimizeDir, List<File> files) {

        
        ClassLoader classLoader = context.getClassLoader();
        Class loaderClass = BaseDexClassLoader.class;
        try {
            
            Object hostPathList = ReflectUtil.getField(loaderClass, classLoader, "pathList");
            
            Object hostDexElement = ReflectUtil.getField(hostPathList.getClass(), hostPathList, "dexElements");
            if (!optimizeDir.exists()) {
                optimizeDir.mkdir();
            }
            for (File file:files){
                
                DexClassLoader patchClassLoader = new DexClassLoader(file.getPath(), optimizeDir.getPath(), null, classLoader);
                
                Object patchPathList = ReflectUtil.getField(loaderClass, patchClassLoader, "pathList");
                
                Object patchDexElement = ReflectUtil.getField(patchPathList.getClass(), patchPathList, "dexElements");

                
                Object newDexElements = combineArray(hostDexElement, patchDexElement);
                
                ReflectUtil.setField(hostPathList.getClass(), hostPathList, "dexElements", newDexElements);
            }

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    





    private static Object combineArray(Object hostElements, Object patchElements) {
        Class<?> componentType = hostElements.getClass().getComponentType();
        int i = Array.getLength(hostElements);
        int j = Array.getLength(patchElements);
        int k = i + j;
        Object result = Array.newInstance(componentType, k);
        
        System.arraycopy(patchElements, 0, result, 0, j);
        System.arraycopy(hostElements, 0, result, j, i);
        return result;
    }

    public interface OnPatchLoadListener {
        void onSuccess();
        void onFailure();
    }
}