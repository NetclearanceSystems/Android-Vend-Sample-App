package com.netclearance.vendtest;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.UUID;
import com.netclearance.ncvendsdk.*;
import android.provider.Settings.Secure;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private NCDevice _mDevice;
    private JWTUtil _JWUtil;
    private NCDeviceCallback _mCallBack;
    private BLEPermission blePermission;
    private String device_name;
    private String device_address;
    private static String _terminalUUID = "00431C4A-A7A4-428B-A96D-D92D43C8C7CF"; //006
    public static UUID BLE_SERVICE_UUID = UUID.fromString(_terminalUUID);

    private int connectionThreshold = -80; // Set RSSI value to connect at close or wide range from the terminal
    private boolean _deviceConnected = false;
    private FirebaseAnalytics mFirebaseAnalytics;
    private Bundle bundle = new Bundle();
    private String android_id;

    private byte[] DEXCOMMAND = hexStringToByteArray("DD12121212121212121212121212121212121212");
    private byte[] ACKCOMMAND = hexStringToByteArray("AA12121212121212121212121212121212121212");

    private int dexDataCount = 0;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        _mDevice = NCDevice.getInstance();
        _mCallBack = new BLECallBack();
        _JWUtil = JWTUtil.getInstance();

        android_id = Secure.getString(this.getContentResolver(), Secure.ANDROID_ID);

        Logger.d("Android Device Id:" + android_id);

        // Obtain the FirebaseAnalytics instance.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        if (_mDevice != null)
        {
            _mDevice.NCDeviceConfig(null, null, null,BLE_SERVICE_UUID, this,_mCallBack);

        }

        checkForBLELocationPermissions(this);

    }


    public void onClickScanBtn(View v)
    {
        TextView tv  = this.findViewById(R.id.message);
        tv.setText("Searching for devices...");


        if (_mDevice != null)
        {
            _mDevice.Init();
            _mDevice.Scan();

        }

        sendFirebaseEvent(_mDevice, "DEVICE_SCAN_STARTED");
    }

    public void onClickDisconnectBtn(View v)
    {
        TextView tv  = this.findViewById(R.id.message);
        tv.setText("Disconnecting from device...");
        Button bt = this.findViewById(R.id.button);

        bt.setVisibility(View.VISIBLE);

        if (_mDevice != null)
        {
            _mDevice.Disconnect();

        }


    }

    public void onClickDexBtn(View v)
    {
        TextView tv  = this.findViewById(R.id.message);
        tv.setText("Beginning DEX Transfer...");
        InitDEX();


    }

    private class BLECallBack extends NCDeviceCallback{


        public BLECallBack() {
            super();
        }

        @Override
        public void OnDeviceDiscovered(BluetoothDevice device, final int rssi) {
            super.OnDeviceDiscovered(device,rssi);

            Button bt = findViewById(R.id.button);

            if(device != null){

                device_name = device.getName();
                device_address = device.getAddress();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView tv  = findViewById(R.id.message);
                        tv.setText("Device: " + device_name + ": " + rssi + "dB : " + device_address );
                    }
                });


                if(_deviceConnected == false){

                    if(checkForVicinity(rssi) && checkForName(device_name,"CokePay")){

                        bt.setVisibility(View.INVISIBLE);

                        InitDEX();
                        _mDevice.Connect();

                        sendFirebaseEvent(_mDevice, "DEVICE_FOUND");
                    }
                    else {

                        bt.setVisibility(View.VISIBLE);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView tv = findViewById(R.id.message);
                                tv.setText("Device too far or not found: Searching...");
                            }
                        });

                    }
                }


            }


        }

        @Override
        public void OnDeviceConnected(BluetoothDevice device) {
            super.OnDeviceConnected(device);

            _deviceConnected = true;


            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv  = findViewById(R.id.message);
                    tv.setText("Device Connected");

                }
            });

            // Logger.d("Initializing: setting write type and enabling notification");
            // _mDevice.enableCharacteristicNotification(_mDevice.get_mGatt(), _mReadCommandChar);
            // _mDevice.enableCharacteristicNotification(_mDevice.get_mGatt(), _mReadDataChar);


            sendFirebaseEvent(_mDevice, "DEVICE_CONNECTED");
        }

        @Override
        public void OnDeviceDisconnected(BluetoothDevice device) {
            super.OnDeviceDisconnected(device);

            _deviceConnected = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = findViewById(R.id.message);
                    tv.setText("Device Disconnected");
                    tv.setText("Ready to connect");
                }
            });

            sendFirebaseEvent(_mDevice, "DEVICE_DISCONNECTED");
        }

        @Override
        public void OnDeviceRead() {
            super.OnDeviceRead();
        }

        @Override
        public void OnDeviceError(int err) {


            Logger.d("Got OnDeviceError callback with error code:" + err);


        }

        @Override
        public void OnDeviceWrite() {

            Logger.d("Got OnDeviceWrite callback");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = findViewById(R.id.message);
                    tv.setText("Data sent successfully");
                }
            });
            sendFirebaseEvent(_mDevice, "DATA_SEND_SUCCESS");
        }

        @Override
        public void OnDeviceDataAvailable(byte[] buffer)
        {
            Logger.d("Got new data buffer with size:"  + buffer.length + " bytes");

            Logger.d("Received msg: " + StringUtils.byteArrayToHexString(buffer));

            final int len = buffer.length;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = findViewById(R.id.message);
                    tv.setText("Got Data:"+ len + " bytes");
                }
            });

            sendFirebaseEvent(_mDevice, "DATA_RECEIVED_SUCCESS");

        }

        @Override
        public void OnDeviceDataReceived(int index)
        {
            Logger.d("Received # of bytes:" + index);

           dexDataCount = index;
           if (dexDataCount == 240)
               sendACK();
        }


        @Override
        public void OnDeviceCommandAvailable(byte[] commandBuffer) {


        }

        @Override
        public void OnDeviceCommandReceived(int index) {


        }


        @Override
        public void OnDeviceBatteryRead(final int battLevel) {

            Logger.d("Got battery level callback");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = findViewById(R.id.message);
                    tv.setText("Battery Level:" + battLevel + "%");
                }
            });
        }

        @Override
        public void OnDevicePOSIdRead(final String posid) {

            Logger.d("Got POS Id callback");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView tv = findViewById(R.id.message);
                    tv.setText("POSID:" + posid);
                }
            });
        }

        @Override
        public void OnDeviceAck(int message) {

            Logger.d("Got device ack callback");

        }
    }

    private void InitDEX() {

        if(_mDevice != null){

            _mDevice.SendCommand(DEXCOMMAND);


        }

    }

    private void sendACK(){

        if(_mDevice != null){

            _mDevice.SendCommand(ACKCOMMAND);


        }

    }


    private void sendFirebaseEvent(NCDevice _mDev, String _eventName){

        bundle.putString("DEVICE_NAME", device_name);
        bundle.putString("DEVICE_ADDRESS", device_address);
        bundle.putString("USER_DEVICE_ID", android_id);
        mFirebaseAnalytics.logEvent(_eventName, bundle);
    }

    public boolean checkForVicinity(int signalRSSI)
    {
        if (signalRSSI > connectionThreshold)
            return true;
        else
            return false;


    }

    public boolean checkForName(String name, String value){

        if (value.contentEquals(name))
            return true;
        else
            return false;


    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void checkForBLELocationPermissions(Activity _parentActivity) {

        BLEPermission blePermission;
        blePermission = BLEPermission.getInstance();
        blePermission.hasPermissions(_mDevice, _parentActivity);

    }



    public static class BLEPermission {

        private static final int REQUEST_ENABLE_BT = 1;
        private static final int REQUEST_FINE_LOCATION = 2;

        public static final BLEPermission ourInstance = new BLEPermission();

        public static BLEPermission getInstance() {
            return ourInstance;
        }

        private BLEPermission() {
        }

        /**
         * Checks if parent activity has permission to utilize Bluetooth resources
         *
         * @param mDevice   Netclearance Device Adapter class
         * @param parent    Parent Activity
         */
        @RequiresApi(api = Build.VERSION_CODES.M)
        public boolean hasPermissions(NCDevice mDevice, Activity parent) {
            // if (mDevice == null || !btAdapter.isEnabled()) {
            if (mDevice == null) {
                requestBluetoothEnable(parent);
                return false;
            } else if (!hasLocationPermissions(parent)) {
                requestLocationPermission(parent);
                return false;
            }
            return true;
        }

        /**
         * Starts user dialog to enable Bluetooth in case is turned off
         *
         * @param context Parent Activity
         */
        public void requestBluetoothEnable(Activity context) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            context.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Logger.d("Requested user enables Bluetooth. Try starting the scan again.");
        }

        /**
         * Checks if the parent application has location permissions
         *
         * @param context Parent Activity
         */
        @RequiresApi(api = Build.VERSION_CODES.M)
        public boolean hasLocationPermissions(Activity context) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        }

        /**
         * Starts user dialog to enable location services
         *
         * @param context Parent Activity
         */
        @RequiresApi(api = Build.VERSION_CODES.M)
        public void requestLocationPermission(Activity context) {
            context.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
            Logger.d("Requested user enable Location. Try starting the scan again.");
        }

    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

}