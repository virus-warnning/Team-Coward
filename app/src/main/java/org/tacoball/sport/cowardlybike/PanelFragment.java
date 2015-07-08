package org.tacoball.sport.cowardlybike;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.tacoball.sport.signals.Settings;
import com.tacoball.sport.signals.SignalReceiver;
import com.tacoball.sport.signals.SignalService;
import com.tacoball.sport.signals.SignalUpdater;
import com.tacoball.sport.signals.Utils;
import com.tacoball.sport.signals.hal.CommonDevice;

import java.util.LinkedList;
import java.util.List;

/**
 * 車錶介面
 *
 * @author Raymond Wu
 * @since  0.1.0
 */
public class PanelFragment extends Fragment {

    // 除錯標籤
    private static final String TAG = "BikePanelFragment";

    // 六項數據的最大值
    private static final int MAX_SPEED      = 60;
    private static final int MAX_CADENCE    = 130;
    private static final int MAX_CALORIES   = 1000;
    private static final int MAX_HEART_RATE = 250;
    private static final int MAX_POWER      = 999;
    private static final int MAX_DISTANCE   = 9999; // 單位 0.1km (這樣比較好計算)

    // 視覺物件
    private ImageView mIvSpeed;
    private ImageView mIvCadence;
    private ImageView mIvCalories;
    private ImageView mIvD2;
    private ImageView mIvD1;
    private ImageView mIvD0;
    private ImageView mIvDN1;
    private ImageView mIvCatStop;
    private ImageView mIvCatFast;
    private ImageView mIvCatSlow;
    private ImageView mIvWheel;
    private TextView  mTxvHeartRate;
    private TextView  mTxvPower;
    private TextView  mTxvBattery;
    private TextView  mTxvNow;

    // 指針
    private Bitmap mBmpPin;         // 0 度指針
    private Bitmap mBmpSpeedPin;    // 速度指針
    private Bitmap mBmpCadencePin;  // 踏頻指針
    private Bitmap mBmpCaloriesPin; // 卡路里指針

    // 排程管理 (用於指針動畫與時鐘)
    // 注意!! 不可以清除時鐘的排程，否則時鐘會失效
    private Handler mHandler = Utils.getSharedHandler();

    // 實際速度值
    private int mLatestSpeed   = 1;
    private int mLatestCadence = 1;

    // 漸變速度/踏頻
    private List<Float> mGradualSpeed = new LinkedList<>();
    private List<Float> mGradualCadence = new LinkedList<>();

    // 訊號服務的工作狀態
    private SignalService.State mServiceState;
    private SignalReceiver      mSignalReceiver;

    // 設定值
    private Settings mSettings;

    // 輪子按鈕的五種狀態
    // drawable/wheel_status.xml
    private static final int WHEEL_STOPPED = 0;
    private static final int WHEEL_STARTED = 1;
    private static final int WHEEL_SLOW    = 2;
    private static final int WHEEL_FAST    = 3;
    private static final int WHEEL_LOADING = 4;

    // 貓貓的三種狀態
    private enum CatState {STOP, SLOW, FAST}
    private CatState mCurrentCatState = CatState.STOP;

    /**
     * 介面初始化 (好像用不到)
     */
    public PanelFragment() {
        super();
    }

    /**
     * 啟動配置
     *
     * @param inflater           管他的
     * @param container          管他的
     * @param savedInstanceState 管他的
     * @return                   管他的
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.frag_panel, container, false);

        //--------------------
        //     連結介面元素
        //--------------------

        // 碼錶指針轉動區
        mIvSpeed    = (ImageView)rootView.findViewById(R.id.iv_speed);
        mIvCadence  = (ImageView)rootView.findViewById(R.id.iv_cadence);
        mIvCalories = (ImageView)rootView.findViewById(R.id.iv_calories);

        // 小貓與輪子
        mIvCatStop = (ImageView)rootView.findViewById(R.id.iv_cat_stop);
        mIvCatFast = (ImageView)rootView.findViewById(R.id.iv_cat_fast);
        mIvCatSlow = (ImageView)rootView.findViewById(R.id.iv_cat_slow);
        mIvWheel   = (ImageView)rootView.findViewById(R.id.iv_wheel);

        // 距離數字
        mIvD0  = (ImageView)rootView.findViewById(R.id.iv_d0);
        mIvD1  = (ImageView)rootView.findViewById(R.id.iv_d1);
        mIvD2  = (ImageView)rootView.findViewById(R.id.iv_d2);
        mIvDN1 = (ImageView)rootView.findViewById(R.id.iv_dn1);

        // 文字 (功率、心率、時間、電量)
        mTxvHeartRate   = (TextView)rootView.findViewById(R.id.txv_heartrate);
        mTxvPower       = (TextView)rootView.findViewById(R.id.txv_power);
        mTxvBattery     = (TextView)rootView.findViewById(R.id.txv_battery);
        mTxvNow         = (TextView)rootView.findViewById(R.id.txv_now);

        //--------------------
        //    指針圖前置處理
        //--------------------

        // 指針圖片零度圖
        mBmpPin  = BitmapFactory.decodeResource(getResources(), R.drawable.pin2);
        int edge = mBmpPin.getWidth()*2-1;

        // 指針圖片旋轉圖
        mBmpSpeedPin    = Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);
        mBmpCadencePin  = Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);
        mBmpCaloriesPin = Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);

        //--------------------
        //    其他初始作業
        //--------------------

        // 接收輪子圖點擊事件
        mIvWheel.setOnClickListener(mOnClick);

        // 載入設定值
        mSettings = new Settings(getActivity());

        // 開啟運動訊息接收端
        mSignalReceiver = new SignalReceiver(getActivity(), mSignalUpdater);

        // 儀表初始化
        mGradualTask.run();
        mSignalUpdater.zeroize();

        // 取得服務狀態
        Intent intent = new Intent(getActivity(), SignalService.class);
        intent.putExtra("Request", SignalService.Request.REFRESH.name());
        getActivity().startService(intent);

        // 時鐘
        mHandler.post(mClock);

        return rootView;
    }

    /**
     * 釋放資源
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 關閉運動訊息接收端
        mSignalReceiver.close();
    }

    /**
     * 數值合理化
     */
    public int getReasonableValue(int value, int max) {
        value = Math.max(value, 0);
        value = Math.min(value, max);
        return value;
    }

    /**
     * 數值換算指針角度
     *
     * @param value  數值
     * @param max    最大值
     * @param begAng 指針歸零角度 (逆時針計算)
     * @param endAng 指針最大角度 (逆時針計算)
     * @return 指針角度
     */
    public float getAngleOfValue(float value, int max, int begAng, int endAng) {
        return begAng - value*(begAng-endAng)/max;
    }

    /**
     * 畫出旋轉後指針
     *
     * @param angle     角度
     * @param pinBuffer 旋轉處理緩衝區
     * @param pinView   旋轉對象的 View
     */
    public void drawRotatedPin(float angle, int cx, int cy, Bitmap pinBuffer, ImageView pinView) {
        // 旋轉運算
        Matrix matrix = new Matrix();
        matrix.postTranslate(-cx, -cy);
        matrix.postRotate(-angle);
        matrix.postTranslate(mBmpPin.getWidth(), mBmpPin.getWidth());

        // 輸出到目的端
        pinBuffer.eraseColor(Color.TRANSPARENT);
        Canvas cv = new Canvas(pinBuffer);
        cv.drawBitmap(mBmpPin, matrix, new Paint());

        // TODO 這動作似乎效能不大好，可能要抽離比較好作單元測試
        pinView.setImageBitmap(pinBuffer);
    }

    /**
     * 切換小貓狀態
     *
     * @param state 小貓狀態名稱
     */
    public void setCatState(CatState state) {
        if (mCurrentCatState == state) return;
        //Log.d(TAG, String.format("小貓狀態切換成: %s", state.name()));

        switch (mCurrentCatState) {
            case STOP:
                mIvCatStop.setVisibility(View.INVISIBLE);
                break;
            case SLOW:
                mIvCatSlow.setVisibility(View.INVISIBLE);
                ((Animatable)mIvCatSlow.getDrawable()).stop();
                break;
            case FAST:
                mIvCatFast.setVisibility(View.INVISIBLE);
                ((Animatable)mIvCatFast.getDrawable()).stop();
                break;
        }

        mCurrentCatState = state;

        switch (mCurrentCatState) {
            case STOP:
                mIvCatStop.setVisibility(View.VISIBLE);
                break;
            case SLOW:
                ((Animatable)mIvCatSlow.getDrawable()).start();
                mIvCatSlow.setVisibility(View.VISIBLE);
                break;
            case FAST:
                ((Animatable)mIvCatFast.getDrawable()).start();
                mIvCatFast.setVisibility(View.VISIBLE);
                break;
        }
    }

    private SignalUpdater mSignalUpdater = new SignalUpdater() {

        @Override
        public void updateHeartRate(int rate, int count, int calories) {
            rate = getReasonableValue(rate, MAX_HEART_RATE);
            mTxvHeartRate.setText(String.format("%d", rate));

            calories = getReasonableValue(1000-calories, MAX_CALORIES);
            float drawDegrees = getAngleOfValue(calories, MAX_CALORIES, 150, 30);
            drawRotatedPin(drawDegrees, 25, 25, mBmpCaloriesPin, mIvCalories);
        }

        @Override
        public void updateBikeCadence(int rpm, long crankRev) {
            if (rpm!=mLatestCadence) {
                // (省電) 速度有改變才計算
                int prevCadence = mLatestCadence;
                mLatestCadence = getReasonableValue(rpm, MAX_CADENCE);

                // 建立 n 個漸進分解動作
                int STEP_COUNT = 5;
                float diffCadence = mLatestCadence - prevCadence;
                mGradualCadence.clear();
                for (int i=1;i<=STEP_COUNT-1;i++) {
                    mGradualCadence.add(prevCadence + (diffCadence * i / STEP_COUNT));
                }

                // 最後一個為最終動作
                mGradualCadence.add((float)mLatestCadence);
            }
        }

        @Override
        public void updateBikeSpeed(double kmhr, double distance, long wheelRev) {
            int speed = (int)kmhr;
            if (speed==0) {
                Log.d(TAG, "zeroize()");
            }
            if (speed!=mLatestSpeed) {
                // (省電) 速度有改變才計算
                int prevSpeed = mLatestSpeed;
                mLatestSpeed = getReasonableValue(speed, MAX_SPEED);

                // 建立 n 個漸進分解動作
                int STEP_COUNT = 5;
                float diffSpeed = mLatestSpeed - prevSpeed;
                mGradualSpeed.clear();
                for (int i=1;i<=STEP_COUNT-1;i++) {
                    mGradualSpeed.add(prevSpeed + (diffSpeed*i/STEP_COUNT));
                }

                // 最後一個為最終動作
                mGradualSpeed.add((float)mLatestSpeed);

                if (speed==0) {
                    mIvWheel.setImageLevel(WHEEL_STARTED);
                    setCatState(CatState.STOP);
                } else {
                    if (speed>20) {
                        mIvWheel.setImageLevel(WHEEL_FAST);
                        setCatState(CatState.FAST);
                    } else {
                        mIvWheel.setImageLevel(WHEEL_SLOW);
                        setCatState(CatState.SLOW);
                    }
                }
            }

            // update distance
            ImageView[] digitView = new ImageView[] {
                    mIvDN1,
                    mIvD0,
                    mIvD1,
                    mIvD2
            };

            // 改以 100m 為單位處理
            int dist100 = Math.min((int)(distance*10), MAX_DISTANCE);

            // 取每一位的數字
            for (int i=0;i<4;i++) {
                int d = dist100 % 10;
                dist100/=10;
                digitView[i].setImageLevel(d);
            }
        }

        @Override
        public void updateBikePower(double powerWatt) {
            int power = (int)powerWatt;
            power = getReasonableValue(power, MAX_POWER);
            mTxvPower.setText(String.format("%d", power));
        }

        @Override
        public void updatePosition(double lat, double lng, double alt, double kmhr, long time, double distance) {
            if (!mSettings.hasBikeSpeedSensor()) {
                updateBikeSpeed(kmhr, distance, 0);
            }
        }
        @Override
        public void updateBattery(double percent, int level, int scale, int plugged) {
            int percentInt = (int)(percent*100);
            if (percentInt>0) {
                String levelText = String.format("%d%%", percentInt);
                mTxvBattery.setText(levelText);
            } else {
                mTxvBattery.setText("");
            }
        }

        @Override
        public void updateState(SignalService.State state) {
            mServiceState = state;
            int wheel_level;
            switch(state) {
                case STARTED:
                    wheel_level = WHEEL_STARTED;
                    break;
                case STOPPED:
                    wheel_level = WHEEL_STOPPED;
                    break;
                case STARTING:
                case STOPPING:
                default:
                    wheel_level = WHEEL_LOADING;
                    break;
            }
            mIvWheel.setImageLevel(wheel_level);
        }

        @Override
        public void updateSensorError(String devId, CommonDevice.Type devType) {
            // 1.5.9 will fix it
            // Toast.makeText(getActivity(), "藍牙感應器發生錯誤，請關閉藍牙再重開", Toast.LENGTH_SHORT).show();
        }

    };

    /**
     * 中央控制按鈕處理 (輪子區)
     */
    private View.OnClickListener mOnClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            Log.d(TAG, String.format("目前服務狀態: %s", mServiceState.name()));

            Intent intent = new Intent(getActivity(), SignalService.class);
            switch(mServiceState) {
                case STOPPED:
                    // 直接更新程 ING 狀態，避免空窗期連點造成錯誤
                    mServiceState = SignalService.State.STARTING;
                    mIvWheel.setImageLevel(WHEEL_LOADING);
                    // 開啟運動訊息服務
                    intent.putExtra("Request", SignalService.Request.START.name());
                    getActivity().startService(intent);
                    break;
                case STARTED:
                    if (mLatestSpeed==0) {
                        // 直接更新程 ING 狀態，避免空窗期連點造成錯誤
                        mServiceState = SignalService.State.STOPPING;
                        mIvWheel.setImageLevel(WHEEL_LOADING);
                        // 關閉運動訊息服務
                        intent.putExtra("Request", SignalService.Request.STOP.name());
                        getActivity().startService(intent);
                    }
                    break;
            }
        }

    };

    /**
     * 指針漸變效果
     */
    private Runnable mGradualTask = new Runnable() {

        final int  FPS   = 10;
        final long DELAY = 1000/FPS;

        @Override
        public void run() {
            float drawDegrees;

            // 速度處理
            if (mGradualSpeed.size()>0) {
                float gradualSpeed = mGradualSpeed.remove(0);
                drawDegrees = getAngleOfValue(gradualSpeed, MAX_SPEED, 225, -45);
                drawRotatedPin(drawDegrees, 25, 25, mBmpSpeedPin, mIvSpeed);
            }

            // 踏頻處理
            if (mGradualCadence.size()>0) {
                float gradualCadence = mGradualCadence.remove(0);
                drawDegrees = getAngleOfValue(gradualCadence, MAX_CADENCE, 135, -135);
                drawRotatedPin(drawDegrees, 25, 25, mBmpCadencePin, mIvCadence);
            }

            // 0.1 秒後製作下一個 frame
            mHandler.postDelayed(this, DELAY);
        }

    };

    /**
     * 電子時鐘效果
     */
    private Runnable mClock = new Runnable() {
        @Override
        public void run() {
            String clockText = Utils.getTimeString().substring(0,5);
            mTxvNow.setText(clockText);
            mHandler.postDelayed(this, 60000);
        }
    };

}
