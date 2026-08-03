package cn.mhook.mhook.xposed.fix;

import android.app.Application;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.mhook.mhook.xposed.utils.H;




public class SystemClassLoaderAdder {
    private static final String TAG = "Tinker.ClassLoaderAdder";
    private static int sPatchDexCount = 0;

    public static void installDexes(Application application, ClassLoader loader, File dexOptDir, List<File> files, boolean isProtectedApp)
            throws Throwable {
        if (!files.isEmpty()) {
            H.p(H.msg("输出日志","使用MK热修复-Dex",""));
            files = createSortedAdditionalPathEntries(files);
            ClassLoader classLoader = loader;
            if (!isProtectedApp&&Build.VERSION.SDK_INT >= 24) {
                classLoader = NewClassLoaderInjector.inject(application, loader, dexOptDir, files);
            } else {
                if (Build.VERSION.SDK_INT >= 23) {
                    V23.install(classLoader, files, dexOptDir);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    V19.install(classLoader, files, dexOptDir);
                } else if (Build.VERSION.SDK_INT >= 14) {
                    V14.install(classLoader, files, dexOptDir);
                }
            }
            sPatchDexCount = files.size();
        }
    }


    private static List<File> createSortedAdditionalPathEntries(List<File> additionalPathEntries) {
        final List<File> result = new ArrayList<>(additionalPathEntries);

        final Map<String, Boolean> matchesClassNPatternMemo = new HashMap<>();
        for (File file : result) {
            final String name = file.getName();
            matchesClassNPatternMemo.put(name, ShareConstants.CLASS_N_PATTERN.matcher(name).matches());
        }
        Collections.sort(result, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                if (lhs == null && rhs == null) {
                    return 0;
                }
                if (lhs == null) {
                    return -1;
                }
                if (rhs == null) {
                    return 1;
                }

                final String lhsName = lhs.getName();
                final String rhsName = rhs.getName();
                if (lhsName.equals(rhsName)) {
                    return 0;
                }

                final String testDexSuffix = ShareConstants.TEST_DEX_NAME;
                
                if (lhsName.startsWith(testDexSuffix)) {
                    return 1;
                }
                if (rhsName.startsWith(testDexSuffix)) {
                    return -1;
                }

                final boolean isLhsNameMatchClassN = matchesClassNPatternMemo.get(lhsName);
                final boolean isRhsNameMatchClassN = matchesClassNPatternMemo.get(rhsName);
                if (isLhsNameMatchClassN && isRhsNameMatchClassN) {
                    final int lhsDotPos = lhsName.indexOf('.');
                    final int rhsDotPos = rhsName.indexOf('.');
                    final int lhsId = (lhsDotPos > 7 ? Integer.parseInt(lhsName.substring(7, lhsDotPos)) : 1);
                    final int rhsId = (rhsDotPos > 7 ? Integer.parseInt(rhsName.substring(7, rhsDotPos)) : 1);
                    return (lhsId == rhsId ? 0 : (lhsId < rhsId ? -1 : 1));
                } else if (isLhsNameMatchClassN) {
                    
                    return -1;
                } else if (isRhsNameMatchClassN) {
                    return 1;
                }
                return lhsName.compareTo(rhsName);
            }
        });

        return result;
    }

    


    private static final class ArkHot {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries)
                throws IllegalArgumentException, IllegalAccessException, NoSuchMethodException,
                InvocationTargetException, IOException, ClassNotFoundException, SecurityException {
            Class<?>  extendedClassLoaderHelper = ClassLoader.getSystemClassLoader()
                    .getParent().loadClass("com.huawei.ark.classloader.ExtendedClassLoaderHelper");

            for (File file : additionalClassPathEntries) {
                String path = file.getCanonicalPath();
                Method applyPatchMethod = extendedClassLoaderHelper.getDeclaredMethod(
                        "applyPatch", ClassLoader.class, String.class);
                applyPatchMethod.setAccessible(true);
                applyPatchMethod.invoke(null, loader, path);
                Log.i(TAG, "ArkHot install path = " + path);
            }
        }
    }

    


    private static final class V23 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
            




            Field pathListField = ShareReflectUtil.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            ShareReflectUtil.expandFieldArray(dexPathList, "dexElements", makePathElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
                    suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                for (IOException e : suppressedExceptions) {
                    Log.w(TAG, "Exception in makePathElement", e);
                    throw e;
                }

            }
        }

        



        private static Object[] makePathElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory,
                ArrayList<IOException> suppressedExceptions)
                throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

            Method makePathElements;
            try {
                makePathElements = ShareReflectUtil.findMethod(dexPathList, "makePathElements", List.class, File.class,
                        List.class);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "NoSuchMethodException: makePathElements(List,File,List) failure");
                try {
                    makePathElements = ShareReflectUtil.findMethod(dexPathList, "makePathElements", ArrayList.class, File.class, ArrayList.class);
                } catch (NoSuchMethodException e1) {
                    Log.e(TAG, "NoSuchMethodException: makeDexElements(ArrayList,File,ArrayList) failure");
                    try {
                        Log.e(TAG, "NoSuchMethodException: try use v19 instead");
                        return V19.makeDexElements(dexPathList, files, optimizedDirectory, suppressedExceptions);
                    } catch (NoSuchMethodException e2) {
                        Log.e(TAG, "NoSuchMethodException: makeDexElements(List,File,List) failure");
                        throw e2;
                    }
                }
            }

            return (Object[]) makePathElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
        }
    }

    


    private static final class V19 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
            




            Field pathListField = ShareReflectUtil.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            ShareReflectUtil.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
                    suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                for (IOException e : suppressedExceptions) {
                    Log.w(TAG, "Exception in makeDexElement", e);
                    throw e;
                }
            }
        }

        



        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory,
                ArrayList<IOException> suppressedExceptions)
                throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

            Method makeDexElements = null;
            try {
                makeDexElements = ShareReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
                        ArrayList.class);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "NoSuchMethodException: makeDexElements(ArrayList,File,ArrayList) failure");
                try {
                    makeDexElements = ShareReflectUtil.findMethod(dexPathList, "makeDexElements", List.class, File.class, List.class);
                } catch (NoSuchMethodException e1) {
                    Log.e(TAG, "NoSuchMethodException: makeDexElements(List,File,List) failure");
                    throw e1;
                }
            }

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
        }
    }

    


    private static final class V14 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            




            Field pathListField = ShareReflectUtil.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ShareReflectUtil.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory));
        }

        



        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory)
                throws IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            Method makeDexElements =
                    ShareReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class);

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
        }
    }

}
