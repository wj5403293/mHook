package cn.mhook.mhook.xposed.dump.util;

import java.io.FileOutputStream;
public class FileUtils {

    public static void writeByteToFile(byte[] data, String path) {
        try {
            FileOutputStream localFileOutputStream = new FileOutputStream(path);
            localFileOutputStream.write(data);
            localFileOutputStream.close();
        } catch (Exception e) {

        }
    }
}
