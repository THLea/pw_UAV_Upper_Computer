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
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.uavhostcomputer.R;
import com.example.uavhostcomputer.adapter.Ble_adapter;
import com.example.uavhostcomputer.broadcast.ScanBroadcastReceiver;
import com.example.uavhostcomputer.tool_class.BlueToothTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FindDeviceActivity extends BaseActivity {
    private static final String TAG = "FindDeviceActivity";
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

        //初始化控件，数据，注册蓝牙扫描广播，开始扫描蓝牙
        viewInit();
        dataInit();
        registerScanBroadcastReceiver();
        BlueToothTool.getBlueToothAdapter().startDiscovery();
    }

    //初始化控件
    @SuppressLint("MissingPermission")
    private void viewInit(){
        device_list = findViewById(R.id.device_list);
        scanning = findViewById(R.id.scanning);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        //设置列表的点击事件
        device_list.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id)->{
            //如果在扫描就停止扫描
            if(BlueToothTool.getBlueToothAdapter().isDiscovering()){
                BlueToothTool.getBlueToothAdapter().cancelDiscovery();
            }
            //获取选中的蓝牙设备，传入Intent中，并退出，在上一个活动中接收这个设备对象
            BluetoothDevice selectedDevice = m_devices.get(position);
            Intent intent = new Intent();
            intent.putExtra("device", selectedDevice);
            setResult(RESULT_OK, intent);
            finish();
        });
    }
    //数据初始化
    private void dataInit(){
        m_devices = new ArrayList<>();
        m_rssi = new ArrayList<>();
        //设置自定义ListView的Adapter，并刷新
        m_adapter = new Ble_adapter(FindDeviceActivity.this, m_devices, m_rssi);
        device_list.setAdapter(m_adapter);
        m_adapter.notifyDataSetChanged();
        //把数据传入BlueToothTool类中，为了在蓝牙扫描广播接收器中添加设备信息
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
    //活动销毁时进行的善后操作
    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanning.setVisibility(View.GONE);
        if(BlueToothTool.getBlueToothAdapter().isDiscovering())
            BlueToothTool.getBlueToothAdapter().cancelDiscovery();
        unregisterReceiver(scanBroadcastReceiver);
        BlueToothTool.setBle_adapter(null);
        BlueToothTool.setDevices(null);
        BlueToothTool.setRssi(null);
    }
    //点击下方退出键时执行，不传给上一级活动数据
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED);
        finish();
    }
    //点击左上角返回时运行，不传给上一级活动数据
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:{
                onBackPressed();
            }
        }
        return true;
    }
}
