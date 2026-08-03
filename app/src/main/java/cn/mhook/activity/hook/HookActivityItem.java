package cn.mhook.activity.hook;

public class HookActivityItem {
    String pkg,appName,detail,author,time,ver,cfgKey,cfgId;
    Boolean verification;
    Boolean enable;

    public HookActivityItem(String pkg, String appName, String detail, String author, String time, String ver, String cfgKey, String cfgId, Boolean verification,Boolean enable) {
        this.pkg = pkg;
        this.appName = appName;
        this.detail = detail;
        this.author = author;
        this.time = time;
        this.ver = ver;
        this.cfgKey = cfgKey;
        this.cfgId = cfgId;
        this.verification = verification;
        this.enable = enable;
    }

    public String getPkg() {
        return pkg;
    }

    public String getAppName() {
        return appName;
    }

    public String getDetail() {
        return detail;
    }

    public String getAuthor() {
        return author;
    }

    public String getTime() {
        return time;
    }

    public String getVer() {
        return ver;
    }

    public String getCfgKey() {
        return cfgKey;
    }

    public String getCfgId() {
        return cfgId;
    }

    public Boolean getVerification() {
        return verification;
    }

    public Boolean getEnable() {
        return enable;
    }
}
