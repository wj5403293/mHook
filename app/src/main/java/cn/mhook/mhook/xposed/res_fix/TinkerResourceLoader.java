package cn.mhook.mhook.xposed.res_fix;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;

import cn.mhook.mhook.xposed.fix.ShareConstants;
import cn.mhook.mhook.xposed.fix.ShareReflectUtil;
import cn.mhook.mhook.xposed.fix.SystemClassLoaderAdder;
import cn.mhook.mhook.xposed.utils.H;

/**
 * Created by liangwenxiang on 2016/4/14.
 */
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;


import com.tamsiree.rxkit.RxFileTool;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Created by liangwenxiang on 2016/4/14.
 */
public class TinkerResourceLoader {
    private static final String TAG = "Tinker.ResourceLoader";

    private TinkerResourceLoader() {
    }

    /**
     * Load tinker resources
     */
    public static boolean loadTinkerResources(Application application, String resourceString) {

        if (!RxFileTool.isFileExists(resourceString))return false;
        try {
            TinkerResourcePatcher.isResourceCanPatch(application);
            TinkerResourcePatcher.monkeyPatchExistingResources(application, resourceString);
            H.p(H.msg("输出日志","检测MK热修复-资源",""));
        } catch (Throwable e) {
            Log.e(TAG, "install resources failed");
            return false;
        }
        // tinker resources loaded, monitor runtime accident
        ResourceStateMonitor.tryStart(application);
        return true;
    }



    /**
     * Some situations may cause our resource modification to be ineffective,
     * for example, an APPLICATION_INFO_CHANGED message will reset LoadedApk#mResDir
     * to default value, then a relaunch activity which using tinker resources may
     * throw an Resources$NotFoundException.
     *
     * Monitor and handle them.
     */
    private static class ResourceStateMonitor {

        private static boolean started = false;

        static void tryStart(Application app) {
            if (Build.VERSION.SDK_INT < 26 || started) {
                return;
            }
            try {
                interceptHandler(fetchMHObject(app));
                started = true;
            } catch (Throwable e) {
                Log.e(TAG, "ResourceStateMonitor start failed, simply ignore.", e);
            }
        }

        private static Handler fetchMHObject(Context context) throws Exception {
            final Object activityThread = ShareReflectUtil.getActivityThread(context, null);
            final Field mHField = ShareReflectUtil.findField(activityThread, "mH");
            return (Handler) mHField.get(activityThread);
        }

        private static void interceptHandler(Handler mH) throws Exception {
            final Field mCallbackField = ShareReflectUtil.findField(Handler.class, "mCallback");
            final Handler.Callback originCallback = (Handler.Callback) mCallbackField.get(mH);
            HackerCallback hackerCallback = new HackerCallback(originCallback, mH.getClass());
            mCallbackField.set(mH, hackerCallback);
        }

        private static class HackerCallback implements Handler.Callback {

            private final int APPLICATION_INFO_CHANGED;

            private Handler.Callback origin;

            HackerCallback(Handler.Callback ori, Class $H) {
                this.origin = ori;
                int appInfoChanged;
                try {
                    appInfoChanged = ShareReflectUtil.findField($H, "APPLICATION_INFO_CHANGED").getInt(null);
                } catch (Throwable e) {
                    appInfoChanged = 156; // default value
                }
                APPLICATION_INFO_CHANGED = appInfoChanged;
            }

            @Override
            public boolean handleMessage(Message msg) {
                boolean consume = false;
                if (hackMessage(msg)) {
                    consume = true;
                } else if (origin != null) {
                    consume = origin.handleMessage(msg);
                }
                return consume;
            }

            private boolean hackMessage(Message msg) {
                if (msg.what == APPLICATION_INFO_CHANGED) {
                    // We are generally in the background this moment(signal trigger is
                    // in front of user), and the signal was going to relaunch all our
                    // activities to apply new overlay resources. So we could simply kill
                    // ourselves, or ignore this signal, or reload tinker resources.
                    Process.killProcess(Process.myPid());
                    return true;
                }
                return false;
            }

        }

    }

}
