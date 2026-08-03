package cn.mhook.fragment;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;

import cn.mhook.mhook.R;

public class MainAdapter extends BaseQuickAdapter<MainItem, BaseViewHolder> {


    public MainAdapter(@LayoutRes int layoutResId, @Nullable List<MainItem> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(final BaseViewHolder helper, final MainItem item) {
        helper.setText(R.id.title,item.getName())
                .setText(R.id.title2,item.getCon())
                .setImageResource(R.id.logo,item.getIcon())
                .setTextColor(R.id.title,item.getColor());
        CardView cardView = helper.getView(R.id.icon);
        cardView.setCardBackgroundColor(item.getColor());
        if (item.getOnClickListener()!=null){
            CardView cardItem = helper.getView(R.id.card);
            cardItem.setOnClickListener(item.getOnClickListener());
        }
    }
}
