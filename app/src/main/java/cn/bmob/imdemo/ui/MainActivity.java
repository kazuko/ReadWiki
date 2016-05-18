package cn.bmob.imdemo.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.orhanobut.logger.Logger;

import org.greenrobot.eventbus.Subscribe;

import butterknife.Bind;
import butterknife.OnClick;
import cn.bmob.imdemo.R;
import cn.bmob.imdemo.base.BaseActivity;
import cn.bmob.imdemo.bean.User;
import cn.bmob.imdemo.db.NewFriendManager;
import cn.bmob.imdemo.event.RefreshEvent;
import cn.bmob.imdemo.model.UserModel;
import cn.bmob.imdemo.ui.fragment.CommunicationFragment;
import cn.bmob.imdemo.ui.fragment.ContactFragment;
import cn.bmob.imdemo.ui.fragment.ConversationFragment;
import cn.bmob.imdemo.ui.fragment.ReadingFragment;
import cn.bmob.imdemo.util.IMMLeaks;
import cn.bmob.newim.BmobIM;
import cn.bmob.newim.core.ConnectionStatus;
import cn.bmob.newim.event.MessageEvent;
import cn.bmob.newim.event.OfflineMessageEvent;
import cn.bmob.newim.listener.ConnectListener;
import cn.bmob.newim.listener.ConnectStatusChangeListener;
import cn.bmob.newim.listener.ObseverListener;
import cn.bmob.newim.notification.BmobNotificationManager;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;

/**
 * @author :smile
 * @project:MainActivity
 * @date :2016-01-15-18:23
 */
public class MainActivity extends BaseActivity implements ObseverListener{

    @Bind(R.id.btn_conversation)
    Button btn_conversation;
    @Bind(R.id.btn_set)
    Button btn_set;

    @Bind(R.id.btn_contact)
    Button btn_contact;

    @Bind(R.id.btn_communication)
    Button btn_communication;

    @Bind(R.id.iv_conversation_tips)
    ImageView iv_conversation_tips;

    @Bind(R.id.iv_contact_tips)
    ImageView iv_contact_tips;

    @Bind(R.id.drawerlayout)
    DrawerLayout drawerLayout;

    @Bind(R.id.left)
    LinearLayout leftlayout;

    @Bind(R.id.tv_set_name)
    TextView tv_set_name;

    @Bind(R.id.layout_info)
    RelativeLayout layout_info;

    private Button[] mTabs;
    private ConversationFragment conversationFragment;
    private ReadingFragment readingFragment;
    private CommunicationFragment communicationFragment;
    ContactFragment contactFragment;
    private Fragment[] fragments;
    private int index;
    private int currentTabIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //connect server
        User user = BmobUser.getCurrentUser(this,User.class);
        String username = user.getUsername();
        tv_set_name.setText(TextUtils.isEmpty(username)?"":username);
        BmobIM.connect(user.getObjectId(), new ConnectListener() {
            @Override
            public void done(String uid, BmobException e) {
                if (e == null) {
                    Logger.i("connect success");
                } else {
                    Logger.e(e.getErrorCode() + "/" + e.getMessage());
                }
            }
        });
        //监听连接状态，也可通过BmobIM.getInstance().getCurrentStatus()来获取当前的长连接状态
        BmobIM.getInstance().setOnConnectStatusChangeListener(new ConnectStatusChangeListener() {
            @Override
            public void onChange(ConnectionStatus status) {
                toast("" + status.getMsg());
            }
        });
        //解决leancanary提示InputMethodManager内存泄露的问题
        IMMLeaks.fixFocusedViewLeak(getApplication());
    }

    @Override
    protected void initView() {
        super.initView();
        mTabs = new Button[4];
        mTabs[0] = btn_conversation;
        mTabs[1] = btn_contact;
        mTabs[2] = btn_set;
        mTabs[3] = btn_communication;
        mTabs[2].setSelected(true);
        initTab();
        currentTabIndex = 2;
    }

    private void initTab(){
        conversationFragment = new ConversationFragment();
        readingFragment = new ReadingFragment();
        contactFragment = new ContactFragment();
        communicationFragment = new CommunicationFragment();
        fragments = new Fragment[] {conversationFragment, contactFragment, readingFragment, communicationFragment};
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, conversationFragment).
                add(R.id.fragment_container, contactFragment)
                .add(R.id.fragment_container, readingFragment)
                .add(R.id.fragment_container, communicationFragment)
                .hide(conversationFragment).hide(contactFragment).hide(communicationFragment)
                .show(readingFragment).commit();
    }

    public void onTabSelect(View view) {
        switch (view.getId()) {
            case R.id.btn_conversation:
                index = 0;
                break;
            case R.id.btn_contact:
                index = 1;
                break;
            case R.id.btn_set:
                index = 2;
                break;
            case R.id.btn_communication:
                index = 3;
                break;
        }
        onTabIndex(index);
    }

    private void onTabIndex(int index){
        if (currentTabIndex != index) {
            FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
            trx.hide(fragments[currentTabIndex]);
            if (!fragments[index].isAdded()) {
                trx.add(R.id.fragment_container, fragments[index]);
            }
            trx.show(fragments[index]).commit();
        }
        mTabs[currentTabIndex].setSelected(false);
        mTabs[index].setSelected(true);
        currentTabIndex = index;
    }

    @OnClick(R.id.layout_info)
    public void onInfoClick(View view){
        Bundle bundle = new Bundle();
        bundle.putSerializable("u", BmobUser.getCurrentUser(this,User.class));
        startActivity(UserInfoActivity.class, bundle, false);
    }

    @OnClick(R.id.btn_logout)
    public void onLogoutClick(View view){
        UserModel.getInstance().logout();
        //可断开连接
        BmobIM.getInstance().disConnect();
        this.finish();
        startActivity(LoginActivity.class,null, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //显示小红点
        checkRedPoint();
        //添加观察者-用于是否显示通知消息
        BmobNotificationManager.getInstance(this).addObserver(this);
        //进入应用后，通知栏应取消
        BmobNotificationManager.getInstance(this).cancelNotification();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //移除观察者
        BmobNotificationManager.getInstance(this).removeObserver(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //清理导致内存泄露的资源
        BmobIM.getInstance().clear();
        //完全退出应用时需调用clearObserver来清除观察者
        BmobNotificationManager.getInstance(this).clearObserver();
    }

    /**注册消息接收事件
     * @param event
     */
    @Subscribe
    public void onEventMainThread(MessageEvent event){
        checkRedPoint();
    }

    /**注册离线消息接收事件
     * @param event
     */
    @Subscribe
    public void onEventMainThread(OfflineMessageEvent event){
        checkRedPoint();
    }

    /**注册自定义消息接收事件
     * @param event
     */
    @Subscribe
    public void onEventMainThread(RefreshEvent event){
        log("---主页接收到自定义消息---");
        checkRedPoint();
    }

    private void checkRedPoint(){
        if(BmobIM.getInstance().getAllUnReadCount()>0){
            iv_conversation_tips.setVisibility(View.VISIBLE);
        }else{
            iv_conversation_tips.setVisibility(View.GONE);
        }
        //是否有好友添加的请求
        if(NewFriendManager.getInstance(this).hasNewFriendInvitation()){
            iv_contact_tips.setVisibility(View.VISIBLE);
        }else{
            iv_contact_tips.setVisibility(View.GONE);
        }
    }

}
