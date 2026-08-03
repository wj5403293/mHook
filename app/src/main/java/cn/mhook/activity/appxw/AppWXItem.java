package cn.mhook.activity.appxw;

public class AppWXItem {
    String pkg,ver,appName;

    public AppWXItem(String pkg, String ver, String appName) {
        this.pkg = pkg;
        this.ver = ver;
        this.appName = appName;
    }

    public String getPkg() {
        return pkg;
    }

    public String getVer() {
        return ver;
    }

    public String getAppName() {
        return appName;
    }
}
