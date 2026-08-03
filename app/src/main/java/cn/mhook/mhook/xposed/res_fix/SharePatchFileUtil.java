package cn.mhook.mhook.xposed.res_fix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.mhook.mhook.xposed.fix.ShareConstants;


public class SharePatchFileUtil {
    private static final String TAG = "Tinker.PatchFileUtil";

    private static char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    




    public static File getPatchDirectory(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            
            return null;
        }

        return new File(applicationInfo.dataDir, ShareConstants.PATCH_DIRECTORY_NAME);
    }

    public static File getPatchTempDirectory(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            
            return null;
        }

        return new File(applicationInfo.dataDir, ShareConstants.PATCH_TEMP_DIRECTORY_NAME);
    }

    public static File getPatchLastCrashFile(Context context) {
        File tempFile = getPatchTempDirectory(context);
        if (tempFile == null) {
            return null;
        }
        return new File(tempFile, ShareConstants.PATCH_TEMP_LAST_CRASH_NAME);
    }

    public static File getPatchInfoFile(String patchDirectory) {
        return new File(patchDirectory + "/" + ShareConstants.PATCH_INFO_NAME);
    }

    public static File getPatchInfoLockFile(String patchDirectory) {
        return new File(patchDirectory + "/" + ShareConstants.PATCH_INFO_LOCK_NAME);
    }

    public static String getPatchVersionDirectory(String version) {
        if (version == null || version.length() != ShareConstants.MD5_LENGTH) {
            return null;
        }

        return ShareConstants.PATCH_BASE_NAME + version.substring(0, 8);
    }

    public static String getPatchVersionFile(String version) {
        if (version == null || version.length() != ShareConstants.MD5_LENGTH) {
            return null;
        }

        return getPatchVersionDirectory(version) + ShareConstants.PATCH_SUFFIX;
    }

    public static boolean checkIfMd5Valid(final String object) {
        if ((object == null) || (object.length() != ShareConstants.MD5_LENGTH)) {
            return false;
        }
        return true;
    }

    public static String checkTinkerLastUncaughtCrash(Context context) {
        File crashFile = SharePatchFileUtil.getPatchLastCrashFile(context);
        if (!SharePatchFileUtil.isLegalFile(crashFile)) {
            return null;
        }
        StringBuffer buffer = new StringBuffer();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(crashFile)));
            String line;
            while ((line = in.readLine()) != null) {
                buffer.append(line);
                buffer.append("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "checkTinkerLastUncaughtCrash exception: " + e);
            return null;
        } finally {
            closeQuietly(in);
        }

        return buffer.toString();

    }

    


    @SuppressLint("NewApi")
    public static void closeQuietly(Object obj) {
        if (obj == null) return;
        if (obj instanceof Closeable) {
            try {
                ((Closeable) obj).close();
            } catch (Throwable ignored) {
                
            }
        } else if (Build.VERSION.SDK_INT >= 19 && obj instanceof AutoCloseable) {
            try {
                ((AutoCloseable) obj).close();
            } catch (Throwable ignored) {
                
            }
        } else if (obj instanceof ZipFile) {
            try {
                ((ZipFile) obj).close();
            } catch (Throwable ignored) {
                
            }
        } else {
            throw new IllegalArgumentException("obj: " + obj + " cannot be closed.");
        }
    }

    public static final boolean isLegalFile(File file) {
        return file != null && file.exists() && file.canRead() && file.isFile() && file.length() > 0;
    }



    





    public static long getFileOrDirectorySize(File directory) {
        if (directory == null || !directory.exists()) {
            return 0;
        }
        if (directory.isFile()) {
            return directory.length();
        }
        long totalSize = 0;
        File[] fileList = directory.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    totalSize = totalSize + getFileOrDirectorySize(file);
                } else {
                    totalSize = totalSize + file.length();
                }
            }
        }
        return totalSize;
    }

    public static final boolean safeDeleteFile(File file) {
        if (file == null) {
            return true;
        }

        if (file.exists()) {
            Log.i(TAG, "safeDeleteFile, try to delete path: " + file.getPath());

            boolean deleted = file.delete();
            if (!deleted) {
                Log.e(TAG, "Failed to delete file, try to delete when exit. path: " + file.getPath());
                file.deleteOnExit();
            }
            return deleted;
        }
        return true;
    }

    public static final boolean deleteDir(String dir) {
        if (dir == null) {
            return false;
        }
        return deleteDir(new File(dir));

    }

    public static final boolean deleteDir(File file) {
        if (file == null || (!file.exists())) {
            return false;
        }
        if (file.isFile()) {
            safeDeleteFile(file);
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    deleteDir(subFile);
                }
                safeDeleteFile(file);
            }
        }
        return true;
    }


    


    public static boolean verifyFileMd5(File file, String md5) {
        if (md5 == null) {
            return false;
        }
        String fileMd5 = getMD5(file);

        if (fileMd5 == null) {
            return false;
        }

        return md5.equals(fileMd5);
    }

    public static boolean isRawDexFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.endsWith(ShareConstants.DEX_SUFFIX);
    }

    



    public static boolean verifyDexFileMd5(File file, String md5) {
        return verifyDexFileMd5(file, ShareConstants.DEX_IN_JAR, md5);
    }

    public static boolean verifyDexFileMd5(File file, String entryName, String md5) {
        if (file == null || md5 == null || entryName == null) {
            return false;
        }
        
        String fileMd5 = "";

        if (isRawDexFile(file.getName())) {
            fileMd5 = getMD5(file);
        } else {
            ZipFile dexJar = null;
            try {
                dexJar = new ZipFile(file);
                ZipEntry classesDex = dexJar.getEntry(entryName);
                
                if (null == classesDex) {
                    Log.e(TAG, "There's no entry named: " + ShareConstants.DEX_IN_JAR + " in " + file.getAbsolutePath());
                    return false;
                }
                InputStream is = null;
                try {
                    is = dexJar.getInputStream(classesDex);
                    fileMd5 = getMD5(is);
                } catch (Throwable e) {
                    Log.e(TAG, "exception occurred when get md5: " + file.getAbsolutePath(), e);
                } finally {
                    closeQuietly(is);
                }
            } catch (Throwable e) {
                Log.e(TAG, "Bad dex jar file: " + file.getAbsolutePath(), e);
                return false;
            } finally {
                closeZip(dexJar);
            }
        }

        return md5.equals(fileMd5);
    }

    public static void copyFileUsingStream(File source, File dest) throws IOException {
        if (!SharePatchFileUtil.isLegalFile(source) || dest == null) {
            return;
        }
        if (source.getAbsolutePath().equals(dest.getAbsolutePath())) {
            return;
        }
        FileInputStream is = null;
        FileOutputStream os = null;
        File parent = dest.getParentFile();
        if (parent != null && (!parent.exists())) {
            parent.mkdirs();
        }
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest, false);

            byte[] buffer = new byte[ShareConstants.BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            closeQuietly(is);
            closeQuietly(os);
        }
    }

    




    public static String loadDigestes(JarFile jarFile, JarEntry je) throws Exception {
        InputStream bis = null;
        StringBuilder sb = new StringBuilder();

        try {
            InputStream is = jarFile.getInputStream(je);
            byte[] bytes = new byte[ShareConstants.BUFFER_SIZE];
            bis = new BufferedInputStream(is);
            int readBytes;
            while ((readBytes = bis.read(bytes)) > 0) {
                sb.append(new String(bytes, 0, readBytes));
            }
        } finally {
            closeQuietly(bis);
        }
        return sb.toString();
    }

    





    public final static String getMD5(final InputStream is) {
        if (is == null) {
            return null;
        }
        try {
            BufferedInputStream bis = new BufferedInputStream(is);
            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder md5Str = new StringBuilder(32);

            byte[] buf = new byte[ShareConstants.MD5_FILE_BUF_LENGTH];
            int readCount;
            while ((readCount = bis.read(buf)) != -1) {
                md.update(buf, 0, readCount);
            }

            byte[] hashValue = md.digest();

            for (int i = 0; i < hashValue.length; i++) {
                md5Str.append(Integer.toString((hashValue[i] & 0xff) + 0x100, 16).substring(1));
            }
            return md5Str.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String getMD5(byte[] buffer) {
        try {
            MessageDigest mdTemp = MessageDigest.getInstance("MD5");
            mdTemp.update(buffer);
            byte[] md = mdTemp.digest();
            int j = md.length;
            char[] str = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }

    




    public static String getMD5(final File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            String md5 = getMD5(fin);
            return md5;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return null;
        } finally {
            closeQuietly(fin);
        }
    }



    public static void closeZip(ZipFile zipFile) {
        try {
            if (zipFile != null) {
                zipFile.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to close resource", e);
        }
    }

    public static boolean checkResourceArscMd5(File resOutput, String destMd5) {
        ZipFile resourceZip = null;
        try {
            resourceZip = new ZipFile(resOutput);
            ZipEntry arscEntry = resourceZip.getEntry(ShareConstants.RES_ARSC);
            if (arscEntry == null) {
                Log.i(TAG, "checkResourceArscMd5 resources.arsc not found");
                return false;
            }
            InputStream inputStream = null;
            try {
                inputStream = resourceZip.getInputStream(arscEntry);
                String md5 = SharePatchFileUtil.getMD5(inputStream);
                if (md5 != null && md5.equals(destMd5)) {
                    return true;
                }
            } finally {
                closeQuietly(inputStream);
            }

        } catch (Throwable e) {
            Log.i(TAG, "checkResourceArscMd5 throwable:" + e.getMessage());

        } finally {
            SharePatchFileUtil.closeZip(resourceZip);
        }
        return false;
    }

    public static void ensureFileDirectory(File file) {
        if (file == null) {
            return;
        }
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
    }

}

