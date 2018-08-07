package se.berg.thomas.thingshub;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BleScanService extends Service {

    private String TAG = "BleScanService";
    private BluetoothAdapter mBluetoothAdapter;
    private static final long SCAN_PERIODIC_RESTART_PERIOD = 29 * 60 * 1000;    // 29 minutes
    protected ArrayList<ScanFilter> scanFilterList = new ArrayList<>();

    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean isScanRequested  = false;
    private Handler mScanPeriodicRestartHandler;

    public BleScanService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "Bluetooth not supported");
        }
        mBluetoothAdapter = bluetoothManager.getAdapter();

        mScanPeriodicRestartHandler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        scanFilterList = intent.getParcelableArrayListExtra("SCAN_FILTER");

        isScanRequested = true;

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        if (mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is enabled...starting services");
            startScan();
        } else {
            Log.d(TAG, "Bluetooth is disabled...enabling");
            mBluetoothAdapter.enable();
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        stopScan(); // ToDo: Investigate if this shall be here
    }

    private void startScan() {
        Log.v(TAG, "startScan");
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if ((bluetoothAdapter == null) || !bluetoothAdapter.isEnabled() || !isScanRequested) {
            Log.v(TAG, "startScan not ready, returning");
            return;
        }

        ScanSettings settings;

        if (bluetoothAdapter.isOffloadedScanBatchingSupported()) {
            settings = new ScanSettings.Builder()
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .setReportDelay(1000)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
        } else {
            settings = new ScanSettings.Builder()
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
        }

        if (bluetoothAdapter.isEnabled()) {
            mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            mBluetoothLeScanner.startScan(scanFilterList, settings, mScanCallback);

            if (mScanPeriodicRestartHandler != null) {
                mScanPeriodicRestartHandler.removeCallbacks(scanPeriodicRestartRunnable);
            }
            mScanPeriodicRestartHandler.postDelayed(scanPeriodicRestartRunnable, SCAN_PERIODIC_RESTART_PERIOD);

        } else {
            Log.v(TAG, "startScan: bluetoothAdapter not enabled");
        }


    }

    private void stopScan() {
        Log.v(TAG, "stopScan");

        if (mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
        }

        if (mScanPeriodicRestartHandler != null) {
            mScanPeriodicRestartHandler.removeCallbacks(scanPeriodicRestartRunnable);
        }
    }


    private Runnable scanPeriodicRestartRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "scanPeriodicRestartRunnable");
            stopScan();
            startScan();
        }
    };

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    Log.v(TAG, "Bluetooth state changed ... to ON");
                    startScan();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    Log.v(TAG, "Bluetooth state changed ... to OFF");
                    stopScan();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.v(TAG, result.toString());
            Intent intent = new Intent();
            intent.setAction(ThingsHubCommon.ACTION_ADV);
            intent.putExtra(ThingsHubCommon.EXTRA_SCAN_RESULT, result);
            sendBroadcast(intent);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.v(TAG, "onBatchScanResults");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.v(TAG, "onScanFailed");
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
