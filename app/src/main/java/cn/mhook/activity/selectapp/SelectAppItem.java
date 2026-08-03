package cn.mhook.activity.selectapp;

public class SelectAppItem {
    String pkg,ver,appName;
    Boolean select;
    boolean header;
    String headerText;

    public SelectAppItem(String pkg, String ver, String appName,Boolean select) {
        this.pkg = pkg;
        this.ver = ver;
        this.appName = appName;
        this.select = select;
    }

    public SelectAppItem(String headerText) {
        this.header = true;
        this.headerText = headerText;
    }

    public boolean isHeader() {
        return header;
    }

    public String getHeaderText() {
        return headerText;
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

    public Boolean getSelect() {
        return select;
    }
}
