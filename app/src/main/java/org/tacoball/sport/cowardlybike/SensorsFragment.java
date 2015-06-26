package org.tacoball.sport.cowardlybike;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
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
import com.tacoball.sport.signals.Utils;
import com.tacoball.sport.signals.hal.DeviceInfo;
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
    Button mBtDismiss;
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

    // Sensor 視覺資料
    private List<View> mViewList = new ArrayList<View>();
    private List<DeviceInfo> mModelList = new ArrayList<DeviceInfo>();

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

        mBtDismiss = (Button)rootView.findViewById(R.id.btn_dismiss);
        mBtDismiss.setOnClickListener(mOnClick);

        mTxvScanning = (TextView)rootView.findViewById(R.id.txv_scanning);
        mTxvScanning.setVisibility(View.INVISIBLE);

        mTxvPrompt = (TextView)rootView.findViewById(R.id.txv_prompt);
        mTxvIncompatible = (TextView)rootView.findViewById(R.id.txv_incompatible);

        mGlSensorPanel = (GridLayout)rootView.findViewById(R.id.sensor_panel);
        mGlSensorPanel.removeAllViews();

        // 配置裝置掃瞄器
        mDeviceScanner = new DeviceScanner(getActivity());
        mDeviceScanner.setDeviceScanReceiver(mDeviceReceiver);

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

        loadSensors();

        // 測試用，強制移除所有配對裝置
        //mSettings.reset();

        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDeviceScanner.close();
        mDeviceScanner = null;

        // 假如掃描中就關閉程式，可能會殘留倒數計時排程以及更新裝置排程
        mHandler.removeCallbacks(mWaiting);
        // Log.d(TAG, "onDestroy()");
    }

    @Override
    public void onPause() {
        super.onPause();
        mDeviceScanner.stopScan();
        // Log.d(TAG, "onPause()");
    }

    /**
     * 跳出提示訊息
     *
     * TODO: 防止在碼錶頁跳出
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
        // TODO: 這裡要防止在碼錶頁顯示
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
                editor.apply();
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
     * @param devInfo 感應器資訊
     */
    private void addSensor(DeviceInfo devInfo) {
        // TODO: 如果掃描到不會用到的感應器，不要做新增動作
        LinearLayout ly_sensorInfo = (LinearLayout)mInflater.inflate(R.layout.subview_sensor, mContainer, false);
        ly_sensorInfo.setOnClickListener(mOnClick);
        ly_sensorInfo.setTag(devInfo.getId());

        mViewList.add(ly_sensorInfo);
        mModelList.add(devInfo);

        // 加到 GridLayout 裡面
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.rightMargin = 2;
        lp.bottomMargin = 2;
        mGlSensorPanel.addView(ly_sensorInfo, lp);

        // 第一個感應器，有時候會更新失敗，需要稍微延遲一下下
        // TODO: 如果掃描中就關閉應用程式，這裡可能有 NullPointerException 的風險
        final DeviceInfo devInfoF = devInfo;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSensor(devInfoF);
            }
        }, 100);

    }

    /**
     * 感應器資訊
     *
     * @param devInfo 感應器資訊
     */
    private void updateSensor(DeviceInfo devInfo) {
        LinearLayout lySensorInfo = (LinearLayout)mContainer.findViewWithTag(devInfo.getId());

        // 這種情況有發生的可能性 XD
        if (lySensorInfo==null) {
            Log.d(TAG, "fuck");
            return;
        }

        // 以下要改為 add/update 共用
        ImageView ivSensorIcon   = (ImageView)lySensorInfo.findViewById(R.id.iv_sensor_icon);
        ImageView ivBatteryIcon  = (ImageView)lySensorInfo.findViewById(R.id.iv_battery);
        ImageView ivSignalIcon   = (ImageView)lySensorInfo.findViewById(R.id.iv_signal);
        TextView txvSensorName   = (TextView)lySensorInfo.findViewById(R.id.txv_sensor_name);
        TextView txvManufacturer = (TextView)lySensorInfo.findViewById(R.id.txv_manufacturer);
        TextView txvAddress      = (TextView)lySensorInfo.findViewById(R.id.txv_sensor_addr);
        TextView txvBattery      = (TextView)lySensorInfo.findViewById(R.id.txv_battery);
        TextView txvFirmware     = (TextView)lySensorInfo.findViewById(R.id.txv_sensor_firmware);

        int sensorIconRes = R.drawable.sensor_ant_heart;
        int sensorNameRes = R.string.sensor_hrm;

        switch(devInfo.getType()) {
            case HRM:
                sensorNameRes = R.string.sensor_hrm;
                sensorIconRes = (devInfo.getRadio()==DeviceInfo.Radio.ANT) ?
                        R.drawable.sensor_ant_heart :
                        R.drawable.sensor_ble_heart;
                break;
            case CSC:
                sensorNameRes = R.string.sensor_cadspd;
                sensorIconRes = (devInfo.getRadio()==DeviceInfo.Radio.ANT) ?
                        R.drawable.sensor_ant_cadspd :
                        R.drawable.sensor_ble_cadspd;
                break;
            case PWR:
                sensorNameRes = R.string.sensor_power;
                sensorIconRes = (devInfo.getRadio()==DeviceInfo.Radio.ANT) ?
                        R.drawable.sensor_ant_power :
                        R.drawable.sensor_ble_power;
                break;
        }

        // 感應器示意圖
        ivSensorIcon.setImageResource(sensorIconRes);

        // 這寫法會出現看不懂的感應器名稱，還是別用好了
        // * BLE: HRM
        // * ANT+: Device:1105
        //txvSensorName.setText(model.name);

        // 預設名稱
        txvSensorName.setText(sensorNameRes);

        // BLE 名稱
        /*
        if (devInfo.getRadio()==DeviceInfo.Radio.BLE) {
            if (!devInfo.getName().equals("")) {
                txvSensorName.setText(devInfo.getName());
            }
        }
        */

        // 海綿體
        String label = getActivity().getString(R.string.scan_firmware);
        String rev   = devInfo.getFirmwareVer();
        if (!rev.equals("")) {
            txvFirmware.setText(String.format("%s: %s", label, rev));
        } else {
            txvFirmware.setText(String.format(""));
        }

        // 廠牌 (字首轉大寫)
        String manu = devInfo.getManufacturer();
        if (manu.length()>1) {
            manu = manu.substring(0,1).toUpperCase() + manu.substring(1);
        }
        txvManufacturer.setText(manu);

        // ID
        if (devInfo.getRadio()==DeviceInfo.Radio.ANT) {
            // TODO: 這一段切割成 method 比較好，這裡和 Toast 都會用到
            int idDec = Integer.parseInt(devInfo.getId(), 16);
            String antId = String.format("0x%04x (%d)", idDec, idDec);
            txvAddress.setText(antId);
        } else {
            txvAddress.setText(devInfo.getId());
        }

        // 電量資訊
        String batteryText = (devInfo.getBattery()<101) ? String.format("%d%%", devInfo.getBattery()) : "";
        txvBattery.setText(batteryText);
        ivBatteryIcon.setImageLevel(devInfo.getBattery());

        // 訊號強度資訊
        ivSignalIcon.setImageLevel(3);

        // 配對狀況
        int borderRes = R.drawable.border_sensor;
        if (devInfo.isPaired()) {
            if (devInfo.isFound()) {
                borderRes = R.drawable.border_sensor_available;
            } else {
                borderRes = R.drawable.border_sensor_paired;
            }
        }
        lySensorInfo.setBackgroundResource(borderRes);
    }

    /**
     * 載入已配對的感應器
     */
    private void loadSensors() {
        List<DeviceInfo> sensors = mSettings.getPairedSensors();

        // 隱藏提示訊息
        if (sensors.size()>0) {
            mTxvPrompt.setVisibility(View.INVISIBLE);
        }

        // 列出已配對裝置
        for (DeviceInfo devInfo : sensors) {
            addSensor(devInfo);
        }
    }

    /**
     * 掃描感應器
     * - 如果藍牙已開啟，從內部呼叫
     * - 如果藍牙未開啟，等開啟後從 Activity 呼叫
     */
    public void scanSensors() {
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
        mBtDismiss.setVisibility(View.INVISIBLE);

        // 隱藏提示訊息
        mTxvPrompt.setVisibility(View.INVISIBLE);

        // 開始掃描
        mDeviceScanner.startScan();
        mHandler.postDelayed(mWaiting, 1000);
    }

    /**
     * 選取感應器
     */
    private void selectSensor(int idx) {
        // 如果已經選過了，就忽略這個事件
        DeviceInfo devInfo = mModelList.get(idx);
        //if (model.paired) return; // 除錯時，拿掉這行比較方便

        // 更新已選取裝置的視覺
        devInfo.setPaired(true);
        updateSensor(devInfo);

        // 同類型裝置，限制只能選一個，另一個要排除
        LinearLayout view = (LinearLayout) mViewList.get(idx);
        for (int i=0;i<mModelList.size();i++) {
            if (i==idx) continue;
            DeviceInfo anotherDevInfo = mModelList.get(i);
            if (anotherDevInfo.getType()==devInfo.getType() && anotherDevInfo.isPaired()) {
                anotherDevInfo.setPaired(false);
                updateSensor(anotherDevInfo);
            }
        }

        // 寫入 SharedPreference
        view.setBackgroundResource(R.drawable.border_sensor_paired);

        // TODO: 提示訊息改用譯文處理
        String msg = "";
        switch(devInfo.getType()) {
            case HRM:
                mSettings.setHeartRate(devInfo.getId(), devInfo.getRadio());
                msg = String.format("心率計 %s 已配對", devInfo.getId());
                break;
            case CSC:
                mSettings.setSpeedCadence(devInfo.getId(), devInfo.getRadio());
                msg = String.format("踏頻計 %s 已配對", devInfo.getId());
                break;
            case PWR:
                mSettings.setPower(devInfo.getId(), devInfo.getRadio());
                msg = String.format("功率計 %s 已配對", devInfo.getId());
                break;
        }

        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();

        // 除錯用，顯示偏好設定於 logcat
        //mSettings.dump();
    }

    // 解除配對
    private void dismissSensors() {
        mGlSensorPanel.removeAllViews();
        mSettings.resetSensors();

        // 除錯用，顯示偏好設定於 logcat
        //mSettings.dump();
    }

    // Click 處理
    private View.OnClickListener mOnClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            if (view==mBtScan) {
                Activity activity = getActivity();
                if (Utils.hasBleFeature(activity) && !Utils.isBluetoothOpened(activity)) {
                    //
                    Intent intentBluetooth = new Intent();
                    intentBluetooth.setAction(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    activity.startActivityForResult(intentBluetooth, TeamCowardActivity.RESULT_SCANNER_ENABLE_BT);
                } else {
                    scanSensors();
                }
                return;
            }

            if (view==mBtDismiss) {
                dismissSensors();
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
        public void onDeviceFound(DeviceInfo devInfo) {
            if (devInfo.getType()!=DeviceInfo.Type.UNKNOWN) {
                addSensor(devInfo);
            }
        }

        @Override
        public void onDeviceUpdate(DeviceInfo devInfo) {
            if (devInfo.getType()!=DeviceInfo.Type.UNKNOWN) {
                updateSensor(devInfo);
            }
        }

    };

    // 倒數計時
    Runnable mWaiting = new Runnable() {
        @Override
        public void run() {
            mScanCounter--;
            if (mScanCounter==0) {
                // 時間到 (真實掃描需要停止硬體控制)
                mBtScan.setVisibility(View.VISIBLE);
                mBtDismiss.setVisibility(View.VISIBLE);
                mTxvScanning.setVisibility(View.INVISIBLE);
                mDeviceScanner.stopScan();
            } else {
                // 時間還沒到
                String tpl = getActivity().getResources().getString(R.string.scanning);
                String msg = String.format(tpl, mScanCounter);
                mTxvScanning.setText(msg);
                mHandler.postDelayed(this, 1000);
            }
        }
    };

}