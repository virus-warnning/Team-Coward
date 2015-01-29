package org.tacoball.sport.cowardlybike;

import android.app.Fragment;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * 登入畫面
 */
public class LoginFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.frag_login, container, false);

        // 讀取套件版本，更新到介面上
        String version = "unknown";
        try {
            version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
        } catch(PackageManager.NameNotFoundException e) {}

        TextView txvVersion = (TextView)rootView.findViewById(R.id.txv_version);
        txvVersion.setText(version);

        return rootView;
    }
}
