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
import android.graphics.PorterDuff;
import android.media.Image;
import android.os.Handler;
import android.os.Message;
import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.transition.Slide;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
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
    private static final String ROBOT_MAC_ADDRESS = "00:21:13:00:47:09";//"00:21:13:00:46:44";
    private ConnectThread connectThread;
    protected ConnectedThread connectedThread;
    private static final String TAG = "MY_APP_DEBUG_TAG";
    private Button function1Button;
    private Button function2Button;
    private Button remoteButton;
    private Switch BTSwitch;
    private TextView BTprompt;
    private TextView leftWheelSpeed;
    private DialogFragment connectBTAlert;
 /*   private DialogFragment deviceDisconnected;
    private DialogFragment noBTconnection;
    private DialogFragment socketCreationFailed;
    private DialogFragment socketConnectFailed; */
    private ArrayList<DialogFragment> dialogFragmentArray = new ArrayList<DialogFragment>();
    private boolean UNPAIRED = false;
    private ProgressDialog connectionProgressDialog;


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
                System.out.println("in action found, device name is: " + deviceName );
                String deviceHardwareAddress = device.getAddress(); // MAC address
                System.out.println("device addr is: " + deviceHardwareAddress);
                if(deviceHardwareAddress.equals(ROBOT_MAC_ADDRESS) && deviceName.equals(ROBOT_NAME)) {
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
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                System.out.println("device disconnected");
                DialogFragment deviceDisconnected = AlertDialogFragment.newInstance("Device disconnected",false);
                deviceDisconnected.show(getFragmentManager(),"noBTconnection");
                dialogFragmentArray.add(deviceDisconnected);
            }
        }
    };

   //gets info from connected thread
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //TODO: HANDLE SPEEDS
            switch(msg.what) {
                case MessageConstants.WHEEL_READING_LEFT:
                    System.out.println("wheelSpeed left is " + msg.obj);
                    leftWheelSpeed.setText((String)msg.obj);
                    break;
                case MessageConstants.WHEEL_READING_RIGHT:
                    System.out.println("wheelSpeed right is " + msg.obj);
                    break;
                case MessageConstants.FUNCTION_1_STATE:
                    System.out.println("function 1 state is " + msg.obj);
                    break;
                case MessageConstants.CONNECTION_FAILURE:
                    System.out.println("handling socket connection failure");
                    DialogFragment socketConnectFailed = AlertDialogFragment.newInstance("Socket connection failed", false);
                    socketConnectFailed.show(getFragmentManager(),"noConnectionDialog");
                    dialogFragmentArray.add(socketConnectFailed);
                    disableModeButtons();
                    break;
                case MessageConstants.SOCKET_CONNECTION_SUCCEEDED:
                    System.out.println("socket connection succeeded");
                    connectionProgressDialog.dismiss();
                    enableModeButton();
                    Toast.makeText(getApplicationContext(),"socket connection succeeded",Toast.LENGTH_SHORT).show();

                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                    registerReceiver(mReceiver, filter);

                    break;
                default: System.out.println("invalid tag: "+msg.what);
            }
        }
    };

    private interface ArduinoReadTags {
        byte[] START_TAG_LEFT = "{".getBytes();
        byte[] END_TAG_LEFT = "}".getBytes();
        byte[] START_TAG_RIGHT = "[".getBytes();
        byte[] END_TAG_RIGHT = "]".getBytes();
        byte[] START_TAG_STATE = "#".getBytes();
        byte[] END_TAG_STATE = "*".getBytes();
    }

    private interface ArduinoWriteTags {
        String GO_FORWARD_CMD = "f";
        String GO_BACKWARD_CMD = "b";
        String TURN_LEFT_CMD = "l";
        String TURN_RIGHT_CMD = "r";
        String STOP_CMD = "s";
        /*-------modes--------------*/
        String FUNCTION_ONE = "1";
        String FUNCTION_TWO = "2";
        String REMOTE_CONTROL = "c";
    }

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        int WHEEL_READING_LEFT = 0;
        int WHEEL_READING_RIGHT = 1;
        int FUNCTION_1_STATE = 2;
        int CONNECTION_FAILURE = 3;
        int SOCKET_CONNECTION_SUCCEEDED = 4;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.out.println("in oncreate");
        remoteButton = (Button) findViewById(R.id.remote_control_button);
        function1Button = (Button) findViewById(R.id.function1_button);
        function2Button = (Button) findViewById(R.id.function2_button);
        BTSwitch = (Switch) findViewById(R.id.BT_connection_switch);
        BTprompt = (TextView) findViewById(R.id.BT_connection_prompt);

        BTSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    enableBluetooth();
                }
            }
        });
        setBTpromptVisibility(View.INVISIBLE);

        leftWheelSpeed = (TextView) findViewById(R.id.left_wheel_speed);
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
                System.out.println("hardware address is:" + deviceHardwareAddress);
                if(deviceHardwareAddress.equals(ROBOT_MAC_ADDRESS) && deviceName.equals(ROBOT_NAME)) {
                    robotBluetooth = device;
                    break;
                }
            }
        }
        if (robotBluetooth == null) {
            UNPAIRED = true;
            // Register for broadcasts
            System.out.println("robot bluetooth is null");
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
        //TODO: ADD ALL FRAGMENTS TO ARRAY AT THE TIME OF THEIR INSTANTIATION
        for(DialogFragment dialogFragment : dialogFragmentArray){
            if(dialogFragment != null) {
                dialogFragment.dismiss();
            }
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

    private void setBTpromptVisibility(int visibility){
        if(visibility == View.VISIBLE)
            BTSwitch.setChecked(false);
        BTprompt.setVisibility(visibility);
        BTSwitch.setVisibility(visibility);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private static final String UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB";

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            try {
                setBTpromptVisibility(View.INVISIBLE);

                connectionProgressDialog = new ProgressDialog(MainActivity.this);
                connectionProgressDialog.setMessage("connecting to bluetooth");
                connectionProgressDialog.setCancelable(false);
                connectionProgressDialog.show();

                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                System.out.println("creating a socket");
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(UUID_STRING));
            } catch (IOException e) {
                connectionProgressDialog.dismiss();
                DialogFragment socketCreationFailed = AlertDialogFragment.newInstance("Socket creation failed", false);
                socketCreationFailed.show(getFragmentManager(),"noConnectionDialog");
                dialogFragmentArray.add(socketCreationFailed);
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
                Message connectionFailed = mHandler.obtainMessage(MessageConstants.CONNECTION_FAILURE);
                connectionFailed.sendToTarget();
                connectionProgressDialog.dismiss();
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
        mHandler.obtainMessage(MessageConstants.SOCKET_CONNECTION_SUCCEEDED).sendToTarget();
        connectedThread = new ConnectedThread(mmSocket);
        connectedThread.start();
    }

    public class ConnectedThread extends Thread {
        private static final String TAG = "ConnectedThread";
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        String[] result_strings = new String[3];
        boolean[] foundStart_array = new boolean[3];


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

            /* Initialize the arrays used for reading */
            for(String string : result_strings){
                string = "";
            }
            for(boolean foundStart : foundStart_array){
                foundStart = false;
            }
        }


        /**
         * Check which type of information the incoming data belongs to and process it accordingly
         * @param i the buffer index the data is at
         * @param endTag the endTag that we want to check
         * @param startTag the startTag that we want to check
         * @param messageConstant the message constant to use when sending information to Handler
         */
        private void readIncomingData(int i, int endTag, int startTag, int messageConstant){
            if (mmBuffer[i] == ArduinoReadTags.END_TAG_LEFT[0]){
                System.out.println("result is" + result_strings[messageConstant]);
                // Send the obtained bytes to the UI activity.
                //Message readMsg = mHandler.obtainMessage(messageConstant, Integer.parseInt(result_strings[messageConstant]));
                //TODO test if this is correct
                Message readMsg = mHandler.obtainMessage(messageConstant, result_strings[messageConstant]);
                readMsg.sendToTarget();
                result_strings[messageConstant] = "";
                foundStart_array[messageConstant] = false;
            }
            else if(foundStart_array[messageConstant]) {
                result_strings[messageConstant] = result_strings[messageConstant] + (char) mmBuffer[i];
            }
            else if(mmBuffer[i] == ArduinoReadTags.START_TAG_LEFT[0]){
                foundStart_array[messageConstant] = true;
            }
        }
        /**
         * requires: bytes < mmBuffer.length
         */
        public void run() {
            //mmBuffer = new byte[1024];
            mmBuffer = new byte[1024];
            int index = 0;
            final int maxBytes = 5;
            int bytes;
            boolean foundStart_left = false;
            boolean foundStart_right = false;
            String result_left = "";
            String result_right = "";

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    bytes = mmInStream.read(mmBuffer, index, maxBytes);
                    for(int i = index; i < index + bytes; i++){
                        //TODO test this function
                        readIncomingData(i,ArduinoReadTags.END_TAG_LEFT[0],ArduinoReadTags.START_TAG_LEFT[0],MessageConstants.WHEEL_READING_LEFT);
                        readIncomingData(i,ArduinoReadTags.END_TAG_RIGHT[0],ArduinoReadTags.START_TAG_RIGHT[0],MessageConstants.WHEEL_READING_RIGHT);
                        readIncomingData(i,ArduinoReadTags.END_TAG_STATE[0],ArduinoReadTags.START_TAG_STATE[0],MessageConstants.FUNCTION_1_STATE);
                    }
                    index = index + bytes;
                    if(index >= mmBuffer.length - maxBytes ){
                        index = 0;
                    }

                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        public void writeMsg(String msg){
            byte[] msgBytes = msg.getBytes();
            write(msgBytes);
        }

        // Call this from the main activity to send data to the remote device.
        private void write(byte[] bytes) {

            try {
                System.out.println("writing" + bytes);
                mmOutStream.write(bytes);
            } catch (IOException e) {
                System.out.println("write exception" + bytes);
                Log.e(TAG, "Error occurred when sending data", e);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    public void function2(View view){
        function1Button.setBackgroundResource(0);
        function2Button.setBackgroundResource(R.drawable.dotted_shape);
        remoteButton.setBackgroundResource(0);
        
        connectedThread.writeMsg(ArduinoWriteTags.FUNCTION_TWO);
        createFragment(new FunctionTwoFragment());
    }

    //button callback
    public void function1(View view){
        function1Button.setBackgroundResource(R.drawable.dotted_shape);
        remoteButton.setBackgroundResource(0);
        function2Button.setBackgroundResource(0);

        connectedThread.writeMsg(ArduinoWriteTags.FUNCTION_ONE);
        createFragment(new FunctionOneFragment());
    }
    public void remoteControl(View view){
        remoteButton.setBackgroundResource(R.drawable.dotted_shape);
        function1Button.setBackgroundResource(0);
        function2Button.setBackgroundResource(0);

        connectedThread.writeMsg(ArduinoWriteTags.REMOTE_CONTROL);
        createFragment(new RemoteControlFragment());
    }

    private void createFragment(Fragment fragment) {
        fragment.setEnterTransition(new Slide(Gravity.RIGHT));
        fragment.setExitTransition(new Slide(Gravity.LEFT));
        FragmentTransaction transaction = getFragmentManager().beginTransaction();

        // Replace whatever is in the fragment_container view with this fragment,
        // and add the transaction to the back stack if needed
        transaction.replace(R.id.activity_main, fragment);
        transaction.commit();
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

            forwardButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("forward down");
                        forwardButton.setColorFilter(R.color.colorAccent);
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.GO_FORWARD_CMD);
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("STOP COMMAND");
                        forwardButton.clearColorFilter();
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.STOP_CMD);
                    }
                    return true;
                }
            });
            rightButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("right down");
                        rightButton.setColorFilter(R.color.colorAccent);
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.TURN_RIGHT_CMD);
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("forward up");
                        rightButton.clearColorFilter();
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.STOP_CMD);
                    }
                    return true;
                }
            });
            backwardButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("backward down");
                        backwardButton.setColorFilter(R.color.colorAccent);
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.GO_BACKWARD_CMD);
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("forward up");
                        backwardButton.clearColorFilter();
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.STOP_CMD);
                    }
                    return true;
                }
            });
            leftButton.setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent motionEvent){
                    if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        System.out.println("left down");
                        leftButton.setColorFilter(R.color.colorAccent);
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.TURN_LEFT_CMD);
                    }
                    if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        System.out.println("forward up");
                        leftButton.clearColorFilter();
                        ((MainActivity) getActivity()).connectedThread.writeMsg(ArduinoWriteTags.STOP_CMD);
                    }
                    return true;
                }
            });

            // Inflate the layout for this fragment
            return fragmentView;
        }
    }

    void showAlertDialog() {
        setBTpromptVisibility(View.INVISIBLE);
        connectBTAlert = AlertDialogFragment.newInstance("Connect to bluetooth?",true);
        connectBTAlert.show(getFragmentManager(),"dialog");
        dialogFragmentArray.add(connectBTAlert);
    }

    public void doPositiveClick() {
        enableBluetooth();
        System.out.println("Positive click!");
    }

    public void doNegativeClick() {
        connectBTAlert.dismiss();
        DialogFragment noBTconnection = AlertDialogFragment.newInstance("No bluetooth connection", false);
        noBTconnection.show(getFragmentManager(),"noBTConnection");
        dialogFragmentArray.add(noBTconnection);
        disableModeButtons();
        System.out.println("Negative click!");
    }

    private void disableModeButtons(){
        function1Button.setClickable(false);
        function2Button.setClickable(false);
        remoteButton.setClickable(false);
    }

    private void enableModeButton(){
        function1Button.setClickable(true);
        function2Button.setClickable(true);
        remoteButton.setClickable(true);
    }

    public static class AlertDialogFragment extends DialogFragment {

        public static AlertDialogFragment newInstance(String msg, boolean initConnection) {
            AlertDialogFragment frag = new AlertDialogFragment();
            Bundle args = new Bundle();
         //   args.putString("title", title);
            args.putString("message", msg);
            args.putBoolean("initConnection", initConnection);
            frag.setArguments(args);
            return frag;
        }


        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
         //   String title = getArguments().getString("title");
            String message = getArguments().getString("message");
            boolean initConnection = getArguments().getBoolean("initConnection");

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle("  Bluetooth Connection")
                    .setIcon(R.drawable.alert_icon)
                    .setMessage(message);
            if(initConnection) {
                return builder.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((MainActivity) getActivity()).doPositiveClick();
                            }
                        }
                        )
                        .setNegativeButton("CANCEL",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        ((MainActivity) getActivity()).doNegativeClick();
                                    }
                                }
                        )
                        .create();
            }
            else {
                return builder.setNegativeButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                        ((MainActivity)getActivity()).setBTpromptVisibility(View.VISIBLE);
                    }
                }).create();
            }
        }
    }



}
