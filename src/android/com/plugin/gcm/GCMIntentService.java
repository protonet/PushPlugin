package com.plugin.gcm;

import java.util.List;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.io.IOException;

import com.google.android.gcm.GCMBaseIntentService;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.app.Notification;
import android.app.Notification.*;
import android.net.http.HttpResponseCache;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

  public static final int NOTIFICATION_ID = 237;
  private static final String TAG = "GCMIntentService";
  
  public GCMIntentService() {
    super("GCMIntentService");
  }

  @Override
  public void onRegistered(Context context, String regId) {

    Log.v(TAG, "onRegistered: "+ regId);

    JSONObject json;

    try {
      json = new JSONObject();
      json.put("event", "registered");
      json.put("regid", regId);

      Log.v(TAG, "onRegistered: " + json.toString());

      // Send this JSON data to the JavaScript application above EVENT should be set to the msg type
      // In this case this is the registration ID
      PushPlugin.sendJavascript(json);

    } catch( JSONException e) {
      // No message to the user is sent, JSON failed
      Log.e(TAG, "onRegistered: JSON exception");
    }

    try {
      File httpCacheDir = new File(context.getCacheDir(), "http");
      long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
      HttpResponseCache.install(httpCacheDir, httpCacheSize);
    } catch (IOException e) {}
  }

  @Override
  public void onUnregistered(Context context, String regId) {
    Log.d(TAG, "onUnregistered - regId: " + regId);
  }

  @Override
  protected void onMessage(Context context, Intent intent) {
    Log.d(TAG, "onMessage - context: " + context);

    Bundle extras = intent.getExtras();
    
    String silent = extras.getString("silent");
    
    if (silent != null && silent.equals("true")) {
      onSilentMessage(context, extras);
    } else {
      onLoudMessage(context, extras);
    }
  }

  protected void onSilentMessage(Context context, Bundle extras) {
    try {
      JSONObject json = new JSONObject();
      
      String payload = extras.getString("payload");
      
      JSONObject jsonPayload = new JSONObject(payload);
      
      json.put("event", "silentpush");
      json.put("foreground", PushPlugin.isInForeground());
      json.put("payload", jsonPayload);
      
      Log.v(TAG, "onMessage: " + json.toString());

      // Send this JSON data to the JavaScript application above EVENT should be set to the msg type
      // In this case this is the registration ID
      PushPlugin.sendJavascript(json);

    } catch(JSONException e) {
      // No message to the user is sent, JSON failed
      Log.e(TAG, "onSilentMessage: JSON exception");
    }
  }

  protected void onLoudMessage(Context context, Bundle extras) {
    createNotification(context, extras);
  }

  public void createNotification(Context context, Bundle extras) {
    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    String appName = getAppName(this);

    Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    notificationIntent.putExtra("pushBundle", extras);

    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    String payload = extras.getString("payload");

    if (payload == null) {
      return;
    }

    try {
      JSONObject json = new JSONObject(payload);
      
      Notification.Builder mBuilder =
        new Notification.Builder(context)
          .setAutoCancel(true)
          .setContentTitle(json.getString("title"))
          .setContentText(json.getString("message"))
          .setTicker(json.getString("message"))
          .setSmallIcon(getDefaultIcon(context))
          .setVibrate(new long[] { 300, 300, 300, 300, 300 })
          .setContentIntent(contentIntent)
          .setWhen(System.currentTimeMillis());

      String ledColor = json.has("led_color") ? json.getString("led_color") : null;
      if (ledColor != null) {
        ledColor = ledColor.replace("#", "");
        int aRGBLed = Integer.parseInt(ledColor, 16);
        aRGBLed += 0xFF000000;
        mBuilder.setLights(aRGBLed, 3000, 3000);
      }

      String iconUrl = json.has("icon_url") ? json.getString("icon_url") : null;
      if (iconUrl != null) {
        mBuilder.setLargeIcon(getBitmapFromURL(iconUrl));
      } else {
        mBuilder.setLargeIcon(getDefaultIconAsBitmap(context));
      }

      String color = json.has("color") ? json.getString("color") : null;
      if (color != null && Build.VERSION.SDK_INT >= 21) {
        color = color.replace("#", "");
        int aRGBColor = Integer.parseInt(color, 16);
        aRGBColor += 0xFF000000;
        mBuilder.setColor(aRGBColor);
      }

      mNotificationManager.notify(json.getInt("notification_id"), mBuilder.build());
    } catch(JSONException e) {
      // No message to the user is sent, JSON failed
      Log.e(TAG, "createNotification: JSON exception", e);
    }
  }

  public static void cancelNotification(Context context)
  {
    NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.cancel((String)getAppName(context), NOTIFICATION_ID);
  }

  private static String getAppName(Context context)
  {
    CharSequence appName =
        context
          .getPackageManager()
          .getApplicationLabel(context.getApplicationInfo());

    return (String)appName;
  }

  public static Bitmap getBitmapFromURL(String strURL) {
      try {
          URL url = new URL(strURL);
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setUseCaches(true);
          connection.setDoInput(true);
          connection.connect();
          InputStream input = connection.getInputStream();
          Bitmap myBitmap = BitmapFactory.decodeStream(input);
          return myBitmap;
      } catch (IOException e) {
          e.printStackTrace();
          return null;
      }
  }
  
  public static int getDefaultIcon(Context context) {
    Resources res = context.getResources();
    
    int icon = context.getApplicationInfo().icon;
    
    try {
      icon = res.getIdentifier("notification", "drawable", context.getPackageName());
    } catch(Exception e) {}
    
    return icon;
  }
  
  public static Bitmap getDefaultIconAsBitmap(Context context) {
    int icon = getDefaultIcon(context);
    
    Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), icon);
    
    return bmp;
  }
  
  @Override
  public void onError(Context context, String errorId) {
    Log.e(TAG, "onError - errorId: " + errorId);
  }
}
