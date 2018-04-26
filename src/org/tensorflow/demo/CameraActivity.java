/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image.Plane;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.tensorflow.demo.env.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import androidRecyclerView.MessageAdapter;
import androidRecyclerView.MessageManager;

public abstract class CameraActivity extends AppCompatActivity implements OnImageAvailableListener {
  //CHAT BLUETOOTH
  // Message types sent from the BluetoothChatService Handler
  public static final int MESSAGE_STATE_CHANGE = 1;
  public static final int MESSAGE_READ = 2;
  public static final int MESSAGE_WRITE = 3;
  public static final int MESSAGE_DEVICE_NAME = 4;
  public static final int MESSAGE_TOAST = 5;
  // Key names received from the BluetoothChatService Handler
  public static final String DEVICE_NAME = "device_name";
  public static final String TOAST = "toast";
  private static final Logger LOGGER = new Logger();
  private static final int PERMISSIONS_REQUEST = 1;
  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  // Intent request codes
  private static final int REQUEST_CONNECT_DEVICE = 1;
  private static final int REQUEST_ENABLE_BT = 2;
  public int counter = 0;
  private boolean debug = false;
  private Handler handler;
  private HandlerThread handlerThread;
  //private EditText mOutEditText;
  private DrawerLayout mDrawerLayout;
  // Name of the connected device
  private String mConnectedDeviceName = null;
  // String buffer for outgoing messages
  private StringBuffer mOutStringBuffer;
  // Local Bluetooth adapter
  private BluetoothAdapter mBluetoothAdapter = null;
  // Member object for the chat services
  private BluetoothChatService mChatService = null;
  private RecyclerView mRecyclerView;
  private LinearLayoutManager mLayoutManager;
  private MessageAdapter mAdapter;
  private List messageList = new ArrayList();

  private MessageManager messageManager = null;
  protected boolean computing = false;


  protected boolean istart = false;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_main);
    mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    mLayoutManager = new LinearLayoutManager(this);
    mAdapter = new MessageAdapter(getBaseContext(), messageList);
    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    // If the adapter is null, then Bluetooth is not supported
    if (mBluetoothAdapter == null) {
      Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
      finish();
      return;
    }
    displayRightNavigation();
    messageManager = new MessageManager();
  }

  // The Handler that gets information back from the BluetoothChatService
  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(final Message msg) {
      switch (msg.what) {
        case MESSAGE_WRITE:
          byte[] writeBuf = (byte[]) msg.obj;
          // construct a string from the buffer
          String writeMessage = new String(writeBuf);
          mAdapter.notifyDataSetChanged();
          messageList.add(new androidRecyclerView.Message(counter++, writeMessage, "Me"));
          break;
        case MESSAGE_READ:
          byte[] readBuf = (byte[]) msg.obj;
          // construct a string from the valid bytes in the buffer
          String readMessage = new String(readBuf, 0, msg.arg1);
          mAdapter.notifyDataSetChanged();
          messageList.add(new androidRecyclerView.Message(counter++, readMessage, mConnectedDeviceName));
          break;
        case MESSAGE_DEVICE_NAME:
          // save the connected device's name
          mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
          Toast.makeText(getApplicationContext(), "Connected to "
                  + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
          break;
        case MESSAGE_TOAST:
          new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
              Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                      Toast.LENGTH_SHORT).show();
            }
          });
          break;
      }
    }
  };

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
    if (!mBluetoothAdapter.isEnabled()) {
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    } else {
      if (mChatService == null) setupChat();
    }
  }

  private void setupChat() {
    //mOutEditText = (EditText) findViewById(R.id.edit_text_out);
    //mOutEditText.setOnEditorActionListener(mWriteListener);

    // Initialize the BluetoothChatService to perform bluetooth connections
    mChatService = new BluetoothChatService(this, mHandler);

    // Initialize the buffer for outgoing messages
    mOutStringBuffer = new StringBuffer("");
  }


  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());

    if (mChatService != null) {
      if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
        mChatService.start();
      }
    }
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);
    super.onPause();

    if (!isFinishing()) {
      LOGGER.d("Requesting finish");
      //finish();
    }

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
    // Stop the Bluetooth chat services
    if (mChatService != null) mChatService.stop();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          final int requestCode, final String[] permissions, final int[] grantResults) {
    switch (requestCode) {
      case PERMISSIONS_REQUEST: {
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
          setFragment();
        } else {
          requestPermission();
        }
      }
    }
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
        Toast.makeText(CameraActivity.this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
      }
      requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
    }
  }

  protected void setFragment() {
    final Fragment fragment =
            CameraConnectionFragment.newInstance(
                    new CameraConnectionFragment.ConnectionCallback() {
                      @Override
                      public void onPreviewSizeChosen(final Size size, final int rotation) {
                        CameraActivity.this.onPreviewSizeChosen(size, rotation);
                      }
                    },
                    this,
                    getLayoutId(),
                    getDesiredPreviewFrameSize());

    getFragmentManager()
            .beginTransaction()
            .replace(R.id.container, fragment)
            .commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  public void requestRender() {
    final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
    if (overlay != null) {
      overlay.postInvalidate();
    }
  }

  public void addCallback(final OverlayView.DrawCallback callback) {
    final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
    if (overlay != null) {
      overlay.addCallback(callback);
    }
  }

  public void onSetDebug(final boolean debug) {
  }

  @Override
  public boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      debug = !debug;
      requestRender();
      onSetDebug(debug);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  public void openLeftDrawer(View view) {
    mDrawerLayout.openDrawer(GravityCompat.START);
  }




  public void openRightDrawer(View view) {
    mDrawerLayout.openDrawer(GravityCompat.END);
  }

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  private void displayRightNavigation() {
    final NavigationView navigationViewRight = (NavigationView) findViewById(R.id.nav_view_right);
    navigationViewRight.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
      @Override
      public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        /*
        if (id == R.id.nav_camera_right) {
          // Handle the camera action
        } else if (id == R.id.nav_gallery_right) {

        } else if (id == R.id.nav_slideshow_right) {

        } else if (id == R.id.nav_manage_right) {

        } else if (id == R.id.nav_share_right) {

        } else if (id == R.id.nav_send_right) {

        }
*/
        Toast.makeText(CameraActivity.this, "Handle from navigation right", Toast.LENGTH_SHORT).show();
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.END);
        return true;

      }
    });
  }

  @Override
  public void onBackPressed() {
    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
    if (drawer.isDrawerOpen(GravityCompat.START)) {
      drawer.closeDrawer(GravityCompat.START);
    } else if (drawer.isDrawerOpen(GravityCompat.END)) {
      drawer.closeDrawer(GravityCompat.END);
    } else {
      super.onBackPressed();
    }
  }


  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

    if (id == R.id.action_settings) {
      drawer.openDrawer(GravityCompat.END);
    }

    return super.onOptionsItemSelected(item);
  }

  // bluetooth


  public void sendMessage(String message) {

    if(!this.messageManager.doSend(message)){
      Log.d("messageManager", "dont send message :" + message);

      return;
    }

    // Check that we're actually connected before trying anything
    if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
      Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
      return;
    }
    // Check that there's actually something to send
    if (message.length() > 0) {
      // Get the message bytes and tell the BluetoothChatService to write
      byte[] send = message.getBytes();
      mChatService.write(send);
      // Reset out string buffer to zero and clear the edit text field
      mOutStringBuffer.setLength(0);
      //mOutEditText.setText(mOutStringBuffer);
    }
  }

  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_CONNECT_DEVICE:
        // When DeviceListActivity returns with a device to connect
        if (resultCode == Activity.RESULT_OK) {
          // Get the device MAC address
          String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
          // Get the BLuetoothDevice object
          BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
          // Attempt to connect to the device
          mChatService.connect(device);
        }
        break;
      case REQUEST_ENABLE_BT:
        // When the request to enable Bluetooth returns
        if (resultCode == Activity.RESULT_OK) {
          // Bluetooth is now enabled, so set up a chat session
          setupChat();
        } else {
          // User did not enable Bluetooth or an error occured
          Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
          finish();
        }
    }
  }

  public void connect(MenuItem i) {
    Intent serverIntent = new Intent(this, DeviceListActivity.class);
    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
  }


  public void discoverable(MenuItem i) {
    ensureDiscoverable();
  }

  private void ensureDiscoverable() {
    if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
      Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
      discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
      startActivity(discoverableIntent);
    }
  }

  public void stopAndStart (View view){
        if (!istart){
          Log.d("stopAndStart" , "start");
          istart = true;

        }
        else{
          Log.d("stopAndStart" , "stop");
          istart = false;
        }
    }
}
