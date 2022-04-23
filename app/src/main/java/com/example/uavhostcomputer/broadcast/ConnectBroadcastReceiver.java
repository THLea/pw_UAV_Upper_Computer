package com.example.uavhostcomputer.broadcast;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

class ConnectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ConnectBroadcastReceive";

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "intent = "+intent.toString());
        String action = intent.getAction();
        if(action != null){
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action){
                case BluetoothDevice.ACTION_ACL_CONNECTED:{
                    Log.d(TAG, "已连接");
                    break;
                }
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:{
                    Log.d(TAG, "断开连接");
                    break;
                }
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:{
                    Log.d(TAG, "配对状态改变");
                    if(device != null){
                        switch (device.getBondState()){
                            case BluetoothDevice.BOND_NONE: {
                                Log.d(TAG, "配对失败");
                                break;
                            }
                            case BluetoothDevice.BOND_BONDING:{
                                Log.d(TAG, "配对中");
                                break;
                            }
                            case BluetoothDevice.BOND_BONDED:{
                                Log.d(TAG, "配对成功");
                                break;
                            }
                        }
                    }
                    break;
                }
                case BluetoothDevice.ACTION_PAIRING_REQUEST:{
                    //验证码
                    int key = intent.getExtras().getInt(BluetoothDevice.EXTRA_PAIRING_KEY,-1);
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        try {
                            //1.确认配对
                            boolean success = false;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                if(key != -1){
                                    success = device.setPin(String.valueOf(key).getBytes());
                                }else {
                                    //需要系统权限，如果没有系统权限，就点击弹窗上的按钮配对吧
                                    success = device.setPairingConfirmation(true);
                                }
                            }
                            Log.d(TAG,"key="+key+"  bond="+success);
                            //如果没有将广播终止，则会出现一个一闪而过的配对框。
                            abortBroadcast();
                        } catch (Exception e) {
                            Log.e(TAG,"反射异常："+e);
                        }
                    }
                    break;
                }
            }
        }
    }
}
