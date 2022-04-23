package com.example.uavhostcomputer.activity;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.uavhostcomputer.R;
import com.example.uavhostcomputer.adapter.Ble_adapter;
import com.example.uavhostcomputer.broadcast.ScanBroadcastReceiver;
import com.example.uavhostcomputer.tool_class.BlueToothTool;

import java.util.ArrayList;
import java.util.List;

public class FindDeviceActivity extends BaseActivity {
    private static final String TAG = "FindDeviceActivity";
    private boolean isScanning = false;
    private List<BluetoothDevice> m_devices;
    private List<Integer> m_rssi;
    private Ble_adapter m_adapter;
    private ListView device_list;
    private ProgressBar scanning;
    private ScanBroadcastReceiver scanBroadcastReceiver;


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_device);

        Toast.makeText(FindDeviceActivity.this, "正在扫描设备", Toast.LENGTH_LONG).show();

        viewInit();
        dataInit();
        registerScanBroadcastReceiver();
        BlueToothTool.getBlueToothAdapter().startDiscovery();
    }

    @SuppressLint("MissingPermission")
    private void viewInit(){
        device_list = findViewById(R.id.device_list);
        scanning = findViewById(R.id.scanning);

        device_list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id)->{
            if(BlueToothTool.getBlueToothAdapter().isDiscovering()){
                BlueToothTool.getBlueToothAdapter().cancelDiscovery();
            }
            BluetoothDevice selectedDevice = m_devices.get(position);
            Intent intent = new Intent();
            intent.putExtra("device", selectedDevice);
            setResult(RESULT_OK, intent);
            finish();
        });
    }

    private void dataInit(){
        m_devices = new ArrayList<>();
        m_rssi = new ArrayList<>();
        m_adapter = new Ble_adapter(FindDeviceActivity.this, m_devices, m_rssi);
        device_list.setAdapter(m_adapter);
        m_adapter.notifyDataSetChanged();
        BlueToothTool.setBle_adapter(m_adapter);
        BlueToothTool.setDevices(m_devices);
        BlueToothTool.setRssi(m_rssi);
    }

    private void registerScanBroadcastReceiver(){
        //注册蓝牙扫描广播
        if(scanBroadcastReceiver == null){
            scanBroadcastReceiver = new ScanBroadcastReceiver();
            scanBroadcastReceiver.setScanning(scanning);
            IntentFilter filter = new IntentFilter();
            //开始扫描
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            //结束扫描
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            //扫描中，返回结果
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            //扫描模式改变
            filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
            //注册广播监听
            registerReceiver(scanBroadcastReceiver, filter);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanning.setVisibility(View.GONE);
        BlueToothTool.getBlueToothAdapter().cancelDiscovery();
        unregisterReceiver(scanBroadcastReceiver);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED);
    }
}
