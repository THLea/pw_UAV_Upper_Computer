package com.example.uavhostcomputer.broadcast;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.view.View;
import android.widget.ProgressBar;

import androidx.core.app.ActivityCompat;

import com.example.uavhostcomputer.activity.FindDeviceActivity;
import com.example.uavhostcomputer.tool_class.BlueToothTool;

public class ScanBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ScanBroadcastReceiver";
    ProgressBar m_scanning;

    public void setScanning(ProgressBar scanning){
        m_scanning = scanning;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "intent = "+intent.toString());
        String action = intent.getAction();

        if(action != null){
            switch (action){
                case BluetoothAdapter.ACTION_SCAN_MODE_CHANGED:{
                    Log.d(TAG, "扫描模式改变");
                    break;
                }
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:{
                    Log.d(TAG, "扫描开始");
                    m_scanning.setVisibility(View.VISIBLE);
                    break;
                }
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:{
                    Log.d(TAG, "扫描结束");
                    m_scanning.setVisibility(View.GONE);
                    break;
                }
                case BluetoothDevice.ACTION_FOUND:{
                    Log.d(TAG, "发现设备");
                    //获取蓝牙设备
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(device != null && device.getName() != null){
                        int rssi = -120;
                        Bundle extras = intent.getExtras();
                        String lv = "";
                        if(extras != null){
                            //获取信号强度
                            rssi = extras.getShort(BluetoothDevice.EXTRA_RSSI);
                        }
                        Log.d(TAG, "rssi="+rssi+" name="+device.getName()+" address="+device.getAddress()+" lv="+lv);
                        //更新扫描到的设备列表
                        BlueToothTool.getDevices().add(device);
                        BlueToothTool.getRssi().add(rssi);
                        BlueToothTool.getBle_adapter().notifyDataSetChanged();
                    }
                    break;
                }
            }
        }
    }
}
