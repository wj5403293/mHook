package cn.mhook.activity;

import android.graphics.drawable.Drawable;

public class SelectAppItem {
    String appName,appVer,appPkg;
    Drawable logo;

    public SelectAppItem(String appName,  String appVer,String appPkg, Drawable logo) {
        this.appName = appName;
        this.appVer = appVer;
        this.appPkg = appPkg;
        this.logo = logo;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppVer() {
        return appVer;
    }

    public Drawable getLogo() {
        return logo;
    }

    public String getAppPkg() {
        return appPkg;
    }
}
