package com.diegofn.btlightcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.SeekBar.OnSeekBarChangeListener
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.*

class MainActivity : AppCompatActivity() {
    //
    // GUI Components
    // TextView for logs
    //
    private lateinit var mBluetoothStatus: TextView
    private lateinit var mReadBuffer: TextView

    //
    // Command buttons
    //
    private lateinit var mScanBtn: Button
    private lateinit var mOffBtn: Button
    private lateinit var mListPairedDevicesBtn: Button
    private lateinit var mDiscoverBtn: Button

    //
    // Bluetooth controller
    //
    private lateinit var mBTAdapter: BluetoothAdapter
    private lateinit var mPairedDevices: Set<BluetoothDevice>
    private lateinit var mBTArrayAdapter: ArrayAdapter<String>
    private lateinit var mDevicesListView: ListView

    //
    // Light controls
    //
    private lateinit var mLeftCheck: CheckBox
    private lateinit var mRightCheck: CheckBox
    private lateinit var mLed1Check: CheckBox
    private lateinit var mLed2Check: CheckBox

    //
    // Light timer SeekBar
    //
    private lateinit var mLeftSeekbar: SeekBar
    private lateinit var mRightSeekbar: SeekBar
    private lateinit var mLed1Seekbar: SeekBar
    private lateinit var mLed2Seekbar: SeekBar

    //
    // Handler for bluetooth
    //
    private val TAG = MainActivity::class.java.simpleName
    private var mHandler : Handler? = null // Our main handler that will receive callback notifications
    private var mConnectedThread: ConnectedThread? = null // bluetooth background worker thread to send and receive data
    private var mBTSocket: BluetoothSocket? = null // bi-directional client-to-client data path

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //
        // Match controllers
        //
        mBluetoothStatus = findViewById(R.id.bluetoothStatus)
        mReadBuffer = findViewById(R.id.readBuffer)
        mScanBtn = findViewById(R.id.scan)
        mOffBtn = findViewById(R.id.off)
        mDiscoverBtn = findViewById(R.id.discover)
        mListPairedDevicesBtn = findViewById(R.id.PairedBtn)
        mLeftCheck = findViewById(R.id.checkBoxLeft)
        mRightCheck = findViewById(R.id.checkBoxRight)
        mLed1Check = findViewById(R.id.checkBoxLed1)
        mLed2Check = findViewById(R.id.checkBoxLed2)
        mLeftSeekbar = findViewById(R.id.seekBarLeft)
        mRightSeekbar = findViewById(R.id.seekBarRight)
        mLed1Seekbar = findViewById(R.id.seekBarLed1)
        mLed2Seekbar = findViewById(R.id.seekBarLed2)
        mBTArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        mBTAdapter = BluetoothAdapter.getDefaultAdapter() // get a handle on the bluetooth radio
        mDevicesListView = findViewById(R.id.devicesListView)
        mDevicesListView.adapter = mBTArrayAdapter // assign model to view
        mDevicesListView.onItemClickListener = mDeviceClickListener

        //
        // Ask for location permission if not already allowed
        //
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        mHandler = Handler(Handler.Callback { msg ->
            if (msg.what == MESSAGE_READ) {
                var readMessage: String? = null
                try {
                    readMessage = String((msg.obj as ByteArray), Charset.forName("UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }
                mReadBuffer.text = readMessage
            }
            if (msg.what == CONNECTING_STATUS) {
                if (msg.arg1 == 1) mBluetoothStatus.text = "Connected to Device: " + msg.obj as String else mBluetoothStatus.text = "Connection Failed"
            }
            true
        })
        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.text = "Status: Bluetooth not found"
            Toast.makeText(applicationContext, "Bluetooth device not found!", Toast.LENGTH_SHORT).show()
        } else {
            //
            // Listener for mLeftCheck
            //
            mLeftCheck.setOnClickListener(View.OnClickListener {
                //
                // First check if the Bluetooth is connected
                //
                if (mConnectedThread != null) {
                    var lightCommand = "0,"

                    //
                    // Turn on or off the led
                    //
                    lightCommand += if (mLeftCheck.isChecked) "1," + mLeftSeekbar.progress + ";" else "0," + mLeftSeekbar.progress + ";"

                    //
                    // Send the light command
                    //
                    mConnectedThread!!.write(lightCommand)
                }
            })


            //
            // Listener for mRightCheck
            //
            mRightCheck.setOnClickListener(View.OnClickListener {
                //
                // First check if the Bluetooth is connected
                //
                if (mConnectedThread != null) {
                    var lightCommand = "1,"

                    //
                    // Turn on or off the led
                    //
                    lightCommand += if (mRightCheck.isChecked) "1," + mRightSeekbar.progress + ";" else "0," + mRightSeekbar.progress + ";"

                    //
                    // Send the light command
                    //
                    mConnectedThread!!.write(lightCommand)
                }
            })


            //
            // Listener for mLed1Check
            //
            mLed1Check.setOnClickListener(View.OnClickListener {
                //
                // First check if the Bluetooth is connected
                //
                if (mConnectedThread != null) {
                    var lightCommand = "2,"

                    //
                    // Turn on or off the led
                    //
                    lightCommand += if (mLed1Check.isChecked) "1," + mLed1Seekbar.progress + ";" else "0," + mLed1Seekbar.progress + ";"

                    //
                    // Send the light command
                    //
                    mConnectedThread!!.write(lightCommand)
                }
            })


            //
            // Listener for mLed2Check
            //
            mLed2Check.setOnClickListener(View.OnClickListener {
                //
                // First check if the Bluetooth is connected
                //
                if (mConnectedThread != null) {
                    var lightCommand = "3,"

                    //
                    // Turn on or off the led
                    //
                    lightCommand += if (mLed2Check.isChecked) "1," + mLed2Seekbar.progress + ";" else "0," + mLed2Seekbar.progress + ";"

                    //
                    // Send the light command
                    //
                    mConnectedThread!!.write(lightCommand)
                }
            })

            //
            // Listener for mLeftSeekbar
            //
            mLeftSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                    //
                    // First check if the Bluetooth is connected
                    //
                    if (mConnectedThread != null) {
                        var lightCommand = "0,"

                        //
                        // Turn on or off the led
                        //
                        lightCommand += if (mLeftCheck.isChecked) "1,$progress;" else "0,$progress;"

                        //
                        // Send the light command
                        //
                        mConnectedThread!!.write(lightCommand)
                        Toast.makeText(applicationContext, "Timer set: " + progress + "ms", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

            //
            // Listener for mRightSeekbar
            //
            mRightSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                    //
                    // First check if the Bluetooth is connected
                    //
                    if (mConnectedThread != null) {
                        var lightCommand = "1,"

                        //
                        // Turn on or off the led
                        //
                        lightCommand += if (mRightCheck.isChecked) "1,$progress;" else "0,$progress;"

                        //
                        // Send the light command
                        //
                        mConnectedThread!!.write(lightCommand)
                        Toast.makeText(applicationContext, "Timer set: " + progress + "ms", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

            //
            // Listener for mLed1Seekbar
            //
            mLed1Seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                    //
                    // First check if the Bluetooth is connected
                    //
                    if (mConnectedThread != null) {
                        var lightCommand = "2,"

                        //
                        // Turn on or off the led
                        //
                        lightCommand += if (mLed1Check.isChecked) "1,$progress;" else "0,$progress;"

                        //
                        // Send the light command
                        //
                        mConnectedThread!!.write(lightCommand)
                        Toast.makeText(applicationContext, "Timer set: " + progress + "ms", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })


            //
            // Listener for mLed2Seekbar
            //
            mLed2Seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
                    //
                    // First check if the Bluetooth is connected
                    //
                    if (mConnectedThread != null) {
                        var lightCommand = "3,"

                        //
                        // Turn on or off the led
                        //
                        lightCommand += if (mLed2Check.isChecked) "1,$progress;" else "0,$progress;"

                        //
                        // Send the light command
                        //
                        mConnectedThread!!.write(lightCommand)
                        Toast.makeText(applicationContext, "Timer set: " + progress + "ms", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
            mScanBtn.setOnClickListener(View.OnClickListener { v -> bluetoothOn(v) })
            mOffBtn.setOnClickListener(View.OnClickListener { v -> bluetoothOff(v) })
            mListPairedDevicesBtn.setOnClickListener(View.OnClickListener { v -> listPairedDevices(v) })
            mDiscoverBtn.setOnClickListener(View.OnClickListener { v -> discover(v) })
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bluetoothOn(view: View) {
        if (!mBTAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            mBluetoothStatus.text = "Bluetooth enabled"
            Toast.makeText(applicationContext, "Bluetooth turned on", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(applicationContext, "Bluetooth is already on", Toast.LENGTH_SHORT).show()
        }
    }

    //
    // Enter here after user selects "yes" or "no" to enabling bluetooth
    //
    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, Data: Intent?) {
        //
        // Check which request we're responding to
        //
        if (requestCode == REQUEST_ENABLE_BT) {
            //
            // Make sure the request was successful
            //
            if (resultCode == Activity.RESULT_OK) {
                //
                // The user picked yes.
                // The Intent's data indicate that the user pick yes.
                //
                mBluetoothStatus.text = "Enabled"
            } else mBluetoothStatus.text = "Disabled"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bluetoothOff(view: View) {
        mBTAdapter.disable() // turn off
        mBluetoothStatus.text = "Bluetooth disabled"
        Toast.makeText(applicationContext, "Bluetooth turned Off", Toast.LENGTH_SHORT).show()
    }

    private fun discover(view: View) {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering) {
            mBTAdapter.cancelDiscovery()
            Toast.makeText(applicationContext, "Discovery stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (mBTAdapter.isEnabled) {
                mBTArrayAdapter.clear() // clear items
                mBTAdapter.startDiscovery()
                Toast.makeText(applicationContext, "Discovery started", Toast.LENGTH_SHORT).show()
                registerReceiver(blReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            } else {
                Toast.makeText(applicationContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val blReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // add the name to the list
                mBTArrayAdapter.add("""
    ${device.name}
    ${device.address}
    """.trimIndent())
                mBTArrayAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun listPairedDevices(view: View) {
        mBTArrayAdapter.clear()
        mPairedDevices = mBTAdapter.bondedDevices
        if (mBTAdapter.isEnabled) {

            //
            // Add all Bluetooth devices found
            //
            for (device in mPairedDevices) mBTArrayAdapter.add(device.name + "\n" + device.address)
            Toast.makeText(applicationContext, "Show Paired Devices", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(applicationContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    private val mDeviceClickListener = OnItemClickListener { _, v, _, _ ->
        if (!mBTAdapter.isEnabled) {
            Toast.makeText(baseContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            return@OnItemClickListener
        }
        mBluetoothStatus.text = "Connecting..."

        //
        // Get the device MAC address, which is the last 17 chars in the View
        //
        val info = (v as TextView).text.toString()
        val address = info.substring(info.length - 17)
        val name = info.substring(0, info.length - 17)

        //
        // Spawn a new thread to avoid blocking the GUI one
        //
        object : Thread() {
            override fun run() {
                var fail = false
                val device = mBTAdapter.getRemoteDevice(address)
                try {
                    mBTSocket = createBluetoothSocket(device)
                } catch (e: IOException) {
                    fail = true
                    Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT).show()
                }

                //
                // Establish the Bluetooth socket connection.
                //
                try {
                    mBTSocket!!.connect()
                } catch (e: IOException) {
                    try {
                        fail = true
                        mBTSocket!!.close()
                        mHandler!!.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget()
                    } catch (e2: IOException) {
                        //insert code to deal with this
                        Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT).show()
                    }
                }
                if (!fail) {
                    mConnectedThread = ConnectedThread(mBTSocket)
                    mConnectedThread!!.start()
                    mHandler!!.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget()
                }
            }
        }.start()
    }

    @Throws(IOException::class)
    private fun createBluetoothSocket(device: BluetoothDevice): BluetoothSocket {
        try {
            val m = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
            return m.invoke(device, BTMODULEUUID) as BluetoothSocket
        } catch (e: Exception) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e)
        }
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID)
    }

    //
    // Bluetooth thread class
    //
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket?) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            //
            // buffer store for the stream
            //
            var buffer: ByteArray

            //
            // bytes returned from read()
            //
            var bytes: Int

            //
            // Keep listening to the InputStream until an exception occurs
            //
            while (true) {
                try {
                    //
                    // Read from the InputStream
                    //
                    bytes = mmInStream!!.available()
                    if (bytes != 0) {
                        buffer = ByteArray(1024)
                        SystemClock.sleep(100) //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available() // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes) // record how many bytes we actually read
                        mHandler!!.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget() // Send the obtained bytes to the UI activity
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    break
                }
            }
        }

        //
        // Call this from the main activity to send data to the remote device
        //
        fun write(input: String) {
            //
            // Converts entered String into bytes
            //
            val bytes = input.toByteArray()
            try {
                mmOutStream!!.write(bytes)
            } catch (e: IOException) {
            }
        }

        //
        // Call this from the main activity to shutdown the connection
        //
        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
            }
        }

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            //
            // Get the input and output streams, using temp objects because
            // member streams are final
            //
            try {
                tmpIn = mmSocket!!.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    companion object {
        private val BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // "random" unique identifier

        //
        // #defines for identifying shared types between calling functions
        //
        private const val REQUEST_ENABLE_BT = 1 // used to identify adding bluetooth names
        private const val MESSAGE_READ = 2 // used in bluetooth handler to identify message update
        private const val CONNECTING_STATUS = 3 // used in bluetooth handler to identify message status
    }
}