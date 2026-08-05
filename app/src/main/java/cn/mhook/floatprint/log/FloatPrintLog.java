package cn.mhook.floatprint.log;

import android.app.AlertDialog;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.SimpleCallback;
import com.qmuiteam.qmui.arch.QMUIFragment;
import com.tamsiree.rxkit.RxFileTool;
import com.tamsiree.rxkit.RxSPTool;
import com.tamsiree.rxkit.RxTimeTool;
import com.tamsiree.rxkit.RxTool;
import com.tamsiree.rxkit.view.RxToast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import cn.mhook.activity.SelectApp;
import cn.mhook.mhook.R;
import cn.mhook.mhook.contentprovider.PrintData;

public class FloatPrintLog extends QMUIFragment {


    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private View root;
    private Handler handler;
    private List<FloatPrintLogItem> datas = new ArrayList<>();
    private FloatPrintLogAdapter adapter;
    private int endId = 0;
    private Boolean stop = false;

    /**
     * onCreateView
     */
    @Override
    protected View onCreateView() {
        root = LayoutInflater.from(getContext()).inflate(R.layout.float_print_log, null);
        handler = new Handler();
        initListView();
        initMenu();
        return root;
    }

    private void initListView(){
        recyclerView = (RecyclerView) root.findViewById(R.id.config_recycler_view);
        refreshLayout=(SwipeRefreshLayout)root.findViewById(R.id.refresh_layout);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        refreshLayout.setEnabled(false);
        adapter = new FloatPrintLogAdapter(R.layout.float_print_item, datas);
        recyclerView.setAdapter(adapter);
        PrintData.delAll(RxTool.getContext());
        handler.post(task);
    }

    private void initMenu(){
        root.findViewById(R.id.cleanAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        datas.clear();
                        adapter.notifyDataSetChanged();
                    }
                }, 0);
            }
        });
        root.findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop = !stop;
                TextView t = root.findViewById(R.id.stop_text);
                t.setText(stop?"继续":"暂停");
            }
        });
        root.findViewById(R.id.sx).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RxToast.warning("无需筛选");
            }
        });
        root.findViewById(R.id.dc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JSONObject j = PrintData.getData(RxTool.getContext(),0);
                if (j==null||j.isEmpty()){
                    return;
                }
                String s = JSONObject.toJSONString(j,true);
                String path = "/sdcard/cn.mhook.mhook/OtherAppLog/"+ RxTimeTool.getCurTimeString(new SimpleDateFormat("HH:mm:ss"))+".json";
                RxFileTool.writeFileFromString(path,s,false);
                RxToast.success("已导出到 "+path);
            }
        });
    }


    private Runnable task =new Runnable() {
        public void run() {
            // TODOAuto-generated method stub
            handler.postDelayed(this,200);//设置延迟时间，此处是5秒
            JSONObject jsonObject = PrintData.getData(RxTool.getContext(),endId);
            int eid = jsonObject.getIntValue("endId");
            if (eid<=0){
                return;
            }
            endId = eid;
            JSONArray msg = jsonObject.getJSONArray("msg");
            for (Object o:msg){
                JSONObject j = JSON.parseObject(o.toString());
                datas.add(new FloatPrintLogItem(JSONObject.toJSONString(j,true)));
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!stop){
                        adapter.notifyDataSetChanged();
                        recyclerView.scrollToPosition(datas.size() - 1);
                    }
                }
            }, 0);
        }
    };

}