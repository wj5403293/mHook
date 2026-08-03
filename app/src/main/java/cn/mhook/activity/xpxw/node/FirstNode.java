package cn.mhook.activity.xpxw.node;

import android.graphics.drawable.Drawable;

import com.chad.library.adapter.base.entity.node.BaseExpandNode;
import com.chad.library.adapter.base.entity.node.BaseNode;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FirstNode extends BaseExpandNode {

    private List<BaseNode> childNode;
    private String title,pkg;
    private Drawable logo;

    public FirstNode(List<BaseNode> childNode, String title,String pkg, Drawable logo) {
        this.childNode = childNode;
        this.title = title;
        this.logo = logo;
        this.pkg = pkg;
        setExpanded(false);
    }

    public String getTitle() {
        return title;
    }

    public Drawable getLogo() {
        return logo;
    }

    public String getPkg() {
        return pkg;
    }

    @Nullable
    @Override
    public List<BaseNode> getChildNode() {
        return childNode;
    }
}