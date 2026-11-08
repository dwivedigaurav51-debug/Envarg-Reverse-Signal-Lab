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
import org.json.JSONArray;
import java.util.TreeMap;
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
 try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>1048576)throw new Exception("Response too large");}JSONObject j=new JSONObject(out.toString("UTF-8"));if(j.has("candles"))count=j.optJSONArray("candles")==null?0:j.optJSONArray("candles").length();else if(j.has("charts"))count=j.optJSONArray("charts")==null?0:j.optJSONArray("charts").length();scanCandles(j);}
 }catch(Exception e){err=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}finally{if(conn!=null)conn.disconnect();}
 getSharedPreferences(PREF,0).edit().putLong("lastCheck",System.currentTimeMillis()).putInt("sourceItems",count).putString("lastError",err).apply();
 }
 private void scanCandles(JSONObject root){
  JSONArray charts=root.optJSONArray("charts"),candles=root.optJSONArray("candles");if(candles==null&&charts!=null){for(int i=0;i<charts.length();i++){JSONObject chart=charts.optJSONObject(i);if(chart==null)continue;int frame=chart.optInt("candle_frame",chart.optInt("frame",chart.optInt("timeframe",300)));if(frame!=300)continue;JSONObject asset=chart.optJSONObject("asset");if(asset!=null&&!asset.optString("symbol","EURUSD_OTC").equals("EURUSD_OTC"))continue;candles=chart.optJSONArray("candles");if(candles!=null)break;}}
  if(candles==null)return;TreeMap<Long,Double> pts=new TreeMap<>();
  for(int i=0;i<candles.length();i++){Object obj=candles.opt(i);long t=0;double c=Double.NaN;if(obj instanceof JSONArray){JSONArray a=(JSONArray)obj;t=a.optLong(0);c=a.optDouble(1,Double.NaN);}else if(obj instanceof JSONObject){JSONObject a=(JSONObject)obj;t=a.optLong("ts",a.optLong("time",a.optLong("timestamp",a.optLong("t",0))));c=a.optDouble("c",a.optDouble("close",a.optDouble("closing_price",Double.NaN)));}if(t>1000000000000000L)t/=1000;if(t>0&&t<100000000000L)t*=1000;if(t>0&&Double.isFinite(c)&&c>0)pts.put(t,c);}
  if(pts.size()<5)return;long latest=pts.lastKey();if(System.currentTimeMillis()-latest>660000L)return;
  Double prev=pts.get(latest-900000L),entry=pts.get(latest);if(prev==null||entry==null||entry.equals(prev))return;
  String dir=entry>prev?"UP":"DOWN";SharedPreferences p=getSharedPreferences(PREF,0);long old=p.getLong("lastSignalTs",0);
  if(latest>old){p.edit().putLong("lastSignalTs",latest).putString("lastSignalDir",dir).putString("lastSignalType","NATIVE CANDIDATE · UNVALIDATED").putLong("lastSignalRecordedAt",System.currentTimeMillis()).apply();}
 }
 private void stopScanner(){active=false;handler.removeCallbacks(tick);getSharedPreferences(PREF,0).edit().putBoolean("running",false).apply();stopForeground(true);stopSelf();}
 @Override public void onDestroy(){active=false;handler.removeCallbacks(tick);worker.shutdownNow();getSharedPreferences(PREF,0).edit().putBoolean("running",false).apply();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}