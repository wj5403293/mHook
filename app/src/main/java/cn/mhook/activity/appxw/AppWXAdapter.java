package cn.mhook.activity.appxw;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.tamsiree.rxkit.RxActivityTool;

import java.util.List;

import cn.mhook.mhook.R;

public class AppWXAdapter extends BaseQuickAdapter<AppWXItem, BaseViewHolder>  {


    public AppWXAdapter(@LayoutRes int layoutResId, @Nullable List<AppWXItem> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(final BaseViewHolder helper, final AppWXItem item) {
        helper.setText(R.id.item_name_tv,item.getAppName())
                .setText(R.id.item_ver,item.getVer())
                .setText(R.id.item_pkg,item.getPkg());
        helper.getView(R.id.appInfoItem).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle();
                bundle.putString("pkg",item.getPkg());
                RxActivityTool.skipActivity(getContext(),AppSetCfg.class,bundle);
            }
        });
    }
}
