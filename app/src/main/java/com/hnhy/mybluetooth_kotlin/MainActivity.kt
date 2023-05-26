package com.hnhy.mybluetooth_kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.hnhy.mybluetooth_kotlin.adapter.DeviceAdapter
import com.permissionx.guolindev.PermissionX
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.reflect.Method

class MainActivity : AppCompatActivity() {

    //蓝牙广播接收器
    private var bluetoothReceiver: BluetoothReceiver? = null

    //蓝牙适配器
    private var bluetoothAdapter: BluetoothAdapter? = null

    //蓝牙设备适配器
    private var mAdapter: DeviceAdapter? = null

    //可变列表
    private var list: MutableList<BluetoothDevice> = mutableListOf()

    //请求码
    private val REQUEST_ENABLE_BLUETOOTH = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //设置亮色状态栏模式 systemUiVisibility在Android11中弃用了，可以尝试一下。
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        //检查版本
        checkVersion()
    }

    private fun checkVersion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //请求动态权限 6.0及6.0以上
            permissionRequest()
        } else {
            //无需权限 6.0以下  直接初始化蓝牙
            initBluetooth()
        }
    }

    /**
     * 动态权限申请
     */
    private fun permissionRequest() =
        PermissionX.init(this).permissions(Manifest.permission.ACCESS_FINE_LOCATION)
            .request { allGrand, _, _ ->
                if (allGrand) initBluetooth() else showMsg("未打开权限")
            }

    /**
     * Toast
     */
    private fun showMsg(msg: CharSequence) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /**
     *  初始化蓝牙
     */
    private fun initBluetooth() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND)  //获取扫描结果
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) //获取绑定状态
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED) //开始扫描
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) //扫描结束
        bluetoothReceiver = BluetoothReceiver() //实例化广播接收器
        registerReceiver(bluetoothReceiver, intentFilter) //注册广播接收器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() //获取蓝牙适配器
    }

    /**
     * 广播接收器
     */
    inner class BluetoothReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                //显示蓝牙设备
                BluetoothDevice.ACTION_FOUND -> showDevicesData(context, intent)
                //当有蓝牙状态改变时，刷新列表适配器
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> mAdapter?.changeBondDevice()
                //开始扫描
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> loading_lay.visibility = View.VISIBLE
                //停止扫描
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> loading_lay.visibility = View.GONE
                else -> showMsg("Unknown未知")
            }
        }

    }

    /**
     * 显示设备数据
     * @param context 上下文参数
     * @param intent 意图
     */
    @SuppressLint("MissingPermission")
    private fun showDevicesData(context: Context?, intent: Intent) {
        //获取已绑定的设备
        getBondedDevice()
        //获取周围蓝牙设备
        val device =
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (list.indexOf(device) == -1) { //防止重复添加
            if (device!!.name != null) { //过滤掉设备名称为空的设备
                list.add(device)
            }
        }
        mAdapter = DeviceAdapter(R.layout.item_device_list, list)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = mAdapter

        //item的点击事件
        mAdapter!!.setOnItemChildClickListener { adapter, view, position ->
            //点击时获取状态，如果已经配对过了就不需要再配对
            if (list[position].bondState == BluetoothDevice.BOND_NONE) {
                //开始匹配
                createOrRemoveBond(1, list[position])
            } else {
                showDialog("确定取消配对吗？",
                    DialogInterface.OnClickListener { dialog, _ ->
                        //取消配对
                        createOrRemoveBond(2, list[position])
                    }
                )
            }
        }
    }

    /**
     * 弹窗
     * @param dialogTitle 标题
     * @param onclickListener 按钮点击事件
     */
    private fun showDialog(dialogTitle: String, onclickListener: DialogInterface.OnClickListener) =
        AlertDialog.Builder(this)
            .setMessage(dialogTitle)
            .setPositiveButton("确定", onclickListener)
            .setNegativeButton("取消", null)
            .create().show()

    /**
     * 创建或者取消匹配
     * @param type 处理类型 1匹配；2取消匹配
     * @param device 设备
     */
    private fun createOrRemoveBond(type: Int, device: BluetoothDevice) {
        var method: Method? = null
        when (type) {
            1 -> {
                method = BluetoothDevice::class.java.getMethod("createBond")
                method.invoke(device)
            }
            2 -> {
                method = BluetoothDevice::class.java.getMethod("removeBond")
                method.invoke(device)
            }
            else -> return
        }
    }

    /**
     * 获取已绑定的设备
     */
    @SuppressLint("MissingPermission")
    private fun getBondedDevice() {
        val pairedDevices = bluetoothAdapter!!.bondedDevices
        if (pairedDevices.size > 0) { //如果获取的结果大于0，则开始逐个解析
            for (device in pairedDevices) {
                if (list.indexOf(device) == -1) { //防止重复添加
                    if (device.name != null) { //过滤掉设备名称为空的设备
                        list.add(device)
                    }
                }
            }
        }
    }

    /**
     * 扫描蓝牙
     */
    @SuppressLint("MissingPermission")
    fun scanBluetooth(view: View) {
        if (bluetoothAdapter != null) { //是否支持蓝牙
            if (bluetoothAdapter!!.isEnabled) { //是否开启蓝牙
                //开始扫描周围的蓝牙设配，如果扫描到蓝牙设备，通过广播接收器发送广播
                if (mAdapter != null) {  //当适配器不为空时，说明已经有数据，则清空数据，重新扫描
                    list.clear()
                    mAdapter!!.notifyDataSetChanged()
                }
                bluetoothAdapter!!.startDiscovery()
            } else { //未打开蓝牙
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH)
            }
        } else {
            showMsg("当前设备不支持蓝牙")
        }
    }

    /**
     * 销毁
     */
    override fun onDestroy() {
        super.onDestroy()
        //解绑广播
        if (bluetoothReceiver != null) unregisterReceiver(bluetoothReceiver)
    }

}