package org.tacoball.sport.cowardlybike;

import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.view.View;
import android.widget.TextView;

/**
 * 測試主介面
 */
public class BikePanelTest extends ActivityInstrumentationTestCase2<FragmentTestActivity> {

    PanelFragment fragment;
    View view;

    TextView txvPower;

    public BikePanelTest() {
        super(FragmentTestActivity.class);
    }

    public void setUp() throws Exception {
        super.setUp();

        fragment = new PanelFragment();
        getActivity()
            .getFragmentManager()
            .beginTransaction()
            .add(R.id.container, fragment)
            .commit();
        getInstrumentation().waitForIdleSync();
        view = fragment.getView();
        txvPower = (TextView)view.findViewById(R.id.txv_power);
    }

    /**
     * 測試正常功率值
     */
    @UiThreadTest
    public void test01_NormalPower() {
        int actualPwr;

        fragment.updatePower(0);
        actualPwr = Integer.parseInt(txvPower.getText().toString());
        assertEquals(0, actualPwr);

        fragment.updatePower(999);
        actualPwr = Integer.parseInt(txvPower.getText().toString());
        assertEquals(999, actualPwr);
    }

    /**
     * 測試異常功率值
     */
    @UiThreadTest
    public void test02_BadPower() {
        int actualPwr;

        fragment.updatePower(-1);
        actualPwr = Integer.parseInt(txvPower.getText().toString());
        assertEquals(0, actualPwr);

        fragment.updatePower(1000);
        actualPwr = Integer.parseInt(txvPower.getText().toString());
        assertEquals(999, actualPwr);
    }

    /**
     * 測試指針動畫效能
     * (注意！這個測試可能會造成電腦當機)
     */
    @UiThreadTest
    public void test03_PinPerformance() {
        final int TRANSFORM_COUNT = 5000; // 這個值開太大，而且跑模擬器時會當機喔
        final int TIME_LIMIT = 500;       // 設定 0 可以故意錯誤，觀察速度用

        int i, speed;
        long begin, elapsed;

        begin = System.currentTimeMillis();
        for (i=0;i<TRANSFORM_COUNT;i++) {
            speed = i%60;
            fragment.updateSpeed(speed);
        }
        elapsed = System.currentTimeMillis()-begin;

        double ppm = (double)TRANSFORM_COUNT/elapsed;
        assertTrue(String.format("Pin rotate too slowly. (%d ms, %.2f prod/ms)", elapsed, ppm), elapsed<=TIME_LIMIT);
    }

}
