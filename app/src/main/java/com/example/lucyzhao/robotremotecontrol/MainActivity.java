package com.example.lucyzhao.robotremotecontrol;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice robotBluetooth = null;
    private static final String ROBOT_MAC_ADDRESS = "00:21:13:00:46:44";
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private static final String TAG = "MY_APP_DEBUG_TAG";
    private Handler mHandler; // handler that gets info from Bluetooth service

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private ProgressDialog mDialog;

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if(deviceName.equals("HC-06")) {
                    robotBluetooth = device;
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mDialog = new ProgressDialog(MainActivity.this);
                mDialog.setMessage("looking for devices");
                mDialog.setCancelable(false);
                mDialog.show();
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                if(robotBluetooth == null){
                    Toast.makeText(getApplicationContext(),"robot not found",Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(),"robot found, connecting",Toast.LENGTH_SHORT).show();
                    connectToRobot();
                }
                mDialog.dismiss();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    //button callback
    public void enableBluetooth(View view){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this,"your device does not support bluetooth", Toast.LENGTH_LONG).show();
        }
        else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            findBluetoothDevice();
        }
    }

    private void findBluetoothDevice(){
        //querying paired devices
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            System.out.println(pairedDevices.size() + " paired devices");
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                System.out.println("paired with: " + deviceName);
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if(deviceHardwareAddress.equals(ROBOT_MAC_ADDRESS)) {
                    robotBluetooth = device;
                    break;
                }
            }
        }
        if (robotBluetooth == null) {
            // Register for broadcasts
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mReceiver, filter);
            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            registerReceiver(mReceiver, filter);
            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(mReceiver, filter);
            mBluetoothAdapter.startDiscovery();
        }
        else {
            //robot found, start connecting to it
            connectToRobot();
        }
    }

    private void connectToRobot(){
        System.out.println("new connectThread");
        connectThread = new ConnectThread(robotBluetooth);
        System.out.println("starting connectThread");
        connectThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
        connectThread.cancel();
        connectedThread.cancel();
    }


    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data)  {
        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == RESULT_OK){
                findBluetoothDevice();
            }
        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        ProgressDialog mDialog;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            mDialog = new ProgressDialog(MainActivity.this);
            mDialog.setMessage("Connecting to bluetooth...");
            mDialog.setCancelable(false);
            mDialog.show();

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                System.out.println("creating a socket");
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            } catch (IOException e) {
                Log.e("IOException", "Socket's create() method failed", e);
                Toast.makeText(getApplicationContext(),"socket create failed",Toast.LENGTH_SHORT).show();
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            System.out.println("running connect thread");
            mBluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                System.out.println("mmSocket.connect()");
                mmSocket.connect();

            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                Toast.makeText(getApplicationContext(),"socket connection failed",Toast.LENGTH_SHORT).show();
                System.out.println("unable to connect to socket");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("IOException", "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            mDialog.dismiss();
            manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("IOException", "Could not close the client socket", e);
            }
        }
    }

    private void manageMyConnectedSocket(BluetoothSocket mmSocket) {
        System.out.println("managing my connected socket");
        connectedThread = new ConnectedThread(mmSocket);
        connectedThread.start();
    }


    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
              /*      Message readMsg = mHandler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget(); */
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    Toast.makeText(getApplicationContext(),"input stream disconnected",Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {

            try {
                System.out.println("writing" + bytes);
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
       /*         Message writtenMsg = mHandler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget(); */
            } catch (IOException e) {
                System.out.println("write exception" + bytes);
                Log.e(TAG, "Error occurred when sending data", e);
                Toast.makeText(getApplicationContext(),"unable to write",Toast.LENGTH_LONG).show();
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
                Toast.makeText(getApplicationContext(),"unable to close socket",Toast.LENGTH_LONG).show();
            }
        }
    }

    //button callback
    public void send1(View view){
        byte[] number_one = "1".getBytes();
        connectedThread.write(number_one);
    }
    public void send0(View view){
        byte[] number_zero = "0".getBytes();
        connectedThread.write(number_zero);
    }


}
