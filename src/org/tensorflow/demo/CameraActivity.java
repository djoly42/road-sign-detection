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
import static org.tensorflow.demo.Config.LOGGING_TAG;

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
  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  // Intent request codes
  private static final int REQUEST_CONNECT_DEVICE = 1;
  private static final int REQUEST_ENABLE_BT = 2;
  public int counter = 0;
  private boolean debug = false;
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

  private static final int PERMISSIONS_REQUEST = 1;

  private Handler handler;
  private HandlerThread handlerThread;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_main);
    mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    displayRightNavigation();
  }

  @Override
  public synchronized void onResume() {
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    if (!isFinishing()) {
      finish();
    }

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException ex) {
      Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
    }

    super.onPause();
  }

  protected synchronized void runInBackground(final Runnable runnable) {
    if (handler != null) {
      handler.post(runnable);
    }
  }

  @Override
  public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
                                         final int[] grantResults) {
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
      return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
              && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
              || shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
        Toast.makeText(CameraActivity.this,
                "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
      }
      requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST);
    }
  }

  protected void setFragment() {
    CameraConnectionFragment cameraConnectionFragment = new CameraConnectionFragment();
    cameraConnectionFragment.addConnectionListener((final Size size, final int rotation) ->
            CameraActivity.this.onPreviewSizeChosen(size, rotation));
    cameraConnectionFragment.addImageAvailableListener(this);

    getFragmentManager()
            .beginTransaction()
            .replace(R.id.container, cameraConnectionFragment)
            .commit();
  }

  public void requestRender() {
    final OverlayView overlay = (OverlayView) findViewById(R.id.overlay);
    if (overlay != null) {
      overlay.postInvalidate();
    }
  }

  public void addCallback(final OverlayView.DrawCallback callback) {
    final OverlayView overlay = (OverlayView) findViewById(R.id.overlay);
    if (overlay != null) {
      overlay.addCallback(callback);
    }
  }

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);


  // method add
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

  public void openLeftDrawer(View view) {
    mDrawerLayout.openDrawer(GravityCompat.START);
  }


  public void openRightDrawer(View view) {
    mDrawerLayout.openDrawer(GravityCompat.END);
  }

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


  //end method add




}
