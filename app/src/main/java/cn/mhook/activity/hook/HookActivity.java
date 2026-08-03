package cn.mhook.activity.hook;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nightonke.boommenu.BoomButtons.HamButton;
import com.nightonke.boommenu.BoomButtons.OnBMClickListener;
import com.nightonke.boommenu.BoomMenuButton;
import com.tamsiree.rxkit.RxActivityTool;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.util.ArrayList;
import java.util.List;

import cn.mhook.BaseActivity;
import cn.mhook.activity.editcfg.EditHookActivity;
import cn.mhook.mhook.EventMessage;
import cn.mhook.mhook.R;
import cn.mhook.mhook.contentprovider.jsonCfg;

public class HookActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private List<HookActivityItem> datas = new ArrayList<>();
    private HookActivityAdapter adapter;
    private Handler handler;

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter!=null){
            initList();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hook);
        handler = new Handler();
        EventBus.getDefault().register(this);
        initListView();
    }

    private void initListView(){
        recyclerView = (RecyclerView)findViewById(R.id.config_recycler_view);
        refreshLayout=(SwipeRefreshLayout)findViewById(R.id.refresh_layout);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        refreshLayout.setRefreshing(true);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                initList();
            }
        });
        adapter = new HookActivityAdapter(R.layout.activity_hook_item, datas,HookActivity.this);
        adapter.setEmptyView(LayoutInflater.from(this).inflate(R.layout.view_empty, null));
        recyclerView.setAdapter(adapter);
        initList();
        initBoomMenu();
    }

    private void initBoomMenu(){
        BoomMenuButton bmb = findViewById(R.id.bmb);
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("添加配置")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        RxActivityTool.skipActivity(HookActivity.this, EditHookActivity.class);
                    }
                })
                .subNormalText("添加配置文件"));
    }

    private  void initList(){

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (datas.size()>0){
                            datas.clear();
                        }
                        JSONArray jsonArray = jsonCfg.getAllCfg();
                        if (jsonArray.size()>0){
                            for (Object o:jsonArray) {
                                JSONObject jsonObject = JSONObject.parseObject(o.toString());
                                JSONObject cfg = jsonObject.getJSONObject("config");
                                datas.add(new HookActivityItem(cfg.getString("appPkg"),
                                        cfg.getString("appName"),
                                        cfg.getString("detail"),
                                        cfg.getString("author"),
                                        cfg.getString("time"),
                                        cfg.getString("appVer"),
                                        cfg.getString("keyStr"),
                                        cfg.containsKey("cfgId")?cfg.getString("cfgId"):"",
                                        jsonObject.getBoolean("canUse"),
                                        jsonObject.getBoolean("enable")));
                            }
                        }

                        adapter.notifyDataSetChanged();
                        refreshLayout.setRefreshing(false);
                    }
                }, 0);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReceiveMsg(final EventMessage message) {
        if (message.getType().equals("sync")){
            refreshLayout.setRefreshing(true);
            initList();
        }
    }
}
