package com.example.uavhostcomputer.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
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

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends BaseActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION_BT = 0x01;
    private static final int REQUEST_PERMISSION_LOCATION = 0x02;
    private static final int REQUEST_GET_DEVICE = 0x03;
    private static int PARAM_STEP_MAX = 100;

    private enum ParamType_e {
        KP,
        KI,
        KD
    }
    private static final String PARAM_INIT = "50.000";
    private final Map<ParamType_e, String> param_map = new HashMap<>(3);
    private final TextWatcher edit_text_watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
            _update_param(R.id.p_param_bar);
            _update_param(R.id.i_param_bar);
            _update_param(R.id.d_param_bar);
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
    private BluetoothDevice selected_device = null;

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
    }

    @SuppressLint({"InlinedApi", "MissingPermission"})
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.choose_ble: {
                Log.i(TAG, "onClick: choose ble net");
                //判断蓝牙是否开启
                if(BlueToothTool.getBlueToothAdapter().isEnabled()){
                    goto_choose_ble();
                } else {
                    BlueToothTool.getBlueToothAdapter().enable();
                }
                Log.i(TAG, "onClick: " + BlueToothTool.getBlueToothInfo());
                break;
            }
            case R.id.connect_ble:{
                Log.i(TAG, "onClick: connect ble net");
                break;
            }
            case R.id.ble_send:{
                Log.i(TAG, "onClick: " + param_map.toString());
                break;
            }
            default:{
                _fatal_error_popwin("不存在的Button");
            }
        }
    }

    private void goto_choose_ble(){
        Intent intent = new Intent(MainActivity.this, FindDeviceActivity.class);
        startActivity(intent);
        startActivityForResult(intent, REQUEST_GET_DEVICE);
    }

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
                } else {
                    Toast.makeText(MainActivity.this, "权限开启失败，蓝牙功能可能无法正常使用", Toast.LENGTH_SHORT).show();
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
        Double param = 0.;  //当前值
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
        param_str = String.format("%.3f", param);

        param_text.setText(param_str);
        param_map.put(param_key, param_str);
    }
    private void widget_init(){
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
            min.addTextChangedListener(edit_text_watcher);

            max = findViewById(R.id.i_param_max);
            min = findViewById(R.id.i_param_min);
            max.addTextChangedListener(edit_text_watcher);
            min.addTextChangedListener(edit_text_watcher);

            max = findViewById(R.id.d_param_max);
            min = findViewById(R.id.d_param_min);
            max.addTextChangedListener(edit_text_watcher);
            min.addTextChangedListener(edit_text_watcher);
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
        }
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
                        TextView bt_name = findViewById(R.id.ble_name);
                        bt_name.setText(device.getName());
                    }
                }
                break;
            }
        }
    }
}