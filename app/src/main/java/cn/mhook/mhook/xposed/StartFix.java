package cn.mhook.mhook.xposed;

import android.app.Application;
import android.content.Context;
import com.tamsiree.rxkit.RxFileTool;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import cn.mhook.activity.mkfix.MK;
import cn.mhook.mhook.xposed.fix.HotfixHelper;
import cn.mhook.mhook.xposed.fix.SystemClassLoaderAdder;
import cn.mhook.mhook.xposed.res_fix.TinkerResourceLoader;
import cn.mhook.mhook.xposed.utils.H;
import de.robv.android.xposed.XC_MethodHook;
import me.weishu.reflection.Reflection;

import static cn.mhook.mhook.xposed.utils.mHookCfg.fixDir;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class StartFix {

    public static Application application;
    public static void init() throws IOException, ClassNotFoundException {
        findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                application = (Application)param.thisObject;
                if (!RxFileTool.isFileExists(fixDir)) return;
                Reflection.unseal(application);
                int mode = MK.getCheck(H.pkg);
                
                if (mode == 3) {
                    try {
                        copyAutoJsAssets(new ZipFile(fixDir), new File(application.getFilesDir(), "project"));
                        H.p(H.msg("输出日志","AutoJS脚本覆盖成功",""));
                    }catch (Throwable throwable){
                        H.p(H.msg("输出日志","AutoJS脚本覆盖失败",""));
                    }
                    return;
                }
                String dexDir = application.getCacheDir()+"/.dexCache/";
                RxFileTool.deleteDir(dexDir);
                File optimizeDir = new File(application.getCacheDir() + "/optimize");
                if (!optimizeDir.exists()) {
                    optimizeDir.mkdir();
                }
                ArrayList<File> legalFiles = new ArrayList<>();
                ZipFile zipFile = new ZipFile(fixDir);
                for (FileHeader file:zipFile.getFileHeaders()) {
                    String fileName = file.getFileName();
                    if (fileName.endsWith(".dex") && !fileName.contains("/")) {
                        zipFile.extractFile(fileName, dexDir);
                        legalFiles.add(new File(dexDir + fileName));
                    }
                }
                try {
                    switch (mode){
                        case 0:
                            SystemClassLoaderAdder.installDexes(application,application.getClassLoader(),optimizeDir,legalFiles,false);
                            break;
                        case 1:
                            SystemClassLoaderAdder.installDexes(application,application.getClassLoader(),optimizeDir,legalFiles,true);
                            break;
                        case 2:
                            HotfixHelper.applyPatch(application,optimizeDir,legalFiles);
                            break;
                    }
                }catch (Throwable throwable){
                    H.p(H.msg("输出日志","dex热修复失败",""));
                }

                if (zipFile.getFileHeader("resources.arsc")==null) return;

                TinkerResourceLoader.loadTinkerResources(application,fixDir);
            }
        });
    }

    private static void copyAutoJsAssets(ZipFile zipFile, File projectDir) throws IOException {
        if (zipFile.getFileHeader("assets/project/project.json")==null) return;
        for (FileHeader file:zipFile.getFileHeaders()) {
            String name = file.getFileName();
            if (!name.startsWith("assets/project/") || name.endsWith("/")) continue;
            if (!isScriptFile(name)) continue;
            String rel = name.substring("assets/project/".length());
            File dst = new File(projectDir, rel);
            File dir = dst.getParentFile();
            if (dir!=null && !dir.exists() && !dir.mkdirs()) continue;
            InputStream is = null;
            OutputStream os = null;
            try {
                is = zipFile.getInputStream(file);
                os = new FileOutputStream(dst);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            } catch (Throwable ignored) {
            } finally {
                if (os != null) try { os.close(); } catch (IOException ignored) {}
                if (is != null) try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static boolean isScriptFile(String name) {
        return name.endsWith(".js") || name.endsWith(".snapshot") || name.endsWith("project.json");
    }
}
