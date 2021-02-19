package com.trilinkee.levelregulator

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException
import org.json.JSONObject
import java.util.*


class MainActivity : AppCompatActivity() {
    private val TAG: String =
        MainActivity::class.java.simpleName

    private var mAxisSensor = AxisSensor()
    private var mSensorAverageNumber: Double = 0.0

    private val mLevelMinimum: Double = -0.05
    private val mLevelMaximum: Double = 0.05
    private val mLevelRanger: ClosedFloatingPointRange<Double> = mLevelMinimum..mLevelMaximum
    private var mConfig: ApplicationConfig = ApplicationConfig()

    //private val DEVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val mDeviceUUID: String = "0000FFE1-0000-1000-8000-00805F9B34FB"


    private var mStateMachineText: TextView? = null
    private var mAccelXText: TextView? = null
    private var mSideViewRearArrow: ImageView? = null
    private var mSideViewFrontArrow: ImageView? = null
    private var mAccelYText: TextView? = null
    private var mRearViewLeftArrow: ImageView? = null
    private var mRearViewRightArrow: ImageView? = null

    private var mFrontRearSwapButton: ImageButton? = null
    private var mLeftRightSwapButton: ImageButton? = null
    private var mAxisSwapButton: ImageButton? = null
    private var mSaveAdjustValueButton: Button? = null
    private var mResetValueButton: Button? = null

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mHandler: Handler? = null
    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var mOutputStream: java.io.OutputStream? = null

    private var mIsShownError: Boolean = false
    private var mErrorCode: Int = 0
    private var mIMUStatus: Int = 0
    private var mIMUSErrorDetail: String? = null

    private enum class StateMachine {
        NONE,
        SCANNING,
        CONNECTING,
        CONNECTED,
        CONNECTED_PAUSE,
        DISCONNECTED
    }
    private var mState: StateMachine = StateMachine.NONE
    private enum class StateMachineInput {
        NONE,
        START,
        PAUSE,
        RESUME,
        TIMEOUT,
        FOUND_DEVICE,
        SERVICE_CONNECTED,
        SERVICE_DISCONNECTED,
        GATT_CONNECTED,
        GATT_DISCONNECTED,
        GATT_SERVICES_DISCOVERED,
        DATA_RECEIVED,
        FIND_CHARACTERISTIC,
        DESTROY
    }
    private val SCAN_PERIOD: Long = 10000

    private val REQUEST_ENABLE_BT = 1

    private var mBluetoothLeService: BluetoothLeService? = null
    private var mNotifyCharacteristic: BluetoothGattCharacteristic? = null

    // Code to manage Service lifecycle.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            runOnUiThread {
                this@MainActivity.stateMachine(StateMachineInput.SERVICE_CONNECTED, service)
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            runOnUiThread {
                this@MainActivity.stateMachine(StateMachineInput.SERVICE_DISCONNECTED, null)
            }

        }
    }
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private val mGattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                runOnUiThread {
                    this@MainActivity.stateMachine(StateMachineInput.GATT_CONNECTED, null)
                }
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                runOnUiThread {
                    this@MainActivity.stateMachine(StateMachineInput.GATT_DISCONNECTED, null)
                }
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) { // Show all the supported services and characteristics on the user interface.
                runOnUiThread {
                    this@MainActivity.stateMachine(StateMachineInput.GATT_SERVICES_DISCOVERED, mBluetoothLeService!!.supportedGattServices)
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                runOnUiThread {
                    this@MainActivity.stateMachine(StateMachineInput.DATA_RECEIVED, intent.getStringExtra(BluetoothLeService.EXTRA_DATA))
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main)

        mStateMachineText =
            findViewById<View>(R.id.state_machine_text) as TextView
        mStateMachineText!!.text = getText(R.string.state_none)

        mAccelXText = findViewById<View>(R.id.accel_x) as TextView
        mSideViewRearArrow = findViewById<View>(R.id.trailer_sideview_rear_arrow) as ImageView
        mSideViewFrontArrow = findViewById<View>(R.id.trailer_sideview_front_arrow) as ImageView
        mAccelYText = findViewById<View>(R.id.accel_y) as TextView
        mRearViewLeftArrow = findViewById<View>(R.id.trailer_rearview_left_arrow) as ImageView
        mRearViewRightArrow = findViewById<View>(R.id.trailer_rearview_right_arrow) as ImageView

        mFrontRearSwapButton = findViewById<View>(R.id.front_rear_swap_button) as ImageButton
        mLeftRightSwapButton = findViewById<View>(R.id.left_right_swap_button) as ImageButton
        mAxisSwapButton = findViewById<View>(R.id.axis_swap_button) as ImageButton
        mSaveAdjustValueButton = findViewById<View>(R.id.save_center_button) as Button
        mResetValueButton = findViewById<View>(R.id.reset_button) as Button

        mAccelXText!!.text = String.format("-")
        mAccelYText!!.text = String.format("-")

        mSideViewRearArrow!!.setImageBitmap(null)
        mSideViewFrontArrow!!.setImageBitmap(null)
        mRearViewLeftArrow!!.setImageBitmap(null)
        mRearViewRightArrow!!.setImageBitmap(null)

        loadConfig()

        mFrontRearSwapButton!!.setOnClickListener {
            mConfig.mFrontRearSwap = !mConfig.mFrontRearSwap

            saveConfig()
        }
        mLeftRightSwapButton!!.setOnClickListener {
            mConfig.mLeftRightSwap = !mConfig.mLeftRightSwap

            saveConfig()
        }
        mAxisSwapButton!!.setOnClickListener {
            mConfig.mAxisSwap = !mConfig.mAxisSwap

            saveConfig()
        }
        mSaveAdjustValueButton!!.setOnClickListener {
            mConfig.mLevelAdjustX = mAxisSensor.mAcceleration.mX
            mConfig.mLevelAdjustY = mAxisSensor.mAcceleration.mY

            saveConfig()
        }
        mResetValueButton!!.setOnClickListener {
            mConfig.reset()

            saveConfig()
        }

        mHandler = Handler()

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()

        stateMachine(StateMachineInput.RESUME, null)
    }

    override fun onPause() {
        super.onPause()

        stateMachine(StateMachineInput.PAUSE, null)
    }

    private fun startScanLeDevice() {
        // Stops scanning after a pre-defined scan period.
        mHandler?.postDelayed({
            runOnUiThread {
                stateMachine(StateMachineInput.TIMEOUT, null)
            }
        }, SCAN_PERIOD)
        mBluetoothAdapter!!.startLeScan(mLeScanCallback)
    }

    private fun stopScanLeDevice() {
        mBluetoothAdapter!!.stopLeScan(mLeScanCallback)
    }

    // Device scan callback.
    private var mLeScanCallback: LeScanCallback? =
        LeScanCallback { device, rssi, scanRecord ->
            if(device.name == "DSD TECH") {
                runOnUiThread {
                    stateMachine(StateMachineInput.FOUND_DEVICE, device)
                }
            }
        }

    private fun loadConfig() {
        val filePath = getFileStreamPath("config.json")
        if(filePath.exists()) {
            val inputStream = openFileInput("config.json")

            val dataSize = inputStream.available()
            val data = ByteArray(dataSize)
            inputStream.read(data)
            val charset = Charsets.UTF_8
            val json = data.toString(charset)

            val jsonObject = JSONObject(json)
            val adjustObject = jsonObject.getJSONObject("adjust")
            mConfig.mLevelAdjustX = adjustObject.getDouble("x")
            mConfig.mLevelAdjustY = adjustObject.getDouble("y")

            val swapObject = jsonObject.getJSONObject("swap")
            mConfig.mFrontRearSwap = swapObject.getBoolean("front_rear")
            mConfig.mLeftRightSwap = swapObject.getBoolean("left_right")
            mConfig.mAxisSwap = swapObject.getBoolean("axis")
        }
    }

    private fun saveConfig() {
        var jsonObject = JSONObject()

        val adjustObject = JSONObject()
        adjustObject.put("x", mConfig.mLevelAdjustX)
        adjustObject.put("y", mConfig.mLevelAdjustY)
        jsonObject.putOpt("adjust", adjustObject)

        val swapObject = JSONObject()
        swapObject.put("front_rear", mConfig.mFrontRearSwap)
        swapObject.put("left_right", mConfig.mLeftRightSwap)
        swapObject.put("axis", mConfig.mAxisSwap)
        jsonObject.putOpt("swap", swapObject)

        val outputStream = openFileOutput("config.json", 0)
        val charset = Charsets.UTF_8
        outputStream.write(jsonObject.toString(0).toByteArray(charset))
    }

    class ParsingInfo {
        enum class State {
            NONE,
            LENGTH,
            DATA,
        }
        var mStateMachine: State = State.NONE
        var mParsingLine: StringBuilder = StringBuilder()
        val mLengthToken: String = "length:"
        var mDataLength: Int = 0
    }
    var mParsingInfo: ParsingInfo = ParsingInfo()

    private fun updateText() {
        var mAccelerationX: Double =  mAxisSensor.mAcceleration.mX
        var mAccelerationY: Double =  mAxisSensor.mAcceleration.mY
        mAccelerationX -=  mConfig.mLevelAdjustX
        mAccelerationY -=  mConfig.mLevelAdjustY

        if(mConfig.mAxisSwap) {
            var swap = mAccelerationX
            mAccelerationX = mAccelerationY
            mAccelerationY = swap
        }
        if(mConfig.mFrontRearSwap) {
            mAccelerationX *= -1
        }
        if(mConfig.mLeftRightSwap) {
            mAccelerationY *= -1
        }

        mAccelXText!!.text = String.format("%.10f", mAccelerationX)
        mAccelYText!!.text = String.format("%.10f", mAccelerationY)

        if(mAccelerationX in mLevelRanger) {
            mSideViewRearArrow!!.setImageBitmap(null)
            mSideViewFrontArrow!!.setImageBitmap(null)
        }
        else if(mAccelerationX > mLevelMaximum) {
            mSideViewRearArrow!!.setImageResource(R.drawable.arrow_down)
            mSideViewFrontArrow!!.setImageResource(R.drawable.arrow_up)
        }
        else if(mAccelerationX < mLevelMinimum) {
            mSideViewRearArrow!!.setImageResource(R.drawable.arrow_up)
            mSideViewFrontArrow!!.setImageResource(R.drawable.arrow_down)
        }
        if(mAccelerationY in mLevelRanger) {
            mRearViewLeftArrow!!.setImageBitmap(null)
            mRearViewRightArrow!!.setImageBitmap(null)
        }
        else if(mAccelerationY > mLevelMaximum) {
            mRearViewLeftArrow!!.setImageResource(R.drawable.arrow_down)
            mRearViewRightArrow!!.setImageResource(R.drawable.arrow_up)
        }
        else if(mAccelerationY < mLevelMinimum) {
            mRearViewLeftArrow!!.setImageResource(R.drawable.arrow_up)
            mRearViewRightArrow!!.setImageResource(R.drawable.arrow_down)
        }
    }

    private fun calculateSensorValue(jsonObject: JSONObject, sensor: AxisSensor.Sensor, avgNumbers: Double) {
        val x = jsonObject.getDouble("x")
        val y = jsonObject.getDouble("y")
        val z = jsonObject.getDouble("z")
        sensor.mRingBufferX.putData(x)
        sensor.mX = sensor.mRingBufferX.getAverage()
        sensor.mRingBufferY.putData(y)
        sensor.mY = sensor.mRingBufferY.getAverage()
        sensor.mRingBufferZ.putData(z)
        sensor.mZ = sensor.mRingBufferZ.getAverage()
    }

    private fun parsingJson(json: String): Int {
        /*
        Log.i(
            TAG,
            json
        )
        */
        var errorCode: Int = 0
        try {
            val jsonObject = JSONObject(json)

            val error = jsonObject.getJSONObject("error")
            errorCode = error.getInt("code")
            if(errorCode == 100) {
                val accel = jsonObject.getJSONObject("accel")
                calculateSensorValue(accel, mAxisSensor.mAcceleration, mSensorAverageNumber)

                val gyro = jsonObject.getJSONObject("gyro")
                calculateSensorValue(gyro, mAxisSensor.mGyroscope, mSensorAverageNumber)

                val mag = jsonObject.getJSONObject("mag")
                calculateSensorValue(mag, mAxisSensor.mMagnetic, mSensorAverageNumber)

                val temp = jsonObject.getDouble("temp")
                if(mSensorAverageNumber > 0.0) {
                    mAxisSensor.mTemperature += (temp - mAxisSensor.mTemperature) / mSensorAverageNumber
                }
                else {
                    mAxisSensor.mTemperature = temp
                }

                mSensorAverageNumber += 1.0
                mSensorAverageNumber = Math.min(mSensorAverageNumber + 1.0, 10.0)
            }
            else {
                mIMUStatus = error.getInt("IMUStatus")
                mIMUSErrorDetail = error.getString("detail")
            }
        }
        catch (e: JSONException ) {

        }
        return errorCode
    }

    private fun processData(data: String?) {
        if (data == null)
            return
        val separator = data.lastIndexOf('\n')
        if(separator == -1)
            return
        val str = data.substring(0, separator)
        for(ch in str) {
            when (mParsingInfo.mStateMachine) {
                ParsingInfo.State.NONE -> {
                    if(ch == '\n') {
                        mParsingInfo.mStateMachine = ParsingInfo.State.LENGTH
                    }
                }
                ParsingInfo.State.LENGTH -> {
                    if(ch == '\n') {
                        if(mParsingInfo.mParsingLine.commonPrefixWith(mParsingInfo.mLengthToken, true).isNotEmpty()) {
                            mParsingInfo.mDataLength = mParsingInfo.mParsingLine.substring(
                                mParsingInfo.mLengthToken.length,
                                mParsingInfo.mParsingLine.length
                            ).toInt()
                            mParsingInfo.mStateMachine = ParsingInfo.State.DATA
                        }
                        mParsingInfo.mParsingLine.clear()
                    }
                    else {
                        mParsingInfo.mParsingLine.append(ch)
                    }
                }
                ParsingInfo.State.DATA -> {
                    if(ch == '\n') {
                        if(mParsingInfo.mParsingLine.length == mParsingInfo.mDataLength) {
                            val errorCode = parsingJson(mParsingInfo.mParsingLine.toString())
                            if(errorCode == 100) {
                                mErrorCode = errorCode
                                mIsShownError = false
                                updateText()
                            }
                            else {
                                if(errorCode != mErrorCode) {
                                    mIsShownError = false
                                    mErrorCode = errorCode
                                }

                                if(!mIsShownError) {
                                    mIsShownError = true
                                    var errorMessage: String = "Received Error from Device: "
                                    errorMessage += errorCode
                                    errorMessage += " IMU status: "
                                    errorMessage += mIMUStatus
                                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        mParsingInfo.mStateMachine = ParsingInfo.State.LENGTH
                        mParsingInfo.mParsingLine.clear()
                    }
                    else {
                        mParsingInfo.mParsingLine.append(ch)
                    }
                }
                else -> {
                }
            }
        }
    }

    private fun findGattCharacteristicsServices(gattServices: List<BluetoothGattService>?) {
        if (gattServices == null)
            return

        for (gattService: BluetoothGattService in gattServices) {
            val gattCharacteristics: List<BluetoothGattCharacteristic> =
                gattService.characteristics
            for(gattCharacteristic: BluetoothGattCharacteristic in gattCharacteristics) {
                val uuid: String = gattCharacteristic.uuid.toString()
                if(uuid.equals(mDeviceUUID, true)) {
                    runOnUiThread {
                        stateMachine(StateMachineInput.FIND_CHARACTERISTIC, gattCharacteristic)
                    }
                }
            }
        }
    }

    private fun changeState(newState: StateMachine) {
        if(mState != newState) {
            mState = newState
            runOnUiThread {
                when(mState) {
                    StateMachine.NONE -> mStateMachineText!!.text = getText(R.string.state_none)
                    StateMachine.SCANNING -> mStateMachineText!!.text = getText(R.string.state_scanning)
                    StateMachine.CONNECTING -> mStateMachineText!!.text = getText(R.string.state_connecting)
                    StateMachine.CONNECTED -> mStateMachineText!!.text = getText(R.string.state_connected)
                    StateMachine.DISCONNECTED -> mStateMachineText!!.text = getText(R.string.state_disconnected)
                    else -> {
                    }
                }
            }
        }
    }

    private fun stateMachine(stateInput: StateMachineInput, obj: Any?) {
        when(mState) {
            StateMachine.NONE -> {
                when(stateInput) {
                    StateMachineInput.START, StateMachineInput.RESUME -> {
                        startScanLeDevice()
                        changeState(StateMachine.SCANNING)
                    }
                    else -> {
                    }
                }
            }
            StateMachine.SCANNING -> {
                when(stateInput) {
                    StateMachineInput.TIMEOUT -> {
                        stopScanLeDevice()
                        changeState(StateMachine.NONE)
                    }
                    StateMachineInput.RESUME -> {
                        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
                        // fire an intent to display a dialog asking the user to grant permission to enable it.
                        if (!mBluetoothAdapter!!.isEnabled()) {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                        }

                        startScanLeDevice()
                    }
                    StateMachineInput.PAUSE -> {
                        stopScanLeDevice()
                        changeState(StateMachine.NONE)
                    }
                    StateMachineInput.FOUND_DEVICE -> {
                        if (obj != null) {
                            val device: BluetoothDevice? = obj as? BluetoothDevice
                            if (device != null) {
                                mDeviceName = device.name
                                mDeviceAddress = device.address
                            }
                            stopScanLeDevice()

                            val gattServiceIntent =
                                Intent(this, BluetoothLeService::class.java)
                            bindService(
                                gattServiceIntent,
                                mServiceConnection,
                                Context.BIND_AUTO_CREATE
                            )

                            changeState(StateMachine.CONNECTING)
                        }
                    }
                    else -> {
                    }
                }
            }
            StateMachine.CONNECTING -> {
                when(stateInput) {
                    StateMachineInput.SERVICE_CONNECTED -> {
                        val service: BluetoothLeService.LocalBinder? = obj as? BluetoothLeService.LocalBinder
                        mBluetoothLeService = service!!.service
                        if (!mBluetoothLeService!!.initialize()) {
                            Log.e(
                                TAG,
                                "Unable to initialize Bluetooth"
                            )
                            finish()
                        }
                        // Automatically connects to the device upon successful start-up initialization.
                        mBluetoothLeService!!.connect(mDeviceAddress)

                        registerReceiver(
                            mGattUpdateReceiver,
                            makeGattUpdateIntentFilter()
                        )
                        if (mBluetoothLeService != null) {
                            val result = mBluetoothLeService!!.connect(mDeviceAddress)
                            Log.d(
                                TAG,
                                "Connect request result=$result"
                            )
                        }

                        changeState(StateMachine.CONNECTED)
                    }
                    else -> {
                    }
                }
            }
            StateMachine.CONNECTED -> {
                when(stateInput) {
                    StateMachineInput.SERVICE_DISCONNECTED -> {
                        mBluetoothLeService = null
                        changeState(StateMachine.DISCONNECTED)
                    }
                    StateMachineInput.DESTROY -> {
                        unbindService(mServiceConnection)
                        mBluetoothLeService = null
                        changeState(StateMachine.NONE)
                    }
                    StateMachineInput.RESUME -> {
                    }
                    StateMachineInput.PAUSE -> {
                        unregisterReceiver(mGattUpdateReceiver)
                        changeState(StateMachine.CONNECTED_PAUSE)
                    }
                    StateMachineInput.GATT_SERVICES_DISCOVERED -> {
                        val gattServices: List<BluetoothGattService>? = obj as? List<BluetoothGattService>
                        findGattCharacteristicsServices(gattServices)
                    }
                    StateMachineInput.FIND_CHARACTERISTIC -> {
                        val gattCharacteristic: BluetoothGattCharacteristic? =
                            obj as? BluetoothGattCharacteristic
                        val charaProp: Int = gattCharacteristic!!.properties
                        if (charaProp or BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService!!.setCharacteristicNotification(
                                    mNotifyCharacteristic!!, false
                                )
                                mNotifyCharacteristic = null
                            }
                            mBluetoothLeService!!.readCharacteristic(gattCharacteristic)
                        }
                        if (charaProp or BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                            mNotifyCharacteristic = gattCharacteristic
                            mBluetoothLeService!!.setCharacteristicNotification(
                                gattCharacteristic, true
                            )
                        }
                    }
                    StateMachineInput.DATA_RECEIVED -> {
                        val data: String? = obj as? String
                        processData(data)
                    }
                    else -> {
                    }
                }
            }
            StateMachine.CONNECTED_PAUSE -> {
                when(stateInput) {
                    StateMachineInput.RESUME -> {
                        registerReceiver(
                            mGattUpdateReceiver,
                            makeGattUpdateIntentFilter()
                        )
                        if (mBluetoothLeService != null) {
                            val result = mBluetoothLeService!!.connect(mDeviceAddress)
                            Log.d(
                                TAG,
                                "Connect request result=$result"
                            )
                        }
                        changeState(StateMachine.CONNECTED)
                    }
                    else -> {
                    }
                }
            }
            StateMachine.DISCONNECTED -> {
                when(stateInput) {
                    StateMachineInput.NONE -> {
                    }
                    else -> {
                    }
                }
            }
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    override fun onDestroy() {
        super.onDestroy()

        stateMachine(StateMachineInput.DESTROY, null)
    }

}
