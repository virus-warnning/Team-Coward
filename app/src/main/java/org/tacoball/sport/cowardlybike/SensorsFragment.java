package org.tacoball.sport.cowardlybike;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tacoball.sport.signals.Settings;
import com.tacoball.sport.signals.hal.DeviceReceiver;
import com.tacoball.sport.signals.hal.DeviceScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * 感應器配對工具
 */
public class SensorsFragment extends Fragment {

    private static final String TAG = "SensorsFragment";

    Button mBtScan;
    TextView mTxvScanning;
    TextView mTxvPrompt;
    TextView mTxvIncompatible;
    GridLayout mGlSensorPanel;
    Handler mHandler;
    LayoutInflater mInflater;
    ViewGroup mContainer;
    DeviceScanner mDeviceScanner;
    Settings mSettings;

    int mSensorCount = 0;
    int mScanCounter;

    // SensorModel 選項
    // TODO: 這裡似乎用 DeviceReceiver 的列舉值即可
    enum SensorType { HRM, CSC, PWR, UNKNOWN }
    enum RadioType  { ANT, BLE }

    // Sensor 資料
    private class SensorModel {
        public SensorType sensorType = SensorType.HRM;
        public RadioType  radioType  = RadioType.ANT;
        public String     name;
        public String     manufacturer;
        public String     address;
        public int        batteryLevel;
        public int        signalLevel;
        public boolean    paired;
    }

    // Sensor 視覺資料
    private List<View> mViewList = new ArrayList<View>();
    private List<SensorModel> mModelList = new ArrayList<SensorModel>();

    // 模擬掃描功能設定
    private static final boolean ENABLE_MOCK_DATA = false;
    private static final int MOCK_SENSORS_TOTAL = 6;
    private static final SensorModel[] mMockSensors = new SensorModel[MOCK_SENSORS_TOTAL];

    /**
     * 啟動配置
     *
     * @param inflater  我啊知
     * @param container 這是
     * @param savedInstanceState 三小
     * @return 玩意兒
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.frag_sensors, container, false);

        mInflater = inflater;
        mContainer = container;
        mHandler = new Handler(Looper.myLooper());

        mBtScan = (Button)rootView.findViewById(R.id.btn_scan);
        mBtScan.setOnClickListener(mOnClick);

        mTxvScanning = (TextView)rootView.findViewById(R.id.txv_scanning);
        mTxvScanning.setVisibility(View.INVISIBLE);

        mTxvPrompt = (TextView)rootView.findViewById(R.id.txv_prompt);
        mTxvIncompatible = (TextView)rootView.findViewById(R.id.txv_incompatible);

        mGlSensorPanel = (GridLayout)rootView.findViewById(R.id.sensor_panel);
        mGlSensorPanel.removeAllViews();

        // 使用模擬模式時，產生模擬資料
        if (ENABLE_MOCK_DATA) {
            for (int i=0;i<6;i++) {
                mMockSensors[i] = new SensorModel();
                mMockSensors[i].radioType = (i%2==0) ? RadioType.ANT : RadioType.BLE;
                mMockSensors[i].manufacturer = (i%2==0) ? "Karnim" : "Tacoball Studio";
                mMockSensors[i].address =  (i%2==0) ? "0x3dac (15788)" : "01:23:45:67:89:ab";
                mMockSensors[i].batteryLevel = 100-i*15;
                switch (i%3) {
                    case 0: mMockSensors[i].sensorType = SensorType.HRM; break;
                    case 1: mMockSensors[i].sensorType = SensorType.CSC; break;
                    case 2: mMockSensors[i].sensorType = SensorType.PWR; break;
                }
            }
        } else {
            mDeviceScanner = new DeviceScanner(getActivity());
            mDeviceScanner.setDeviceScanReceiver(mDeviceReceiver);
        }

        // 這一整段可能獨立出去比較好維護，與前面程式無相關性
        final SharedPreferences pref = getActivity().getSharedPreferences("DEFAULT", Context.MODE_PRIVATE);
        boolean never_hint = pref.getBoolean("scanner.never_hint",false);
        boolean ant_compat = mDeviceScanner.isAntCompatible();
        boolean ble_compat = mDeviceScanner.isBleCompatible();

        if (ant_compat || ble_compat) {
            // 支援其中一種感應器
            if (!never_hint) {
                // 還沒關閉提示，載入與跳出提示
                View vGuide = inflater.inflate(R.layout.dlg_guide_first, container, false);
                popGuide(vGuide);
            }
        } else {
            // 通通都不支援，畫面呈現不支援形式
            lockScanner();
        }

        // 測試用，強制移除 "不要再提示我" 的勾勾
        //pref.edit().putBoolean("scanner.never_hint", false).commit();

        // 載入設定工具
        mSettings = new Settings(getActivity());

        // 測試用，強制移除所有配對裝置
        //mSettings.reset();

        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        mHandler.removeCallbacksAndMessages(null);

        if (!ENABLE_MOCK_DATA) {
            mDeviceScanner.close();
            mDeviceScanner = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        if (!ENABLE_MOCK_DATA) {
            mDeviceScanner.stopScan();
        }
    }

    /**
     * 跳出提示訊息
     */
    private void popGuide(View vGuide) {
        final SharedPreferences pref = getActivity().getSharedPreferences("DEFAULT", Context.MODE_PRIVATE);
        boolean ant_compat = mDeviceScanner.isAntCompatible();
        boolean ble_compat = mDeviceScanner.isBleCompatible();

        // 移除不支援的通訊方式
        TableLayout tbl_proto = (TableLayout)vGuide.findViewById(R.id.tbl_proto);
        if (!ant_compat) tbl_proto.removeViewAt(0);
        if (!ble_compat) tbl_proto.removeViewAt(1);

        // 不再提示處理
        final CheckBox chkNeverHint = (CheckBox)vGuide.findViewById(R.id.chk_never_hint);
        chkNeverHint.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                SharedPreferences.Editor editor = pref.edit();
                if (checked) {
                    editor.putBoolean("scanner.never_hint", true);
                } else {
                    editor.putBoolean("scanner.never_hint", false);
                }
                editor.commit();
            }
        });

        // 顯示對話方塊
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(vGuide);
        builder.setPositiveButton(R.string.scan_guide_isee, null);
        builder.create().show();
    }

    /**
     * 鎖定畫面，不支援所有感應器時會使用
     */
    private void lockScanner() {
        mBtScan.setVisibility(View.INVISIBLE);
        mTxvPrompt.setVisibility(View.INVISIBLE);
        mTxvIncompatible.setVisibility(View.VISIBLE);
    }

    /**
     * 增加一個感應器資訊
     *
     * @param model 感應器資訊
     */
    private void addSensor(SensorModel model) {
        // TODO: 如果掃描到不會用到的感應器，不要做新增動作

        LinearLayout ly_sensorInfo = (LinearLayout)mInflater.inflate(R.layout.subview_sensor, mContainer, false);
        ly_sensorInfo.setOnClickListener(mOnClick);
        ly_sensorInfo.setTag(model.address);

        mViewList.add(ly_sensorInfo);
        mModelList.add(model);

        // 加到 GridLayout 裡面
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.rightMargin = 2;
        lp.bottomMargin = 2;
        mGlSensorPanel.addView(ly_sensorInfo, lp);

        updateSensor(model);
    }

    /**
     * 感應器資訊
     *
     * @param model 感應器資訊
     */
    private void updateSensor(SensorModel model) {
        LinearLayout lySensorInfo = (LinearLayout)mContainer.findViewWithTag(model.address);

        // 這種情況有發生的可能性 XD
        if (lySensorInfo==null) return;

        // 以下要改為 add/update 共用
        ImageView ivSensorIcon   = (ImageView)lySensorInfo.findViewById(R.id.iv_sensor_icon);
        ImageView ivBatteryIcon  = (ImageView)lySensorInfo.findViewById(R.id.iv_battery);
        ImageView ivSignalIcon   = (ImageView)lySensorInfo.findViewById(R.id.iv_signal);
        TextView txvSensorName   = (TextView)lySensorInfo.findViewById(R.id.txv_sensor_name);
        TextView txvManufacturer = (TextView)lySensorInfo.findViewById(R.id.txv_manufacturer);
        TextView txvAddress      = (TextView)lySensorInfo.findViewById(R.id.txv_sensor_addr);
        TextView txvBattery      = (TextView)lySensorInfo.findViewById(R.id.txv_battery);

        int sensorIconRes = R.drawable.sensor_ant_heart;
        int sensorNameRes = R.string.sensor_hrm;

        switch(model.sensorType) {
            case HRM:
                sensorNameRes = R.string.sensor_hrm;
                sensorIconRes = (model.radioType == RadioType.ANT) ?
                        R.drawable.sensor_ant_heart :
                        R.drawable.sensor_ble_heart;
                break;
            case CSC:
                sensorNameRes = R.string.sensor_cadspd;
                sensorIconRes = (model.radioType == RadioType.ANT) ?
                        R.drawable.sensor_ant_cadspd :
                        R.drawable.sensor_ble_cadspd;
                break;
            case PWR:
                sensorNameRes = R.string.sensor_power;
                sensorIconRes = (model.radioType == RadioType.ANT) ?
                        R.drawable.sensor_ant_power :
                        R.drawable.sensor_ble_power;
                break;
        }

        // 感應器示意圖
        ivSensorIcon.setImageResource(sensorIconRes);

        // 這寫法會出現看不懂的感應器名稱，還是別用好了
        // *  BLE: HRM
        // * ANT+: Device:1105
        //txvSensorName.setText(model.name);

        // 名稱/廠牌/ID
        txvSensorName.setText(sensorNameRes);
        txvManufacturer.setText(model.manufacturer);
        if (model.radioType==RadioType.ANT) {
            // TODO: 這一段切割成 method 比較好，這裡和 Toast 都會用到
            int idDec = Integer.parseInt(model.address, 16);
            String antId = String.format("0x%04x (%d)", idDec, idDec);
            txvAddress.setText(antId);
        } else {
            txvAddress.setText(model.address);
        }

        // 電量資訊
        String batteryText = (model.batteryLevel<101) ? String.format("%d%%", model.batteryLevel) : "";
        txvBattery.setText(batteryText);
        ivBatteryIcon.setImageLevel(model.batteryLevel);

        // 訊號強度資訊
        ivSignalIcon.setImageLevel(3);

        // 配對狀況
        int borderRes = model.paired ? R.drawable.border_sensor_selected : R.drawable.border_sensor;
        lySensorInfo.setBackgroundResource(borderRes);
    }

    /**
     * 掃描感應器
     */
    private void scanSensors() {
        // 清空感應器列表與計數
        mSensorCount = 0;
        mScanCounter = 15;  // 掃描 15 秒
        mGlSensorPanel.removeAllViews();

        // 顯示等待文字
        final String tpl = getActivity().getResources().getString(R.string.scanning);
        String msg = String.format(tpl, mScanCounter);
        mTxvScanning.setText(msg);
        mTxvScanning.setVisibility(View.VISIBLE);

        // 隱藏掃描按鈕
        mBtScan.setVisibility(View.INVISIBLE);

        // 隱藏提示訊息
        mTxvPrompt.setVisibility(View.INVISIBLE);

        if (ENABLE_MOCK_DATA) {
            // 假資料掃描 (介面測試用)
            mHandler.post(mMockDataTask);
        } else {
            // 真實掃描
            mDeviceScanner.startScan();
        }

        // 倒數計時
        Runnable waiting = new Runnable() {
            @Override
            public void run() {
                mScanCounter--;
                if (mScanCounter==0) {
                    // 時間到 (真實掃描需要停止硬體控制)
                    mBtScan.setVisibility(View.VISIBLE);
                    mTxvScanning.setVisibility(View.INVISIBLE);
                    if (!ENABLE_MOCK_DATA) {
                        mDeviceScanner.stopScan();
                    }
                } else {
                    // 時間還沒到
                    String msg = String.format(tpl, mScanCounter);
                    mTxvScanning.setText(msg);
                    mHandler.postDelayed(this, 1000);
                }
            }
        };
        mHandler.postDelayed(waiting, 1000);
    }

    /**
     * 選取感應器
     */
    private void selectSensor(int idx) {
        // 如果已經選過了，就忽略這個事件
        SensorModel model = mModelList.get(idx);
        //if (model.paired) return; // 除錯時，拿掉這行比較方便

        // 更新已選取裝置的視覺
        model.paired = true;
        updateSensor(model);

        // 同類型裝置，限制只能選一個，另一個要排除
        LinearLayout view = (LinearLayout) mViewList.get(idx);
        for (int i=0;i<mModelList.size();i++) {
            if (i==idx) continue;
            SensorModel anotherModel = mModelList.get(i);
            if (anotherModel.sensorType==model.sensorType && anotherModel.paired) {
                LinearLayout anotherView = (LinearLayout) mViewList.get(i);
                anotherModel.paired = false;
                updateSensor(anotherModel);
            }
        }

        // 寫入 SharedPreference
        view.setBackgroundResource(R.drawable.border_sensor_selected);

        DeviceReceiver.Radio radio = model.radioType.equals(RadioType.ANT) ?
            DeviceReceiver.Radio.ANT :
            DeviceReceiver.Radio.BLE;

        // TODO: 提示訊息改用譯文處理
        String msg = "";
        switch(model.sensorType) {
            case HRM:
                mSettings.setHeartRate(model.address, radio);
                msg = String.format("心率計 %s 已配對", model.address);
                break;
            case CSC:
                mSettings.setSpeedCadence(model.address, radio);
                msg = String.format("踏頻計 %s 已配對", model.address);
                break;
            case PWR:
                mSettings.setPower(model.address, radio);
                msg = String.format("功率計 %s 已配對", model.address);
                break;
        }

        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();

        // 除錯用，顯示偏好設定於 logcat
        mSettings.dump();
    }

    // 產生模擬掃描資料
    private Runnable mMockDataTask = new Runnable() {

        @Override
        public void run() {
            addSensor(mMockSensors[mSensorCount]);
            mSensorCount++;

            if (mSensorCount<MOCK_SENSORS_TOTAL) {
                mHandler.postDelayed(mMockDataTask, 200);
            } else {
                // TODO: 顯示烤吐司提示配對結果摘要
            }
        }

    };

    // Click 處理
    private View.OnClickListener mOnClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            if (view==mBtScan) {
                scanSensors();
                return;
            }

            int idx = mViewList.indexOf(view);
            if (idx>=0) {
                selectSensor(idx);
            }
        }

    };

    // 感應器掃描結果回報
    private DeviceReceiver mDeviceReceiver = new DeviceReceiver() {

        @Override
        public void onDeviceFound(Bundle devData) {
            SensorModel model = createModel(devData);
            if (model.sensorType!=SensorType.UNKNOWN) {
                addSensor(model);
            }
        }

        @Override
        public void onDeviceUpdate(Bundle devData) {
            SensorModel model = createModel(devData);
            if (model.sensorType!=SensorType.UNKNOWN) {
                updateSensor(model);
            }
        }

        /**
         * DeviceScanner 回傳資訊轉換 UI 輸出資訊
         *
         * @param devData
         * @return
         */
        private SensorModel createModel(Bundle devData) {
            SensorModel model = new SensorModel();

            // 通訊方式
            int radio = devData.getInt(DeviceReceiver.KI_PROTOCOL);
            model.radioType = (radio==Radio.ANT.ordinal()) ? RadioType.ANT : RadioType.BLE;

            // 感應器 ID
            String id = devData.getString(DeviceReceiver.KS_ID);
            model.address = id;
            /*
            if (model.radioType == RadioType.ANT) {
                int ant_id = Integer.parseInt(id, 16);
                model.address = String.format("0x%04x (%d)", ant_id, ant_id);
            } else {
                model.address = id;
            }
            */

            // 感應器名稱
            model.name = devData.getString(DeviceReceiver.KS_NAME);

            // 廠商名稱，銷售商優先，製造商其次
            String vendor       = devData.getString(DeviceReceiver.KS_VENDOR);
            String manufacturer = devData.getString(DeviceReceiver.KS_MANUFACTURER);
            if (!vendor.equals("")) {
                model.manufacturer = vendor;
            } else {
                model.manufacturer = manufacturer;
            }

            // 電量 (因為 level-list 不能用負數，所以無電量時改用 101)
            model.batteryLevel = devData.getInt(DeviceReceiver.KI_BATTERY);
            if (model.batteryLevel<0) model.batteryLevel = 101;

            // 訊號強度
            int rssi = devData.getInt(DeviceReceiver.KI_RSSI);
            if (rssi>0) {
                // 無訊號資訊
                model.signalLevel = 4;
            } else {
                // RSSI 級距
                // TODO: 找時間移動感應器進行修正
                model.signalLevel = 0;
                if (rssi>-200) model.signalLevel++;
                if (rssi>-100) model.signalLevel++;
                if (rssi>-60)  model.signalLevel++;

                String msg = String.format("sensor:%s, rssi:%d, level=%d", model.address, rssi, model.signalLevel);
                Log.d(TAG, msg);
            }

            // 感應器類型
            Type type = Type.values()[devData.getInt(DeviceReceiver.KI_TYPE)];
            switch(type) {
                case HRM:
                    model.sensorType = SensorType.HRM;
                    break;
                case SPDCAD:
                    model.sensorType = SensorType.CSC;
                    break;
                case PWR:
                    model.sensorType = SensorType.PWR;
                    break;
                default:
                    model.sensorType = SensorType.UNKNOWN;
            }

            // 配對狀況
            model.paired = mSettings.isPaired(model.address);

            return model;
        }

    };

}