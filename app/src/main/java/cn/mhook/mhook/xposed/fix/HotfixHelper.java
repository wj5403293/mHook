package cn.mhook.mhook.xposed.fix;


import android.content.Context;

import java.io.File;
import java.lang.reflect.Array;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;

public class HotfixHelper {

    public static void applyPatch(Context context, File optimizeDir, List<File> files) {

        // 获取宿主的ClassLoader
        ClassLoader classLoader = context.getClassLoader();
        Class loaderClass = BaseDexClassLoader.class;
        try {
            // 获取宿主ClassLoader的pathList对象
            Object hostPathList = ReflectUtil.getField(loaderClass, classLoader, "pathList");
            // 获取宿主pathList对象中的dexElements数组对象
            Object hostDexElement = ReflectUtil.getField(hostPathList.getClass(), hostPathList, "dexElements");
            if (!optimizeDir.exists()) {
                optimizeDir.mkdir();
            }
            for (File file:files){
                // 创建补丁包的类加载器
                DexClassLoader patchClassLoader = new DexClassLoader(file.getPath(), optimizeDir.getPath(), null, classLoader);
                // 获取补丁ClassLoader中的pathList对象
                Object patchPathList = ReflectUtil.getField(loaderClass, patchClassLoader, "pathList");
                // 获取补丁pathList对象中的dexElements数组对象
                Object patchDexElement = ReflectUtil.getField(patchPathList.getClass(), patchPathList, "dexElements");

                // 合并宿主中的dexElements和补丁中的dexElements，并把补丁的dexElements放在数组的头部
                Object newDexElements = combineArray(hostDexElement, patchDexElement);
                // 将合并完成的dexElements设置到宿主ClassLoader中去
                ReflectUtil.setField(hostPathList.getClass(), hostPathList, "dexElements", newDexElements);
            }

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param hostElements    宿主中的dexElements
     * @param patchElements   补丁包中的dexElements
     * @return Object         合并成的dexElements
     */
    private static Object combineArray(Object hostElements, Object patchElements) {
        Class<?> componentType = hostElements.getClass().getComponentType();
        int i = Array.getLength(hostElements);
        int j = Array.getLength(patchElements);
        int k = i + j;
        Object result = Array.newInstance(componentType, k);
        // 将补丁包的dexElements合并到头部
        System.arraycopy(patchElements, 0, result, 0, j);
        System.arraycopy(hostElements, 0, result, j, i);
        return result;
    }

    public interface OnPatchLoadListener {
        void onSuccess();
        void onFailure();
    }
}