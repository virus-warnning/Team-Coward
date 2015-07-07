package org.tacoball.sport.cowardlybike;

import android.app.Fragment;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tacoball.sport.signals.Utils;
import com.tacoball.sport.signals.hal.DeviceScanner;

public class AboutFragment extends Fragment {

    private static final String TAG = "AboutFragment";

    /**
     * 啟動配置
     *
     * @param inflater           xxx
     * @param container          xxx
     * @param savedInstanceState xxx
     * @return                   xxx
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.frag_about, container, false);

        // 讀取套件版本，更新到介面上
        String version = "unknown";
        try {
            version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
        } catch(PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }

        Resources res = getResources();
        DeviceScanner ds = new DeviceScanner(getActivity(), null);

        String antFeature = ds.isAntCompatible() ? res.getString(R.string.available) : res.getString(R.string.not_available);
        String antVersion = ds.getAntRadioServiceVersion();
        String antPlusVersion = ds.getAntPlusPluginsVersion();
        String compatible = res.getString(R.string.compatible);
        String incompatible = res.getString(R.string.incompatible);
        String bleCompat = ds.isBleCompatible() ? compatible : incompatible;

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        if (antVersion==null) antVersion = incompatible;
        if (antPlusVersion==null) antPlusVersion = incompatible;

        TextView txvVersion = (TextView)rootView.findViewById(R.id.txv_version);
        txvVersion.setText(version);

        TextView txvAntFeature = (TextView)rootView.findViewById(R.id.txv_ant_feature);
        txvAntFeature.setText(antFeature);

        TextView txvAntVersion = (TextView)rootView.findViewById(R.id.txv_ant_ver);
        txvAntVersion.setText(antVersion);

        TextView txvAntPlusVersion = (TextView)rootView.findViewById(R.id.txv_antplus_ver);
        txvAntPlusVersion.setText(antPlusVersion);

        TextView txvAndroidVersion = (TextView)rootView.findViewById(R.id.txv_android_ver);
        txvAndroidVersion.setText(Build.VERSION.RELEASE);

        TextView txvBleCompat = (TextView)rootView.findViewById(R.id.txv_ble_compat);
        txvBleCompat.setText(bleCompat);

        TextView txvModel = (TextView)rootView.findViewById(R.id.txv_model);
        txvModel.setText(Build.DEVICE);

        TextView txvResX = (TextView)rootView.findViewById(R.id.txv_res_x);
        txvResX.setText(String.format("%d", size.x));

        TextView txvResY = (TextView)rootView.findViewById(R.id.txv_res_y);
        txvResY.setText(String.format("%d", size.y));

        TextView txtSignalService = (TextView)rootView.findViewById(R.id.txv_signal_service);
        txtSignalService.setText(Utils.getVersion());

        return rootView;
    }

}
