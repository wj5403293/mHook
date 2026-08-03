package cn.mhook.activity.xpxw;

import com.chad.library.adapter.base.BaseNodeAdapter;
import com.chad.library.adapter.base.entity.node.BaseNode;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import cn.mhook.activity.xpxw.node.FirstNode;
import cn.mhook.activity.xpxw.node.SecondNode;
import cn.mhook.mhook.R;

public class XPXWAdapter extends BaseNodeAdapter {

    public XPXWAdapter() {
        super();
        FirstProvider firstProvider = new FirstProvider();
        addNodeProvider(firstProvider);
        addNodeProvider(new SecondProvider());
    }

    @Override
    protected int getItemType(@NotNull List<? extends BaseNode> data, int position) {
        BaseNode node = data.get(position);
        if (node instanceof FirstNode) {
            return 1;
        } else if (node instanceof SecondNode) {
            return 2;
        }
        return -1;
    }

    public static final int EXPAND_COLLAPSE_PAYLOAD = 110;
}
