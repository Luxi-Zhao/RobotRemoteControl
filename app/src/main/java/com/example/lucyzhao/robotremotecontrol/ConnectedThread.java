package com.example.lucyzhao.robotremotecontrol;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

/**
 * Created by LucyZhao on 2017/3/5.
 */

public class ConnectedThread extends Thread {
    private static final String TAG = "ConnectedThread";
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private byte[] mmBuffer; // mmBuffer store for the stream
    public static final byte[] START_TAG_LEFT = "{".getBytes();
    public static final byte[] END_TAG_LEFT = "}".getBytes();
    public static final byte[] START_TAG_RIGHT = "[".getBytes();
    public static final byte[] END_TAG_RIGHT = "]".getBytes();

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

    /**
     * requires: bytes < mmBuffer.length
     */
    public void run() {
        //mmBuffer = new byte[1024];
        mmBuffer = new byte[1024];
        int index = 0;
        final int maxBytes = 5;
        int bytes = maxBytes;
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
                    /*---------state for left wheel readings-----------*/
                    if (mmBuffer[i] == END_TAG_LEFT[0]){
                        System.out.println("left result is " + result_left);
                        result_left = "";
                        foundStart_left = false;
                    }
                    else if(foundStart_left) {
                        result_left = result_left + (char) mmBuffer[i];
                    }
                    else if(mmBuffer[i] == START_TAG_LEFT[0]){
                        foundStart_left = true;
                    }
                    /*---------state for right wheel readings-----------*/
                    if (mmBuffer[i] == END_TAG_RIGHT[0]){
                        System.out.println(" right result is " + result_right);
                        result_right = "";
                        foundStart_right = false;
                    }
                    else if(foundStart_right) {
                        result_right = result_right + (char) mmBuffer[i];
                    }
                    else if(mmBuffer[i] == START_TAG_RIGHT[0]){
                        foundStart_right = true;
                    }
                }
                index = index + bytes;
                if(index >= mmBuffer.length - maxBytes ){
                    index = 0;
                }


                // Send the obtained bytes to the UI activity.
              /*      Message readMsg = mHandler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget(); */
             /*   Scanner s = new Scanner(mmInStream).useDelimiter(",");
                String result = s.hasNext() ? s.next() : ""; */

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

            // Share the sent message with the UI activity.
       /*         Message writtenMsg = mHandler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget(); */
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
