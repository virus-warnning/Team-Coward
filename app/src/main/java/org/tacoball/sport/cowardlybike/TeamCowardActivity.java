package org.tacoball.sport.cowardlybike;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.model.GraphObject;
import com.tacoball.sport.signals.Settings;
import com.tacoball.sport.signals.Utils;
import com.tacoball.sport.signals.hal.DeviceScanner;

import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * 車錶 Activity
 */
public class TeamCowardActivity extends Activity {

    private static final String TAG = "TeamCowardActivity";

    // 金手指I - 好朋友特別允許使用
    private static final List<String> FACEBOOK_VIP = Arrays.asList(new String[] {
        "986315244715254", // me
        "957596700918932"  // Smooth Tsai
    });

    // 金手指II
    private static final String GFINGER_FILE = "gfinger.txt";

    FrameLayout  mNaviMenu;
    LinearLayout mNaviItems;

    Handler mHandler = Utils.getSharedHandler();
    ViewPager mPager;
    Settings mSettings;
    MainPagerAdapter mPagerAdapter = new MainPagerAdapter(getFragmentManager());

    /**
     * 抄來的，我也不知道這是三小，反正 FB 登入要用到
     *
     * @param requestCode 不知道
     * @param resultCode  不知道
     * @param data        不知道
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO: 0.2.5 發生過 NullPointerException
        super.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

    /**
     * 啟動時介面控管
     *
     * @param savedInstanceState 不知道
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_pager);

        // 檢查是否已驗證身分
        // - 是否已驗證 (true/false)
        // - 驗證日期   (YYYY-MM-DD)
        // - 驗證方式   (Facebook/Weibo)
        mSettings = new Settings(this);
        mSettings.setStoragePath("Team Coward");
        mSettings.setNotificationTitle("膽小車隊執行中 ...");
        mSettings.setNotificationText("點一下回到膽小車隊");
        mSettings.setNotificationIcon(R.drawable.logo);
        mSettings.setNotificationIntent(PendingIntent.getActivity(
            this, 0, new Intent(this, TeamCowardActivity.class), 0
        ));

        // 未登入狀態處理
        onSessionLost();

        // 測試用，強制移除偏好設定
        //mSettings.reset();

        // 測試用，觀察簽章摘要
        //showHashKey();

        // 測試用，暫時不使用 FB 驗證
        //onSessionCreated();
        //if (true) return;

        if (isPassedIn12Hr()) {
            onSessionCreated();
        } else {
            if (hasGoldenFingerII()) {
                onAuthPassed("Golden Finger II");
            } else {
                if (hasInternet()) {
                    checkFacebookMembership();
                    // TODO: 以後要增加中國用的項目
                } else {
                    checkOffline();
                }
            }
        }
    }

    /**
     * 未登入 UI 配置
     */
    private void onSessionLost() {
        Log.d(TAG, "未登入");

        // 強制不關閉螢幕
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 導覽列配置
        mNaviItems = (LinearLayout)findViewById(R.id.sv_navi_items);
        mNaviMenu  = (FrameLayout)findViewById(R.id.sv_navi_menu);
        mNaviMenu.setVisibility(View.INVISIBLE);

        // 翻頁器配置
        mPager = (ViewPager)findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);

        // 檢查相依套件
        new DeviceScanner(this).checkDependencies(
            R.drawable.logo,
            R.string.update_ant_title,
            R.string.update_ant_text,
            R.string.update_antplus_title,
            R.string.update_antplus_text
        );
    }

    /**
     * 已登入 UI 配置
     */
    private void onSessionCreated() {
        // 記錄最後開啟時間
        mSettings.setCustomizedLong("player.auth.touch", System.currentTimeMillis());

        // 翻頁器切換成已登入模式
        mPagerAdapter.setLogined(true);

        // 配置翻頁事件
        mPager.setOnPageChangeListener(pc);
        mPager.setCurrentItem(1);
    }

    /**
     * ViewPage 事件管理，用於連動導覽工具
     */
    private ViewPager.OnPageChangeListener pc = new ViewPager.OnPageChangeListener() {

        private boolean firstTime = true; // 第一次隱藏不使用動畫效果
        private long lastAnimTrigger = 0; // 動畫運作中，不允許再次使用動畫效果

        /**
         * 分頁器動作時，微調導覽列可見位置
         *
         * @param position             不知道
         * @param positionOffset       不知道
         * @param positionOffsetPixels 不知道
         */
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (positionOffset!=0.0f) {
                mNaviMenu.setVisibility(View.VISIBLE);
            }

            TextView tx1 = (TextView)TeamCowardActivity.this.findViewById(R.id.tx1);
            int svoff = (int)(tx1.getWidth()*(position+positionOffset));

            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams)mNaviItems.getLayoutParams();
            p.leftMargin = -svoff;
            mNaviItems.requestLayout();
        }

        /**
         * 設定值改過之後，立即變更碼表組成
         *
         * @param position 不知道
         */
        @Override
        public void onPageSelected(final int position) {
            // 如果翻到碼表頁，強制觸發 onResume() 更新狀態
            // TODO: 希望可以避免 onResume() 觸發兩次
            if (position==2) {
                mPagerAdapter.getItem(position).onResume();
            }
        }

        /**
         * 分頁器固定時，導覽列自動淡出消失
         *
         * @param state 不知道
         */
        @Override
        public void onPageScrollStateChanged(int state) {
            if (state==0) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        long currAnimTrigger = System.currentTimeMillis();
                        long duration = currAnimTrigger - lastAnimTrigger;

                        // 1. 先設定結束狀態
                        mNaviMenu.setVisibility(View.INVISIBLE);

                        // 2. 然後才跑動畫
                        if (duration>1500) {
                            if (!firstTime) {
                                Animation fadeOut = AnimationUtils.loadAnimation(TeamCowardActivity.this, R.anim.fade_out);
                                mNaviMenu.startAnimation(fadeOut);
                            } else {
                                firstTime = false;
                            }
                            lastAnimTrigger = currAnimTrigger;
                        }
                    }
                };
                mHandler.postDelayed(r, 500);
            }
        }

    };

    /**
     * 顯示目前的簽章摘要
     */
    private void showHashKey() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                "org.tacoball.sport.cowardlybike",
                PackageManager.GET_SIGNATURES
            );
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d("KeyHash: ", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (Exception e) {
            // TODO: ...
        }
    }

    /**
     * 檢查是否有網路連線
     *
     * @return 是否有網路連線
     */
    private boolean hasInternet() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        return (cm.getActiveNetworkInfo()!=null);
    }

    /**
     * 檢查是否 12 小時內曾經驗證過
     *
     * @return 是否 12 小時內曾經驗證過
     */
    private boolean isPassedIn12Hr() {
        long touch = mSettings.getCustomizedLong("player.auth.touch");
        long now   = System.currentTimeMillis();
        long diffHours = (now-touch)/1000/60/60;

        return (diffHours<12);
    }

    /**
     * 檢查 Facebook 社團的身分
     *
     * - 使用 Facebook Open Graph API 檢查
     * - 檢查是否可以列舉社團成員，可以列舉表示有加入社團
     */
    private void checkFacebookMembership() {
        // 膽小車隊是封閉社團，非社團成員存取 API 時，實際狀況如下:
        // - /group         可以正常存取
        // - /group/members 可以正常存取，但是資料是空的
        //
        // 注意!! 抓全部的成員封包太大，只要抓一個人確定有社團權限即可
        //
        // 因此要檢查 /group/members 是否真得有成員清單，
        // 假如成員清單是空的，表示自己不隸屬膽小車隊，此時 JSON 值如下:
        // {"data": []}
        final String API_MEMBERS = "/198718930223955/members";
        final Bundle PARAMS_MEMBERS = new Bundle();
        PARAMS_MEMBERS.putInt("limit",1);

        final String API_ME = "/me";
        final Bundle PARAMS_ME = new Bundle();
        PARAMS_MEMBERS.putString("fields", "id,name");

        // 1. 取得個資
        // 2. 檢查是不是隊員
        // 3. 檢查是不是 VIP
        Session.openActiveSession(this, true, new Session.StatusCallback() {

            Session mSession = null;

            // 順利登入後，執行 OG API
            @Override
            public void call(Session session, SessionState state, Exception exception) {
                if (session.isOpened()) {
                    mSession = session;
                    String id = mSettings.getCustomizedString("player.auth.facebook_id");
                    if (id.equals("")) {
                        sendRequest(API_ME, PARAMS_ME);           // 如果 Facebook ID 尚未取得，先取個資
                    } else {
                        sendRequest(API_MEMBERS, PARAMS_MEMBERS); // 如果 Facebook ID 已存在，直接檢查社團權限
                    }
                }
            }

            // 執行 OG API
            private void sendRequest(String api, Bundle params) {
                new Request(mSession, api, params, HttpMethod.GET, mCallback).executeAsync();
            }

            // 處理 OG API 回應
            private Request.Callback mCallback = new Request.Callback() {
                public void onCompleted(Response response) {
                    Log.d(TAG, String.format("收到回應: %s", response.getRequest().getGraphPath()));
                    if (response.getError()==null) {
                        String api = response.getRequest().getGraphPath();
                        GraphObject gobj = response.getGraphObject();

                        if (api==API_ME) {
                            String id   = (String)gobj.getProperty("id");
                            String name = (String)gobj.getProperty("name");
                            mSettings.setCustomizedString("player.auth.facebook_id", id);
                            mSettings.setCustomizedString("player.auth.name", name);

                            // 個資取得之後，繼續檢查社團權限
                            sendRequest(API_MEMBERS, PARAMS_MEMBERS);
                        }

                        if (api==API_MEMBERS) {
                            List<GraphObject> objects = gobj.getPropertyAsList("data", GraphObject.class);
                            if (objects.size()>0) {
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        onAuthPassed("Facebook");     // 隸屬膽小車隊
                                    }
                                }, 1000);
                            } else {
                                String id = mSettings.getCustomizedString("player.auth.facebook_id");
                                File gfinger = null;

                                if (FACEBOOK_VIP.contains(id)) {
                                    Toast.makeText(TeamCowardActivity.this, "以金手指模式登入", Toast.LENGTH_LONG).show();
                                    onAuthPassed("Facebook");         // 不屬膽小車隊，而是 VIP
                                } else if (gfinger!=null && gfinger.exists()) {
                                    onAuthPassed("Golden Finger II"); // 二代金手指
                                } else {
                                    onAuthFailed();                   // 不屬膽小車隊，也不是 VIP
                                }
                            }
                        }
                    } else {
                        // 網路連線或是伺服器異常
                        onAuthError("Facebook");
                    }
                }
            };

        });
    }

    /**
     * 離線身分檢查
     */
    private void checkOffline() {
        // 檢查是否驗證過，以及是否到期
        boolean allowed = mSettings.getCustomizedBoolean("player.auth.allowed");

        if (allowed) {
            String currDate = Utils.getDateString();
            String prevDate = mSettings.getCustomizedString("player.auth.date");
            if (!prevDate.equals("")) {
                int datediff = Utils.getDateDiff(currDate, prevDate);
                if (datediff<=90) {
                    // 離線驗證有效
                    onSessionCreated();

                    int remaining = 90-datediff;
                    if (remaining<14) {
                        String msg = String.format("還剩下 %d 天可以離線操作，有空請打開網路重新確認膽小車友身分喔！", remaining);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    // 離線驗證到期
                    onAuthExpired();
                }
            }
        } else {
            // 尚未連線驗證
            onAuthFailed();
        }
    }

    private boolean hasGoldenFingerII() {
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.KITKAT) {
            File extDir = null;
            File[] dirs = getExternalFilesDirs("magic");
            for (int i=dirs.length-1; i>=0; i--) {
                if (dirs[i]!=null) {
                    extDir = dirs[i]; break;
                }
            }

            if (extDir!=null) {
                File gf = new File(extDir, GFINGER_FILE);
                if (gf.exists()) {
                    Toast.makeText(this, "發現金手指檔案", Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 連線驗證成功後處理
     *
     * @param site 驗證網站
     */
    private void onAuthPassed(String site) {
        // 檢查是否驗證過，以及是否到期
        boolean allowed = mSettings.getCustomizedBoolean("player.auth.allowed");

        // 第一次登入
        if (!allowed) {
            // 開通使用權
            mSettings.setCustomizedBoolean("player.auth.allowed", true);
            mSettings.setCustomizedString("player.auth.site", site);

            // TODO: 多國語系處理
            Toast.makeText(this, "認證通過！歡迎使用膽小車隊 APP", Toast.LENGTH_LONG).show();
        }

        // 設定 OR 展延離線作業時效
        mSettings.setCustomizedString("player.auth.date", Utils.getDateString());

        // 開啟車錶主畫面
        onSessionCreated();
    }

    /**
     * 連線驗證失敗後處理
     */
    private void onAuthFailed() {
        // 封鎖使用權
        mSettings.setCustomizedBoolean("player.auth.allowed", false);

        // 顯示烤土司訊息
        // TODO: 多國語系處理
        if (hasInternet()) {
            Toast.makeText(this, "很抱歉！膽小車隊 APP 僅供膽小車隊成員使用", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "請開啟網路，確認您是否為膽小車隊車友", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 連線驗證網路異常處理
     */
    private void onAuthError(String site) {
        Log.d(TAG, String.format("%s 驗證過程異常", site));

        // 改走離線驗證流程
        checkOffline();
    }

    /**
     * 離線驗證到期後處理
     */
    private void onAuthExpired() {
        // 封鎖使用權
        mSettings.setCustomizedBoolean("player.auth.allowed", false);

        // 顯示烤土司訊息
        // TODO: 多國語系處理
        Toast.makeText(this, "你已經離線操作超過 90 天！請打開網路重新確認你是膽小車隊的車友", Toast.LENGTH_LONG).show();
    }

}

/**
 * 車錶分頁
 * - 未登入: 顯示登入畫面，且只有一頁不可翻頁
 * - 已登入: 顯示車表畫面，共 5 頁
 *
 * 注意! 預設的 ViewPager 無法移除 Page，需要用一招必殺技
 * http://stackoverflow.com/questions/10396321/remove-fragment-page-from-viewpager-in-android
 */
class MainPagerAdapter extends FragmentPagerAdapter {

    int baseId;
    Fragment[] frags;

    public MainPagerAdapter(FragmentManager fm) {
        super(fm);
        setLogined(false);
    }

    @Override
    public Fragment getItem(int i) {
        return frags[i];
    }

    @Override
    public int getCount() {
        return frags.length;
    }

    @Override
    public int getItemPosition(Object object) {
        return PagerAdapter.POSITION_NONE;
    }

    @Override
    public long getItemId(int position) {
        return baseId + position;
    }

    public void setLogined(boolean logined) {
        if (logined) {
            baseId = 0;

            frags = new Fragment[] {
                new SensorsFragment(),
                //new SosFragment(),
                new PanelFragment(),
                new SettingsFragment(),
                new AboutFragment()
            };

            // TODO: 解除帳號綁定
        } else {
            baseId = 10;

            frags = new Fragment[] {
                new LoginFragment(),
            };
        }

        notifyDataSetChanged();
    }

}