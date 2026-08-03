package cn.mhook;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import com.qmuiteam.qmui.arch.QMUISwipeBackActivityManager;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxShellTool;
import com.tamsiree.rxkit.RxTool;
import com.tencent.bugly.Bugly;
import com.tencent.bugly.crashreport.CrashReport;

import cn.mhook.mhook.contentprovider.appCfg;
import cn.mhook.mhook.contentprovider.jsonCfg;
import cn.mhook.msu.su;
import cn.mhook.skin.QDSkinManager;

public class mHookApplication extends Application {

    public static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        new Thread() {
            @Override
            public void run() {
                RxShellTool.execCmd("logcat -c",false);
                RxShellTool.execCmd( "logcat -v time > /sdcard/mHookLog.txt",false);
            }
        }.start();

        context = getApplicationContext();
        QMUISwipeBackActivityManager.init(this);
        QDSkinManager.install(this);
        jsonCfg.context = this;
        appCfg.context = this;
        RxTool.init(this);
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(this);
        strategy.setAppVersion(RxAppTool.getAppVersionName(this));      
        strategy.setAppPackageName(getPackageName());  
        Bugly.init(this, "d254101b57", false, strategy);
        su.init(this);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if((newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES){
            QDSkinManager.changeSkin(QDSkinManager.SKIN_DARK);
        }else if(QDSkinManager.getCurrentSkin() == QDSkinManager.SKIN_DARK){
            QDSkinManager.changeSkin(QDSkinManager.SKIN_BLUE);
        }
    }

}
