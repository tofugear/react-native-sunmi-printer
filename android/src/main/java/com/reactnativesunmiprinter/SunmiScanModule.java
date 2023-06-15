package com.reactnativesunmiprinter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.nio.ByteOrder;

public class SunmiScanModule extends ReactContextBaseJavaModule {
  private static ReactApplicationContext reactContext;
  private static final int START_SCAN = 0x0000;
  private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";
  private static final String E_FAILED_TO_SHOW_SCAN = "E_FAILED_TO_SHOW_SCAN";
  private static final String ACTION_DATA_CODE_RECEIVED = "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED";
  private static final String DATA = "data";
  private static final String SOURCE = "source_byte";
  private Promise mPickerPromise;

  // https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
  private static final char[] LOOKUP_TABLE_LOWER = new char[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66};
  private static final char[] LOOKUP_TABLE_UPPER = new char[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46};

  public static String encodeHex(byte[] byteArray, boolean upperCase, ByteOrder byteOrder) {

    // our output size will be exactly 2x byte-array length
    final char[] buffer = new char[byteArray.length * 2];

    // choose lower or uppercase lookup table
    final char[] lookup = upperCase ? LOOKUP_TABLE_UPPER : LOOKUP_TABLE_LOWER;

    int index;
    for (int i = 0; i < byteArray.length; i++) {
      // for little endian we count from last to first
      index = (byteOrder == ByteOrder.BIG_ENDIAN) ? i : byteArray.length - i - 1;

      // extract the upper 4 bit and look up char (0-A)
      buffer[i << 1] = lookup[(byteArray[index] >> 4) & 0xF];
      // extract the lower 4 bit and look up char (0-A)
      buffer[(i << 1) + 1] = lookup[(byteArray[index] & 0xF)];
    }
    return new String(buffer);
  }

  public static String encodeHex(byte[] byteArray) {
    return encodeHex(byteArray, false, ByteOrder.BIG_ENDIAN);
  }

  private BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_DATA_CODE_RECEIVED.equals(action)) {
        String code = intent.getStringExtra(DATA);
        byte[] arr = intent.getByteArrayExtra(SOURCE);
        // if (arr != null) {
          sendEventHex(arr);
        // }
        if (code != null && !code.isEmpty()) {
          sendEvent(code);
        }
      }
    }
  };

  private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
      if (intent != null) {
        Bundle bundle = intent.getExtras();
        ArrayList<HashMap<String, String>> result = (ArrayList<HashMap<String, String>>) bundle.getSerializable("data");
        if (null != result) {
          Iterator<HashMap<String, String>> it = result.iterator();
          while (it.hasNext()) {
            HashMap hashMap = it.next();
            sendEvent(hashMap.get("VALUE").toString());
          }
        }
      }
    }
  };

  public SunmiScanModule(ReactApplicationContext context) {
    super(context);
    reactContext = context;
    reactContext.addActivityEventListener(mActivityEventListener);
    registerReceiver();
  }

  @Override
  public String getName() {
    return "SunmiScanModule";
  }

  @ReactMethod
  public void scan(final Promise promise) {
    Activity currentActivity = getCurrentActivity();
    if (currentActivity == null) {
      promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
      return;
    }
    mPickerPromise = promise;
    try {
      Intent intent = new Intent("com.sunmi.scan");
      intent.setPackage("com.sunmi.sunmiqrcodescanner");
      intent.putExtra("PLAY_SOUND", true);
      currentActivity.startActivityForResult(intent, START_SCAN);
    } catch (Exception e) {
      mPickerPromise.reject("E_FAILED_TO_SHOW_SCAN", e);
      mPickerPromise = null;
    }
  }

  private void registerReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_DATA_CODE_RECEIVED);
    reactContext.registerReceiver(receiver, filter);
  }

  private static void sendEvent(String msg) {
    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("onScanSuccess", msg);
  }

  private static void sendEventHex(byte[] msg) {
    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("onScanSuccessHex", msg);
  }
}
