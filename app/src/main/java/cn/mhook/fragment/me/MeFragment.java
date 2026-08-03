package cn.mhook.fragment.me;

import android.view.LayoutInflater;
import android.view.View;

import com.qmuiteam.qmui.arch.QMUIFragment;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;

import cn.mhook.mhook.R;

public class MeFragment extends QMUIFragment {


    private View root;

    


    @Override
    protected View onCreateView() {
        root = LayoutInflater.from(getContext()).inflate(R.layout.fragment_me, null);
        initGroupList();
        return root;
    }

    private void initGroupList(){
        QMUIGroupListView mGroupListView = root.findViewById(R.id.groupListView);
        QMUICommonListItemView about = mGroupListView.createItemView("我的配置");
        about.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON);
        QMUICommonListItemView cn = mGroupListView.createItemView("账户设置");
        cn.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON);
        QMUICommonListItemView xx = mGroupListView.createItemView("软件设置");
        xx.setTipPosition(QMUICommonListItemView.TIP_POSITION_LEFT);
        xx.showRedDot(true);
        xx.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON);
        QMUIGroupListView.newSection(getContext())
                .addItemView(about,null)
                .addItemView(cn,null)
                .addItemView(xx,null)
                .addTo(mGroupListView);
    }
}
