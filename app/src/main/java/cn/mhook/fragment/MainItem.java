package cn.mhook.fragment;

import android.graphics.drawable.Drawable;
import android.view.View;

public class MainItem {
    String name,con;
    int color,icon;
    View.OnClickListener onClickListener;

    public MainItem(String name, String con, int color, int icon, View.OnClickListener onClickListener) {
        this.name = name;
        this.con = con;
        this.color = color;
        this.icon = icon;
        this.onClickListener = onClickListener;
    }

    public String getName() {
        return name;
    }

    public String getCon() {
        return con;
    }

    public int getColor() {
        return color;
    }

    public int getIcon() {
        return icon;
    }

    public View.OnClickListener getOnClickListener() {
        return onClickListener;
    }
}
