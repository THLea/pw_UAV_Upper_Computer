package com.example.uavhostcomputer.tool_class;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;

import com.example.uavhostcomputer.adapter.Ble_adapter;

import java.util.List;

public class BlueToothTool {
    private static BluetoothAdapter m_adapter;
    private static BluetoothManager m_manager;
    private static BluetoothGatt m_gatt;
    private static Ble_adapter m_ble_adapter;
    private static List<BluetoothDevice> m_devices;
    private static List<Integer> m_rssi;


    public static boolean blueToothInit(BluetoothManager manager){
        m_manager = manager;
        m_adapter = manager.getAdapter();
        m_gatt = null;

        return m_adapter != null;
    }

    public static BluetoothManager getBlueToothManager(){
        return m_manager;
    }

    public static BluetoothAdapter getBlueToothAdapter(){
        return m_adapter;
    }

    @SuppressLint({"MissingPermission", "HardwareIds"})
    public static StringBuilder getBlueToothInfo(){
        StringBuilder info = new StringBuilder();
        info.append("name:");  //蓝牙名称
        info.append(m_adapter.getName());
        info.append(" address: ");  //蓝牙地址
        info.append(m_adapter.getAddress());
        info.append("\nMode: ");  //扫描模式
        info.append(m_adapter.getScanMode());
        info.append(" state: ");  //状态
        info.append(m_adapter.getState());
        info.append(" IsScanning: ");
        info.append(m_adapter.isDiscovering());

        return info;
    }

    @SuppressLint("MissingPermission")
    public static void setName(String name){
        m_adapter.setName(name);
    }

    public static Ble_adapter getBle_adapter() {
        return m_ble_adapter;
    }

    public static void setBle_adapter(Ble_adapter m_ble_adapter) {
        BlueToothTool.m_ble_adapter = m_ble_adapter;
    }

    public static List<BluetoothDevice> getDevices() {
        return m_devices;
    }

    public static void setDevices(List<BluetoothDevice> m_devices) {
        BlueToothTool.m_devices = m_devices;
    }

    public static List<Integer> getRssi() {
        return m_rssi;
    }

    public static void setRssi(List<Integer> m_rssi) {
        BlueToothTool.m_rssi = m_rssi;
    }

    public static BluetoothGatt getGatt() {
        return m_gatt;
    }

    public static void setGatt(BluetoothGatt m_gatt) {
        BlueToothTool.m_gatt = m_gatt;
    }
}
