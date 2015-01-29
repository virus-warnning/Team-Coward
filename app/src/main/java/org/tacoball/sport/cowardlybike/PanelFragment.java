package org.tacoball.sport.cowardlybike;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.tacoball.sport.signals.Settings;
import com.tacoball.sport.signals.SignalBuilder;
import com.tacoball.sport.signals.SignalService;
import com.tacoball.sport.signals.Utils;

import java.util.LinkedList;
import java.util.List;

/**
 * 車錶介面
 *
 * @author Raymond Wu
 * @since  0.1.0
 */
public class PanelFragment extends Fragment {

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
    private ImageView mIvCatPink;
    private ImageView mIvWheel;
    private TextView  mTxvHeartRate;
    private TextView  mTxvPower;
    private TextView  mTxvBattery;
    private TextView  mTxvNow;
    private TextView  mTxvTemperature;

    // 指針
    private Bitmap mBmpPin;         // 0 度指針
    private Bitmap mBmpSpeedPin;    // 速度指針
    private Bitmap mBmpCadencePin;  // 踏頻指針
    private Bitmap mBmpCaloriesPin; // 卡路里指針

    // 執行緒管理器
    private Handler mHandler = new Handler();

    // 實際速度值
    private int mLatestSpeed = 0;
    private int mLatestCadence = 0;

    // 漸變速度/踏頻
    private List<Float> mGradualSpeed = new LinkedList<Float>();
    private List<Float> mGradualCadence = new LinkedList<Float>();

    // 目前碼表開關狀態
    private boolean mIsMeterOn = false;

    // 訊號服務的工作狀態
    private SignalService.State mServiceState;

    // 設定值
    private Settings mSettings;

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
        mIvCatPink = (ImageView)rootView.findViewById(R.id.iv_cat_pink);
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
        mTxvTemperature = (TextView)rootView.findViewById(R.id.txv_temperature);

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
        // 0~3: 輪子四種角度
        //   4: 開始鈕
        //   5: 停止鈕
        // TODO: 需要增加啟動中/關閉中的示意圖
        mIvWheel.setOnClickListener(mOnClick);

        // 載入設定值
        mSettings = new Settings(getActivity());

        // 開啟運動訊息接收端
        IntentFilter filter = new IntentFilter(SignalService.ACTION);
        getActivity().registerReceiver(mBroadcastReceiver, filter);

        // 儀表初始化
        zeroize();

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
        getActivity().unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * 廣播接收器，解讀運動訊息
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context service, Intent intent) {
            Bundle signal = intent.getBundleExtra("SIGNAL");
            int    type   = signal.getInt(SignalBuilder.I_TYPE, SignalBuilder.TYPE_UNKNOWN);

            switch(type) {
                case SignalBuilder.TYPE_SPEED:
                    double speed = signal.getDouble(SignalBuilder.D_KMHR);
                    updateSpeed((int)speed);
                    double distance = signal.getDouble(SignalBuilder.D_KM);
                    updateDistance(distance);
                    break;
                case SignalBuilder.TYPE_POSITION:
                    // 沒有感應器時，採用 GPS 速度
                    if (!mSettings.hasBikeSpeedSensor()) {
                        speed = signal.getDouble(SignalBuilder.D_KMHR);
                        updateSpeed((int)speed);
                    }
                    break;
                case SignalBuilder.TYPE_CADENCE:
                    int rpm = signal.getInt(SignalBuilder.I_RPM);
                    updateCadence(rpm);
                    break;
                case SignalBuilder.TYPE_HEART_RATE:
                    int rate = signal.getInt(SignalBuilder.I_RATE);
                    int calories = signal.getInt(SignalBuilder.I_CALORIES);
                    updateHeartRate(rate);
                    updateCalories(calories);
                    Log.d("Shit", String.format("已消耗卡路里 %d", calories));
                    break;
                case SignalBuilder.TYPE_POWER:
                    // 注意!! 這個值必須在實際上路或訓練台才能試出來
                    double watt = signal.getDouble(SignalBuilder.D_WATT);
                    updatePower((int)watt);
                    break;
                case SignalBuilder.TYPE_STATE:
                    // 關閉狀態時，小圖換成開始鈕
                    String stateName = signal.getString(SignalBuilder.S_STATE);
                    mServiceState = SignalService.State.valueOf(stateName);
                    Log.d(TAG, String.format("服務狀態: %s", stateName));
                    switch(mServiceState) {
                        case STARTED:
                            mIvWheel.setImageLevel(5);
                            break;
                        case STOPPED:
                            mIvWheel.setImageLevel(4);
                            break;
                    }
                    break;
                case SignalBuilder.TYPE_TEMPERATURE:
                    // !! 如果溫度為攝氏 100, 表示找不到溫度計
                    // TODO: 改善異常處理方式
                    int temp = (int)signal.getDouble(SignalBuilder.D_CELSIUS);
                    String tempText = (temp!=100) ? String.format("%d°C", temp) : "";
                    mTxvTemperature.setText(tempText);
                    break;
                case SignalBuilder.TYPE_BATTERY:
                    String levelText = String.format("%d%%", signal.getInt(SignalBuilder.I_LEVEL));
                    mTxvBattery.setText(levelText);
                    break;
                case SignalBuilder.TYPE_LIGHT:
                    int lux = signal.getInt(SignalBuilder.I_LUX);
                    Log.d(TAG, String.format("環境亮度: %d lux", lux));
                    // TODO: 面板主題控制
                    break;
                case SignalBuilder.TYPE_UNKNOWN:
                    // TODO: 想想看發生時要怎麼處理
                    break;
            }
        }

    };

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
     * TODO: 回傳值改用 float 可能視覺效果較理想
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
        pinView.setImageBitmap(pinBuffer); // TODO: 這動作似乎效能不大好，可能要抽離比較好作單元測試
    }

    /**
     * 設定速度
     *
     * @param speed 速度值 (km/hr)
     */
    public void updateSpeed(int speed) {
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
        }
    }

    /**
     * 設定距離值
     *
     * @param distance 距離 (km)
     */
    public void updateDistance(double distance) {
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

    /**
     * 設定踏頻
     *
     * @param cadence 踏頻值
     */
    public void updateCadence(int cadence) {
        if (cadence!=mLatestCadence) {
            // (省電) 速度有改變才計算
            int prevCadence = mLatestCadence;
            mLatestCadence = getReasonableValue(cadence, MAX_CADENCE);

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

    /**
     * 設定剩餘熱量
     *
     * @param calories 剩餘熱量值
     */
    public void updateCalories(int calories) {
        calories = getReasonableValue(1000-calories, MAX_CALORIES);
        float drawDegrees = getAngleOfValue(calories, MAX_CALORIES, 150, 30);
        drawRotatedPin(drawDegrees, 25, 25, mBmpCaloriesPin, mIvCalories);
    }

    /**
     * 設定心率
     *
     * @param heart_rate 心率值
     */
    public void updateHeartRate(int heart_rate) {
        heart_rate = getReasonableValue(heart_rate, MAX_HEART_RATE);
        mTxvHeartRate.setText(String.format("%d", heart_rate));
    }

    /**
     * 設定功率
     *
     * @param power 功率值
     */
    public void updatePower(int power) {
        power = getReasonableValue(power, MAX_POWER);
        mTxvPower.setText(String.format("%d", power));
    }

    /**
     * 歸零
     */
    private void zeroize() {
        // 不漸變欄位歸零
        updateCalories(1000);
        updateHeartRate(0);
        updatePower(0);
        updateDistance(0);

        // 漸變欄位(速度/踏頻)歸零
        float drawDegrees;

        drawDegrees = getAngleOfValue(0, MAX_SPEED, 225, -45);
        drawRotatedPin(drawDegrees, 25, 25, mBmpSpeedPin, mIvSpeed);

        drawDegrees = getAngleOfValue(0, MAX_CADENCE, 135, -135);
        drawRotatedPin(drawDegrees, 25, 25, mBmpCadencePin, mIvCadence);

        // 小貓調整至初始狀態
        // 電量與溫度值消除
        mIvCatPink.setImageLevel(6);
        mTxvBattery.setText("");
        mTxvTemperature.setText("");
    }

    /**
     * 中央控制按鈕處理 (輪子區)
     */
    private View.OnClickListener mOnClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            Log.d(TAG, String.format("目前服務狀態: %s", mServiceState.name()));

            // 未啟動狀態 > 進入已啟動狀態，啟動動畫迴圈
            if (mServiceState==SignalService.State.STOPPED) {
                mIsMeterOn = true;

                // 開啟運動訊息服務
                Intent intent = new Intent(getActivity(), SignalService.class);
                intent.putExtra("Request", SignalService.Request.START.name());
                getActivity().startService(intent);
                mHandler.postDelayed(mGradualTask, 100);

                // 膽小貓與輪子動畫效果
                // TODO: 這裡併入漸進動作迴圈
                mHandler.postDelayed(mWheelAnimation, 100);
            }

            // 已啟動狀態
            // - 靜止狀態: 停止訊號服務
            // - 移動狀態: 不可操作，按鈕變成輪子動畫
            if (mServiceState==SignalService.State.STARTED) {
                if (mLatestSpeed==0) {
                    // 關閉運動訊息服務
                    Intent intent = new Intent(getActivity(), SignalService.class);
                    intent.putExtra("Request", SignalService.Request.STOP.name());
                    getActivity().startService(intent);

                    mIsMeterOn = false;
                    mHandler.removeCallbacksAndMessages(null);
                }
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
            if (!mIsMeterOn) return;

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
     * 輪子轉動效果
     */
    private Runnable mWheelAnimation = new Runnable() {

        int mCatLevel   = 0;
        int mWheelLevel = 0;

        @Override
        public void run() {
            if (mServiceState!=SignalService.State.STOPPED) {
                if (mLatestSpeed==0) {
                    // 服務啟動中 & 速度=0
                    mIvCatPink.setImageLevel(6);
                    mIvWheel.setImageLevel(5);
                } else {
                    // 服務啟動中 & 速度>0
                    mCatLevel   = (mCatLevel+1)%6;
                    mWheelLevel = (mWheelLevel+1)&3;
                    mIvCatPink.setImageLevel(mCatLevel);
                    mIvWheel.setImageLevel(mWheelLevel);
                }

                // 依目前速度調整動畫速度
                long delay = (mLatestSpeed>=20) ? 75 : 250;
                mHandler.postDelayed(this, delay);
            }
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
