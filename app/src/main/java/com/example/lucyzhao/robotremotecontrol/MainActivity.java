package com.example.lucyzhao.robotremotecontrol;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Image;
import android.os.Handler;
import android.os.Message;
import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
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
    private static final String ROBOT_NAME = "HC-06";
    private static final String ROBOT_MAC_ADDRESS = "00:21:13:00:46:44";
    private ConnectThread connectThread;
    protected ConnectedThread connectedThread;
    private static final String TAG = "MY_APP_DEBUG_TAG";
    private Handler mHandler; // handler that gets info from Bluetooth service
    private Button button1;
    private Button button0;
    private DialogFragment connectBTAlert;
    private boolean UNPAIRED = false;

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
                if(deviceName.equals(ROBOT_NAME)) {
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
        System.out.println("in oncreate");
        button0 = (Button) findViewById(R.id.msg0_button);
        button1 = (Button) findViewById(R.id.msg1_button);
    }

    public void enableBluetooth(){
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
            UNPAIRED = true;
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
            UNPAIRED = false;
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
    protected void onResume(){
        super.onResume();
        System.out.println("in onresume");
        showAlertDialog();
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(UNPAIRED) {
            // Don't forget to unregister the ACTION_FOUND receiver.
            unregisterReceiver(mReceiver);
        }
        if(connectThread != null) {connectThread.cancel();}
        if(connectedThread != null) {connectedThread.cancel();}

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        if(UNPAIRED) {
            // Don't forget to unregister the ACTION_FOUND receiver.
            unregisterReceiver(mReceiver);
        }
        if(connectThread != null) {connectThread.cancel();}
        if(connectedThread != null) {connectedThread.cancel();}
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
                //CRASH: create a handler for this thread
          /*      new AlertDialog.Builder(getApplicationContext())
                        .setTitle("Bluetooth Connection")
                        .setIcon(R.drawable.alert_icon)
                        .setMessage("No bluetooth connection")
                        .setNegativeButton("CANCEL",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        dialog.cancel();
                                    }
                                }
                        )
                        .create().show(); */
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
    public void send2(View view){
        createFragment(new FunctionTwoFragment());
    }

    //button callback
    public void send1(View view){
        createFragment(new FunctionOneFragment());
    }
    public void send0(View view){
        createFragment(new RemoteControlFragment());
    }

    private void createFragment(Fragment fragment){
        FragmentManager fm = getFragmentManager();
        if(fm.getBackStackEntryCount() > 0)
            fm.popBackStack();
        fm.beginTransaction()
                .replace(R.id.activity_main, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .show(fragment)
                .addToBackStack(null)
                .commit();
    }


     public static class FunctionOneFragment extends Fragment {
         @Override
         public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                  Bundle savedInstanceState){
             return inflater.inflate(R.layout.fragment_function_one, container, false);
         }
     }

    public static class FunctionTwoFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState){
            return inflater.inflate(R.layout.fragment_function_two, container, false);
        }
    }

     public static class RemoteControlFragment extends Fragment {
         private ImageView forwardButton;
         private ImageView backwardButton;
         private ImageView leftButton;
         private ImageView rightButton;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View fragmentView = inflater.inflate(R.layout.fragment_remote_control, container, false);
            forwardButton = (ImageView) fragmentView.findViewById(R.id.forward_button);
            backwardButton= (ImageView) fragmentView.findViewById(R.id.backward_button);
            leftButton = (ImageView) fragmentView.findViewById(R.id.left_button);
            rightButton = (ImageView) fragmentView.findViewById(R.id.right_button);
            //stopButton = (ImageView) fragmentView.findViewById(R.id.mid_button);

            forwardButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("forward down");
                        byte[] goForward = "f".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(goForward);
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("forward up");
                        byte[] stop = "s".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(stop);
                    }
                    return true;
                }
            });
            rightButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("right down");
                        byte[] turnRight = "r".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(turnRight);
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("forward up");
                        byte[] stop = "s".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(stop);
                    }
                    return true;
                }
            });
            backwardButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("backward down");
               /*         byte[] goBackward = "b".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(goBackward); */
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("forward up");
                 /*       byte[] stop = "s".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(stop); */
                    }
                    return true;
                }
            });
            leftButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("left down");
                        byte[] turnLeft = "l".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(turnLeft);
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("forward up");
                        byte[] stop = "s".getBytes();
                        ((MainActivity) getActivity()).connectedThread.write(stop);
                    }
                    return true;
                }
            });




            // Inflate the layout for this fragment
            return fragmentView;
        }
    }

    void showAlertDialog() {
        connectBTAlert = new AlertDialogFragment();
        connectBTAlert.show(getFragmentManager(), "dialog");
    }

    public void doPositiveClick() {
        enableBluetooth();
        System.out.println("Positive click!");
    }

    public void doNegativeClick() {
        Toast.makeText(getApplicationContext(),"NO BLUETOOTH CONNECTION",Toast.LENGTH_SHORT).show();
        connectBTAlert.dismiss();
        System.out.println("Negative click!");
    }

    public static class AlertDialogFragment extends DialogFragment {

        public static AlertDialogFragment newInstance(String title, String msg, boolean posButton) {
            AlertDialogFragment frag = new AlertDialogFragment();
            Bundle args = new Bundle();
            args.putString("title", title);
            args.putString("message", msg);
            args.putBoolean("posButton", posButton);
            frag.setArguments(args);
            return frag;
        }


        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {


            return new AlertDialog.Builder(getActivity())
                    .setTitle("Bluetooth Connection")
                    .setIcon(R.drawable.alert_icon)
                    .setMessage("Connect to bluetooth?")
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    ((MainActivity)getActivity()).doPositiveClick();
                                }
                            }
                    )
                    .setNegativeButton("CANCEL",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    ((MainActivity)getActivity()).doNegativeClick();
                                }
                            }
                    )
                    .create();
        }
    }



}
