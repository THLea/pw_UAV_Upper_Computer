package com.example.uavhostcomputer.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uavhostcomputer.R;
import com.example.uavhostcomputer.tool_class.ActivityManagement;
import com.example.uavhostcomputer.tool_class.BlueToothTool;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collector;

public class MainActivity extends BaseActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    //请求码
    private static final int REQUEST_PERMISSION_BT = 0x01;
    private static final int REQUEST_PERMISSION_LOCATION = 0x02;
    private static final int REQUEST_GET_DEVICE = 0x03;
    //
    private static final String ACTION_GATT_CONNECTING = "com.example.uavhostcomputer.ACTION_GATT_CONNECTING";
    private static final String ACTION_GATT_CONNECTED = "com.example.uavhostcomputer.ACTION_GATT_CONNECTED";
    private static final String ACTION_GATT_DISCONNECTING = "com.example.uavhostcomputer.ACTION_GATT_DISCONNECTING";
    private static final String ACTION_GATT_DISCONNECTED = "com.example.uavhostcomputer.ACTION_GATT_DISCONNECTED";
    private static final String ACTION_GATT_SEND_SUCCESSFUL = "com.example.uavhostcomputer.ACTION_GATT_SEND_SUCCESSFUL";
    private static final String ACTION_GATT_SEND_FAIL = "com.example.uavhostcomputer.ACTION_GATT_SEND_FAIL";
    //参数分成多少份
    private static int PARAM_STEP_MAX = 100;
    //param_map的key，用来区分不同的参数
    private enum ParamType_e {
        KP,
        KI,
        KD
    }
    //初始参数的值
    private static final String PARAM_INIT = "50.00";
    //参数的值
    private final Map<ParamType_e, String> param_map = new HashMap<>(3);
    private final TextWatcher edit_text_watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
            if(charSequence.length() != 0) {
                _update_param(R.id.p_param_bar);
                _update_param(R.id.i_param_bar);
                _update_param(R.id.d_param_bar);
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    };
    private final SeekBar.OnSeekBarChangeListener sbListener = new SeekBar.OnSeekBarChangeListener() {
        @SuppressLint({"DefaultLocale", "NonConstantResourceId"})
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            _update_param(seekBar.getId());
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private final InputFilter ipSectionFilter = (CharSequence source, int start, int end, Spanned dest, int dStart, int dEnd)->{
        String sourceText = source.toString();
        String destText = dest.toString();
        if(dStart == 0 && "0".equals(source)){
            return "0";
        }

        StringBuilder totalText = new StringBuilder();
        totalText.append(destText.substring(0, dStart))
                .append(sourceText)
                .append(destText.substring(dStart, destText.length()));
        try {
            if(Double.parseDouble(totalText.toString()) >= 1000){
                return "";
            }
        } catch (Exception e){
            return "";
        }

        if("".equals(sourceText.toString())) {
            return "";
        }

        return sourceText;
    };
    private BluetoothDevice selected_device = null;
    private TextView device_name;
    private TextView connect_status;

    private boolean permission_is_opened = false;
    private boolean is_connect = false;

    private UUID write_UUID_service;
    private UUID write_UUID_chara;
    private UUID read_UUID_service;
    private UUID read_UUID_chara;
    private UUID notify_UUID_service;
    private UUID notify_UUID_chara;
    private UUID indicate_UUID_service;
    private UUID indicate_UUID_chara;
    private final UUID DEVICE_UUID_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private final UUID DEVICE_UUID_CHARA = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private final boolean DEBUG_SEL = false;  //调试开关，用来调试不同的蓝牙模块
    //数据帧，大小固定为20字节，帧格式为{(帧头)0xfffe[0:1] + (操作码)0xAA[2] + (KP)0x0ABCDE[3:5] + (KI)0x0ABCDE[6:8]
    //                           + (KD)0x0ABCDE[9:11] + (高度)0xAAAA[12:13] + (未定)[14:15] + (CRC16)0xAAAA[16:17] + (帧尾)0xefff[18:19]}
    //操作码： 暂定为发送数据0x01, 启动0x02, 停止0x04, 仅操作不发送数据时数据帧的参数部分填0
    //参数使用8421BCD码发送，高度使用16进制数据，校验码只校验2到15字节
    //举例，如果发送参数为KP=12.36,KI=1.25,KD=0.77,Height=20, 发送数据为0xfffe 11 001236 000125 000077 0014 0000 d006 efff, 共20字节

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //初始化控件设置
        widget_init();
        //初始化数据设置
        data_init();
        //蓝牙服务初始化
        if (!BlueToothTool.blueToothInit((BluetoothManager) getSystemService(BLUETOOTH_SERVICE))) {
            _normal_exception("设备不支持蓝牙");
            TextView connect_status = findViewById(R.id.ble_status);
            connect_status.setText("设备不支持");
        }
        //权限判断与申请
        checkPermission();
        register_update_connect_state_receiver();
    }

    @SuppressLint({"InlinedApi", "MissingPermission"})
    @Override
    public void onClick(View view) {
        if(permission_is_opened) {
            switch (view.getId()) {
                case R.id.choose_ble: {
                    //判断蓝牙是否开启
                    if (BlueToothTool.getBlueToothAdapter().isEnabled()) {
                        if(is_connect && BlueToothTool.getGatt() != null){
                            BlueToothTool.getGatt().disconnect();
                            BlueToothTool.setGatt(null);
                            is_connect = false;
                        }
                        goto_choose_ble();
                    } else {
                        BlueToothTool.getBlueToothAdapter().enable();
                    }
                    Log.i(TAG, "onClick: " + BlueToothTool.getBlueToothInfo());
                    break;
                }
                case R.id.connect_ble: {
                    Log.i(TAG, "onClick: connect ble net");
                    if (selected_device != null && BlueToothTool.getBlueToothAdapter() != null) {
                        if(is_connect){
                            BlueToothTool.getGatt().disconnect();
                            BlueToothTool.setGatt(null);
                            is_connect = false;
                        } else {
                            ble_connect();
                        }
                    }
                    break;
                }
                case R.id.ble_send: {
                    Log.i(TAG, "onClick: " + param_map.toString());
                    if(is_connect){
                        send_data_frame((byte) 0x01);
                    } else {
                        Toast.makeText(MainActivity.this, "没有连接任何设备，无法发送", Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                default: {
                    _fatal_error_popwin("不存在的Button");
                }
            }
        } else {
            _normal_exception("相关权限未开启，不能够使用蓝牙功能");
        }

    }

    private void goto_choose_ble(){
        Intent intent = new Intent(MainActivity.this, FindDeviceActivity.class);
        startActivityForResult(intent, REQUEST_GET_DEVICE);
    }

    @SuppressLint("MissingPermission")
    private boolean ble_connect(){
        if(BlueToothTool.getBlueToothAdapter() == null || selected_device == null){
            Toast.makeText(MainActivity.this, "找不到蓝牙对象", Toast.LENGTH_SHORT).show();
            return false;
        }
        //连接过的设备，尝试重新连接
        if(selected_device.getAddress() != null && BlueToothTool.getGatt() != null){
            Toast.makeText(MainActivity.this, "连接已断开，正在重新连接", Toast.LENGTH_SHORT).show();
            if(BlueToothTool.getGatt().connect()){
                Toast.makeText(MainActivity.this, "已连接", Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent(ACTION_GATT_CONNECTED));
                return true;
            }else{
                Toast.makeText(MainActivity.this,"连接失败", Toast.LENGTH_SHORT).show();
                sendBroadcast(new Intent(ACTION_GATT_DISCONNECTED));
                return false;
            }
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            BlueToothTool.setGatt(selected_device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE));
        else
            BlueToothTool.setGatt(selected_device.connectGatt(this, false, gattCallback));
        connect_status.setText("连接中");

        return true;
    }

    private short crc16(byte[] data, int num, short crc){
        final int PLOY = 0x1021;
        for(int i = 0; i < num; i++){
            crc = (short) (crc^(data[i] << 8));
            for(int j = 0; j < 8; j++){
                if((crc & 0x8000) != 0){
                    crc = (short) ((crc << 1) ^ PLOY);
                } else {
                    crc <<= 1;
                }
                crc &= 0xffff;
            }
        }
        return crc;
    }

    private byte[] prepare_data_frame(byte operaCode){
        byte[] frame = new byte[20];
        byte[] checkData = new byte[14];

        final double KP = Double.parseDouble(Objects.requireNonNull(param_map.get(ParamType_e.KP)));
        final double KI = Double.parseDouble(Objects.requireNonNull(param_map.get(ParamType_e.KI)));
        final double KD = Double.parseDouble(Objects.requireNonNull(param_map.get(ParamType_e.KD)));
        final short HEIGHT = 20;

        if(operaCode == 0x01) {
            checkData[1] = (byte) (KP / 100 % 10);
            checkData[2] = (byte) ((((byte) (KP / 10 % 10)) << 4) | ((byte) (KP % 10)));
            checkData[3] = (byte) ((((byte) (KP * 10 % 10)) << 4) | ((byte) (KP * 100 % 10)));
            checkData[4] = (byte) (KI / 100 % 10);
            checkData[5] = (byte) ((((byte) (KI / 10 % 10)) << 4) | ((byte) (KI % 10)));
            checkData[6] = (byte) ((((byte) (KI * 10 % 10)) << 4) | ((byte) (KI * 100 % 10)));
            checkData[7] = (byte) (KD / 100 % 10);
            checkData[8] = (byte) ((((byte) (KD / 10 % 10)) << 4) | ((byte) (KD % 10)));
            checkData[9] = (byte) ((((byte) (KD * 10 % 10)) << 4) | ((byte) (KD * 100 % 10)));
            checkData[10] = (byte) (HEIGHT >>> 8);
            checkData[11] = (byte) (HEIGHT & 0xff);
            checkData[12] = checkData[13] = 0;
        } else {
            Arrays.fill(checkData, (byte) 0);
        }
        checkData[0] = operaCode;

        short crc = crc16(checkData, 14, (short) 0xffff);

        frame[0] = (byte) 0xff;
        frame[1] = (byte) 0xfe;
        System.arraycopy(checkData, 0, frame, 2, 14);
        frame[16] = (byte) (crc>>>8);
        frame[17] = (byte) (crc & 0xff);
        frame[18] = (byte) 0xef;
        frame[19] = (byte) 0xff;

        Log.i(TAG, "prepare_data_frame: check data = " + Arrays.toString(checkData));
        Log.i(TAG, "prepare_data_frame: crc = " + crc);
        Log.i(TAG, "prepare_data_frame: frame = " + Arrays.toString(frame));

        return frame;
    }

    @SuppressLint("MissingPermission")
    private void send_data_frame(byte operaCode){
        byte[] frame = prepare_data_frame(operaCode);
        BluetoothGattService service = BlueToothTool.getGatt().getService(write_UUID_service);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(write_UUID_chara);
        characteristic.setValue(frame);
        BlueToothTool.getGatt().writeCharacteristic(characteristic);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void send_data(){
        BluetoothGattService service = BlueToothTool.getGatt().getService(write_UUID_service);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(write_UUID_chara);
        byte[] data = ("KP="+param_map.get(ParamType_e.KP)+",KI="+param_map.get(ParamType_e.KI)+",KD="+param_map.get(ParamType_e.KD)).getBytes();
        if(data.length > 20){
            Log.d(TAG, "send_data: data_length = "+data.length);
            int num = 0;
            num = data.length/20;
            if(data.length % 20 != 0){
                num = num + 1;
            }
            for(int i = 0; i < num; i++){
                byte[] tempArr;
                if(i == num-1){
                    tempArr = new byte[data.length - i*20];
                    System.arraycopy(data, i*20, tempArr, 0, data.length- i*20);
                } else {
                    tempArr = new byte[20];
                    System.arraycopy(data, i*20, tempArr, 0, 20);
                }
                Log.d(TAG, "tempArr["+i+"]send_data: "+ Arrays.toString(tempArr));
                characteristic.setValue(tempArr);
                BlueToothTool.getGatt().writeCharacteristic(characteristic);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            characteristic.setValue(data);
            BlueToothTool.getGatt().writeCharacteristic(characteristic);
        }
    }

    public List<BluetoothGattService> getSupportedGattServices(){
        if(BlueToothTool.getGatt() == null)
            return null;
        return BlueToothTool.getGatt().getServices();
    }

    private void initServiceAndChara(){
        List<BluetoothGattService> services = getSupportedGattServices();
        for(BluetoothGattService service : services){
            List<BluetoothGattCharacteristic> charas = service.getCharacteristics();
            for(BluetoothGattCharacteristic chara : charas){
                int charaProp = chara.getProperties();
                if((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0){
                    read_UUID_service = service.getUuid();
                    read_UUID_chara = chara.getUuid();
                    Log.d(TAG, "read_service:"+read_UUID_service+" read_characteristic:"+read_UUID_chara);
                }
                if((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0){
                    if(DEBUG_SEL) {
                        write_UUID_service = service.getUuid();
                        write_UUID_chara = chara.getUuid();
                    } else {
                        write_UUID_service = DEVICE_UUID_SERVICE;
                        write_UUID_chara = DEVICE_UUID_CHARA;
                    }
                    Log.d(TAG, "write_service:"+write_UUID_service+" write_characteristic:"+write_UUID_chara);
                }
                if((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
                    notify_UUID_service = service.getUuid();
                    notify_UUID_chara = chara.getUuid();
                    Log.d(TAG, "notify_service:"+notify_UUID_service+" notify_characteristic:"+notify_UUID_chara);
                }
                if((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0){
                    indicate_UUID_service = service.getUuid();
                    indicate_UUID_chara = chara.getUuid();
                    Log.d(TAG, "indicate_service:"+indicate_UUID_service+" indicate_characteristic:"+indicate_UUID_chara);
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        //连接状态改变时调用这个函数
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState){
                case BluetoothProfile.STATE_CONNECTED:{
                    sendBroadcast(new Intent(ACTION_GATT_CONNECTED));
                    BlueToothTool.getGatt().discoverServices();
                    break;
                }
                case BluetoothProfile.STATE_CONNECTING:{
                    sendBroadcast(new Intent(ACTION_GATT_CONNECTING));
                    break;
                }
                case BluetoothProfile.STATE_DISCONNECTING:{
                    sendBroadcast(new Intent(ACTION_GATT_DISCONNECTING));
                    break;
                }
                case BluetoothProfile.STATE_DISCONNECTED:{
                    sendBroadcast(new Intent(ACTION_GATT_DISCONNECTED));
                    BlueToothTool.setGatt(null);
                    break;
                }
            }
        }

        //寻找到服务时调用这个函数
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                //初始化服务和特征值
                initServiceAndChara();
                //订阅通知
                boolean b = gatt.setCharacteristicNotification(
                        gatt.getService(notify_UUID_service).getCharacteristic(notify_UUID_chara), true);
                if(b){
                    List<BluetoothGattDescriptor> descriptors = gatt.getService(notify_UUID_service).getCharacteristic(notify_UUID_chara).getDescriptors();
                    for(BluetoothGattDescriptor descriptor : descriptors){
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        //读取设备时调用这个函数
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(characteristic.getUuid());
            Log.d(TAG, "onCharacteristicRead: ");
            byte[] data = characteristic.getValue();

            Log.i(TAG, "data = "+ Arrays.toString(data));
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //写设备时调用这个函数
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite: status = "+status+" value: "+ Arrays.toString(characteristic.getValue()));
            if(status == BluetoothGatt.GATT_SUCCESS){
                sendBroadcast(new Intent(ACTION_GATT_SEND_SUCCESSFUL));
            } else {
                sendBroadcast(new Intent(ACTION_GATT_SEND_FAIL));
            }
        }

        //设备发出通知时调用这个
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged: value = "+ Arrays.toString(characteristic.getValue()));
            gatt.readCharacteristic(characteristic);
        }

        //读取到rssi时调用这个
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorRead: ");
        }


    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "onRequestPermissionsResult: " + grantResults.length + " " + grantResults[0] + " " + permissions[0]);
        switch (requestCode){
            case REQUEST_PERMISSION_BT:{

                break;
            }
            case REQUEST_PERMISSION_LOCATION:{
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(MainActivity.this, "已开启定位权限", Toast.LENGTH_SHORT).show();
                    permission_is_opened = true;
                } else {
                    Toast.makeText(MainActivity.this, "权限开启失败，蓝牙功能可能无法正常使用", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    private AlertDialog.Builder _exception_alert(String errorInfo){
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        dialog.setTitle("错误");
        dialog.setMessage("发生了异常:"+errorInfo);
        dialog.setCancelable(false);

        return dialog;
    }
    private void _fatal_error_popwin(String errorInfo){
        AlertDialog.Builder dialog = _exception_alert(errorInfo);

        dialog.setPositiveButton("退出",(DialogInterface dialogInter, int which)->{
            ActivityManagement.finishAll();
        });
        dialog.show();
    }
    private void _normal_exception(String errorInfo){
        AlertDialog.Builder dialog = _exception_alert(errorInfo);
        dialog.setPositiveButton("确定",(dialogInter, which)->{ });
        dialog.show();
    }
    private void _update_param(int seekBar_id){
        double param_min = 0, param_max = 0;  //最小值，最大值
        double param = 0.;  //当前值
        int param_min_id = R.id.p_param_min, param_max_id = R.id.p_param_max, param_text_id = R.id.p_param_text;
        ParamType_e param_key = ParamType_e.KP;
        SeekBar seekBar = findViewById(seekBar_id);

        switch (seekBar_id){
            case R.id.p_param_bar:{
                param_min_id = R.id.p_param_min;
                param_max_id = R.id.p_param_max;
                param_text_id = R.id.p_param_text;
                param_key = ParamType_e.KP;
                break;
            }
            case R.id.i_param_bar:{
                param_min_id = R.id.i_param_min;
                param_max_id = R.id.i_param_max;
                param_text_id = R.id.i_param_text;
                param_key = ParamType_e.KI;
                break;
            }
            case R.id.d_param_bar:{
                param_min_id = R.id.d_param_min;
                param_max_id = R.id.d_param_max;
                param_text_id = R.id.d_param_text;
                param_key = ParamType_e.KD;
                break;
            }
            default:{
                _fatal_error_popwin("不存在的SeekBar");
            }
        }

        TextView param_text = findViewById(param_text_id);
        EditText param_max_edit = findViewById(param_max_id);
        EditText param_min_edit = findViewById(param_min_id);
        String param_str;

        param_min = Double.parseDouble(param_min_edit.getText().toString());
        param_max = Double.parseDouble(param_max_edit.getText().toString());
        param = (param_max - param_min) * ((double) seekBar.getProgress() / PARAM_STEP_MAX) + param_min;
        param_str = String.format("%.2f", param);

        param_text.setText(param_str);
        param_map.put(param_key, param_str);
    }
    private void widget_init(){
        device_name = findViewById(R.id.ble_name);
        connect_status = findViewById(R.id.ble_status);
        //设置SeekBar的监听
        {
            SeekBar p_param = findViewById(R.id.p_param_bar);
            p_param.setOnSeekBarChangeListener(sbListener);
            SeekBar i_param = findViewById(R.id.i_param_bar);
            i_param.setOnSeekBarChangeListener(sbListener);
            SeekBar d_param = findViewById(R.id.d_param_bar);
            d_param.setOnSeekBarChangeListener(sbListener);
        }
        //设置EditText的监听
        {
            EditText max = findViewById(R.id.p_param_max);
            EditText min = findViewById(R.id.p_param_min);
            max.addTextChangedListener(edit_text_watcher);
            max.setFilters(new InputFilter[]{ipSectionFilter});
            min.addTextChangedListener(edit_text_watcher);
            min.setFilters(new InputFilter[]{ipSectionFilter});

            max = findViewById(R.id.i_param_max);
            min = findViewById(R.id.i_param_min);
            max.addTextChangedListener(edit_text_watcher);
            max.setFilters(new InputFilter[]{ipSectionFilter});
            min.addTextChangedListener(edit_text_watcher);
            min.setFilters(new InputFilter[]{ipSectionFilter});

            max = findViewById(R.id.d_param_max);
            min = findViewById(R.id.d_param_min);
            max.addTextChangedListener(edit_text_watcher);
            max.setFilters(new InputFilter[]{ipSectionFilter});
            min.addTextChangedListener(edit_text_watcher);
            min.setFilters(new InputFilter[]{ipSectionFilter});
        }
        //设置Button的监听
        {
            Button sendData = findViewById(R.id.ble_send);
            sendData.setOnClickListener(this);
            Button chooseBle = findViewById(R.id.choose_ble);
            chooseBle.setOnClickListener(this);
            Button connectBle = findViewById(R.id.connect_ble);
            connectBle.setOnClickListener(this);
        }
    }
    private void data_init(){
        PARAM_STEP_MAX = getResources().getInteger(R.integer.param_step_max);

        //初始化PID参数
        param_map.put(ParamType_e.KP, PARAM_INIT);
        param_map.put(ParamType_e.KI, PARAM_INIT);
        param_map.put(ParamType_e.KD, PARAM_INIT);
    }

    private void checkPermission(){
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
        } else {
            permission_is_opened = true;
        }
    }

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            final String action = intent.getAction();
            if(action != null){
                switch (action){
                    case ACTION_GATT_CONNECTED:{
                        Log.i(TAG, "已连接设备");
                        is_connect = true;
                        connect_status.setText("已连接");
                        break;
                    }
                    case ACTION_GATT_CONNECTING:{
                        Log.i(TAG, "正在连接设备");
                        is_connect = false;
                        connect_status.setText("连接中");
                        break;
                    }
                    case ACTION_GATT_DISCONNECTING:{
                        Log.i(TAG, "正在断开设备");
                        is_connect = false;
                        connect_status.setText("正在断开设备");
                        break;
                    }
                    case ACTION_GATT_DISCONNECTED:{
                        Log.i(TAG, "已断开连接");
                        is_connect = false;
                        connect_status.setText("已断开");
                        break;
                    }
                    case ACTION_GATT_SEND_SUCCESSFUL:{
                        Toast.makeText(MainActivity.this, "发送成功", Toast.LENGTH_SHORT).show();
                        break;
                    }
                    case ACTION_GATT_SEND_FAIL:{
                        Toast.makeText(MainActivity.this, "发送失败", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        }
    };

    private void register_update_connect_state_receiver(){
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_CONNECTED);
        intentFilter.addAction(ACTION_GATT_CONNECTING);
        intentFilter.addAction(ACTION_GATT_DISCONNECTING);
        intentFilter.addAction(ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ACTION_GATT_SEND_SUCCESSFUL);
        intentFilter.addAction(ACTION_GATT_SEND_FAIL);

        registerReceiver(gattUpdateReceiver, intentFilter);
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GET_DEVICE:{
                if(resultCode == RESULT_OK && data != null){
                    BluetoothDevice device = data.getParcelableExtra("device");
                    if (device != null && device.getName() != null) {
                        selected_device = device;
                        device_name.setText(device.getName());
                    }
                }
                connect_status.setText("未连接");
                break;
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(gattUpdateReceiver);
    }
}