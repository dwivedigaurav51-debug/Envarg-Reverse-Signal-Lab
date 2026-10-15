package com.envarg.reversesignallab;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
public class ScannerService extends Service {
 public static final String PREF="envarg_scanner";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile boolean active=false;
 private final Runnable tick=new Runnable(){public void run(){if(!active)return;worker.execute(()->probe());handler.postDelayed(this,300000L);}};
 @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("envarg_scan","Virtual scanner status",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
 if(intent!=null&&"STOP".equals(intent.getAction())){stopScanner();return START_NOT_STICKY;}
 Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"envarg_scan"):new Notification.Builder(this);
 b.setContentTitle("Envarg source monitor").setContentText("Checking OTC source every 5 min; no live orders").setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true);
 startForeground(101,b.build());active=true;getSharedPreferences(PREF,0).edit().putBoolean("running",true).apply();handler.removeCallbacks(tick);handler.post(tick);return START_STICKY;
 }
 private void probe(){HttpURLConnection conn=null;String err="";int count=0;try{
 URL url=new URL("https://gw-plus.olymptrade.com/api/assets/v1/EURUSD_OTC");conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(7000);conn.setReadTimeout(9000);conn.setRequestProperty("Accept","application/json");
 if(conn.getResponseCode()!=200)throw new Exception("HTTP "+conn.getResponseCode());
 try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>1048576)throw new Exception("Response too large");}JSONObject j=new JSONObject(out.toString("UTF-8"));if(j.has("candles"))count=j.optJSONArray("candles")==null?0:j.optJSONArray("candles").length();else if(j.has("charts"))count=j.optJSONArray("charts")==null?0:j.optJSONArray("charts").length();}
 }catch(Exception e){err=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}finally{if(conn!=null)conn.disconnect();}
 getSharedPreferences(PREF,0).edit().putLong("lastCheck",System.currentTimeMillis()).putInt("sourceItems",count).putString("lastError",err).apply();
 }
 private void stopScanner(){active=false;handler.removeCallbacks(tick);getSharedPreferences(PREF,0).edit().putBoolean("running",false).apply();stopForeground(true);stopSelf();}
 @Override public void onDestroy(){active=false;handler.removeCallbacks(tick);worker.shutdownNow();getSharedPreferences(PREF,0).edit().putBoolean("running",false).apply();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}