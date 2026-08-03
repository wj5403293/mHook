package cn.mhook.floatprint.setting;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import com.lzf.easyfloat.EasyFloat;
import com.qmuiteam.qmui.arch.QMUIFragment;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;

import org.greenrobot.eventbus.EventBus;

import cn.mhook.mhook.EventMessage;
import cn.mhook.mhook.R;

public class Setting extends QMUIFragment {

    private View root;
    private Handler handler;
    private QMUIGroupListView mGroupListView;

    


    @Override
    protected View onCreateView() {
        root = LayoutInflater.from(getContext()).inflate(R.layout.float_setting, null);
        handler = new Handler();
        initGroupList();
        return root;
    }

    private void initGroupList(){
        mGroupListView = root.findViewById(R.id.groupListView);
        QMUICommonListItemView statusCheck = mGroupListView.createItemView("关闭悬浮窗");
        statusCheck .setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON);
        QMUIGroupListView.newSection(getContext())
                .setTitle("基本")
                .addItemView(statusCheck, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EasyFloat.dismissAppFloat("print");
                        EasyFloat.dismissAppFloat("icon");
                    }
                })
                .addTo(mGroupListView);
    }
}
