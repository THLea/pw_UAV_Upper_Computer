package com.example.uavhostcomputer.adapter;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uavhostcomputer.R;

import java.util.List;

public class Ble_adapter extends BaseAdapter {
    private Context m_context;
    private List<BluetoothDevice> m_device;
    private List<Integer> m_rssi;

    class ViewHolder{
        public TextView name;
        public TextView introduce;
        public TextView rssi;

        public ViewHolder(View view) {
            name = view.findViewById(R.id.name);
            introduce = view.findViewById(R.id.introduce);
            rssi = view.findViewById(R.id.rssi);
        }
    }

    public Ble_adapter(Context context, List<BluetoothDevice> bluetoothDevices, List<Integer> rssi) {
        m_context = context;
        m_device = bluetoothDevices;
        m_rssi = rssi;
    }

    @Override
    public int getCount() { return m_device.size(); }

    @Override
    public Object getItem(int position) { return m_device.get(position); }

    @Override
    public long getItemId(int position) { return 0; }

    @SuppressLint({"InflateParams", "MissingPermission"})
    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        if(view == null){
            view = LayoutInflater.from(m_context).inflate(R.layout.bluetooth_device_list_item, null);
            viewHolder = new ViewHolder(view);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BluetoothDevice device = (BluetoothDevice) getItem(position);
        viewHolder.name.setText(device.getName());
        viewHolder.introduce.setText(device.getAddress());
        viewHolder.rssi.setText(m_rssi.get(position)+"dB");

        return view;
    }
}
