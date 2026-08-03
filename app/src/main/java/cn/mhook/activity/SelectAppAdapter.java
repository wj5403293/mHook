package cn.mhook.activity;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;

import cn.mhook.mhook.R;

public class SelectAppAdapter extends BaseQuickAdapter<SelectAppItem, BaseViewHolder> {


    public SelectAppAdapter(@LayoutRes int layoutResId, @Nullable List<SelectAppItem> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(final BaseViewHolder helper, final SelectAppItem item) {
        helper.setText(R.id.name,item.getAppName())
                .setText(R.id.ver,item.getAppVer())
                .setText(R.id.pkg,item.getAppPkg())
                .setImageDrawable(R.id.icon,item.getLogo());
    }
}
