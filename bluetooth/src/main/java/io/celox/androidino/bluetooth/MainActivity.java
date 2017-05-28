/*
 * Copyright (c) 2017 Martin Pfeffer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.celox.androidino.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final UUID DEFAULT_UUID = UUID.fromString(
            "00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket mBluetoothSocket = null;

    private static BluetoothDevice mBluetoothDevice;

    private BluetoothConnection mBluetoothConnection;

    private TextView mTvInfo;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvInfo = (TextView) findViewById(R.id.tv_info);
        final Button btnOn = (Button) findViewById(R.id.btn_on);
        final Button btnOff = (Button) findViewById(R.id.btn_off);

        final ListView listView = (ListView) findViewById(R.id.list_view);
        final ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
        ArrayList<String> bluetoothDeviceNames = new ArrayList<>();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ensureBluetooth();

        for (BluetoothDevice bluetoothDevice : mBluetoothAdapter.getBondedDevices()) {
            bluetoothDevices.add(bluetoothDevice);
            bluetoothDeviceNames.add(bluetoothDevice.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, bluetoothDeviceNames);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mBluetoothDevice = bluetoothDevices.get(position);

                listView.setVisibility(View.INVISIBLE);
                findViewById(R.id.container).setVisibility(View.VISIBLE);

                runOnBackgroundThread(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        try {
                            mBluetoothSocket = createBluetoothSocket();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        mBluetoothAdapter.cancelDiscovery();

                        try {
                            mBluetoothSocket.connect();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        runOnMainUiThread(new Callable() {
                            @Override
                            public Object call() throws Exception {
                                mBluetoothConnection = new BluetoothConnection(mBluetoothSocket);
                                mBluetoothConnection.start();
                                return null;
                            }
                        });
                        return null;
                    }
                });


                btnOn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mBluetoothConnection.write("1");
                    }
                });

                btnOff.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mBluetoothConnection.write("0");
                    }
                });
            }
        });
    }


    private BluetoothSocket createBluetoothSocket() throws IOException {
        try {
            if (mBluetoothDevice != null) {
                return mBluetoothDevice.createRfcommSocketToServiceRecord(
                        mBluetoothDevice.getUuids()[0].getUuid());

            } else {
                Log.w(TAG, "createBluetoothSocket: Device is null.");
            }
        } catch (NullPointerException e) {
            Log.w(TAG, "createBluetoothSocket: UUID from device is null, will use default UUID.");
            try {
                return mBluetoothDevice.createRfcommSocketToServiceRecord(DEFAULT_UUID);

            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;

        }

        Log.e(TAG, "createBluetoothSocket: Error while creating bluetooth socket.");
        return null;
    }


    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: ");
//        closeSocket();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        closeSocket();
        super.onDestroy();
    }


    private void closeSocket() {
        try {
            mBluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void ensureBluetooth() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Missing bluetooth...", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
    }


    private class BluetoothConnection extends Thread {

        private static final String TAG = "BluetoothConnection";
        private static final int ASCII_DELIMITER = 10;

        private InputStream mInputStream;
        private OutputStream mOutputStream;

        private final Handler handler = new Handler();

        BluetoothConnection(BluetoothSocket socket) {
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "BluetoothConnection: ", e);
            }

            mInputStream = inputStream;
            mOutputStream = outputStream;
        }

        public void run() {
            int readBufferPosition = 0;
            byte[] readBuffer = new byte[1024];
            // receive messages
            while (true) {
                try {
                    int bytesAvailable = mInputStream.available();
                    if (bytesAvailable > 0) {
                        byte[] packetBytes = new byte[bytesAvailable];
                        mInputStream.read(packetBytes);
                        for (int i = 0; i < bytesAvailable; i++) {
                            byte b = packetBytes[i];
                            if (b == ASCII_DELIMITER) {
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                final String data = new String(encodedBytes, "US-ASCII");
                                readBufferPosition = 0;

                                handler.post(new Runnable() {
                                    public void run() {
                                        mTvInfo.setText(data);
                                    }
                                });
                            } else {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } catch (IOException ex) {
                    break;
                }
            }
        }

        void write(String message) {
            Log.d(TAG, "Sending data...\n " +
                    message + "\t[ " + byteToHexStr(message.getBytes()) + " ]");

            byte[] msgBuffer = message.getBytes();
            try {
                mOutputStream.write(msgBuffer);
            } catch (IOException e) {
                Log.d(TAG, "Error while sending data.");
            }
        }
    }

    public static void runOnMainUiThread(final Callable callable) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    callable.call();
                } catch (Exception e) {
                    Log.e(TAG, "runOnMainUiThread: ", e);
                }
            }
        });
    }

    public static void runOnBackgroundThread(final Callable callable) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    callable.call();
                } catch (Exception e) {
                    Log.e(TAG, "runOnBackgroundThread: ", e);
                }
            }
        });
    }

    private String byteToHexStr(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

}