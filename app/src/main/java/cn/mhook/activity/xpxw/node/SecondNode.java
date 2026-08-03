package cn.mhook.activity.xpxw.node;


import android.graphics.drawable.Drawable;

import com.chad.library.adapter.base.entity.node.BaseExpandNode;
import com.chad.library.adapter.base.entity.node.BaseNode;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SecondNode extends BaseNode {
    private String title,pkg,xpPkg;
    private Drawable logo;

    public SecondNode(String title,Drawable logo,String pkg,String xpPkg) {
        this.title = title;
        this.logo = logo;
        this.pkg = pkg;
        this.xpPkg = xpPkg;
    }

    public String getXpPkg() {
        return xpPkg;
    }

    public String getPkg() {
        return pkg;
    }

    public Drawable getLogo() {
        return logo;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    @Override
    public List<BaseNode> getChildNode() {
        return null;
    }
}