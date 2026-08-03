package cn.mhook.activity.editcfg;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;


public class EditHookActivityAdapter extends BaseQuickAdapter<EditHookActivityItem, BaseViewHolder> {


    public EditHookActivityAdapter(@LayoutRes int layoutResId, @Nullable List<EditHookActivityItem> data) {
        super(layoutResId, data);

    }

    @Override
    protected void convert(final BaseViewHolder helper, final EditHookActivityItem item) {

    }
}