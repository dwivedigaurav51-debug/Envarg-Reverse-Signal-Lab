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
import java.util.Map;
public class ScannerService extends Service {
 public static final String PREF="envarg_scanner";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile boolean active=false;
 private final Runnable tick=new Runnable(){public void run(){if(!active)return;worker.execute(()->probe());handler.postDelayed(this,60000L);}};
 @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("envarg_scan","Virtual scanner status",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
 if(intent!=null&&"STOP".equals(intent.getAction())){stopScanner();return START_NOT_STICKY;}
 Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"envarg_scan"):new Notification.Builder(this);
 b.setContentTitle("Envarg source monitor").setContentText("Checking OTC candles in background; virtual only").setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true);
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
  if(pts.size()<5)return;settlePending(pts);long latest=pts.lastKey();if(System.currentTimeMillis()-latest>660000L)return;
  Double prev=pts.get(latest-900000L),entry=pts.get(latest);if(prev==null||entry==null||entry.equals(prev))return;
  String dir=entry>prev?"UP":"DOWN";SharedPreferences p=getSharedPreferences(PREF,0);long old=p.getLong("lastSignalTs",0);
  if(latest>old){saveCandidate(latest,entry,dir);}
 }
 private synchronized void saveCandidate(long ts,double entry,String dir){
  SharedPreferences p=getSharedPreferences(PREF,0);
  try{
   JSONArray history=new JSONArray(p.getString("nativeHistory","[]"));
   JSONObject item=new JSONObject();item.put("ts",ts);item.put("entry",entry);item.put("expiry",ts+300000L);item.put("dir",dir);item.put("result","PENDING");item.put("type","NATIVE 3-CANDLE CANDIDATE · NOT V7 AI");
   history.put(item);JSONArray trimmed=new JSONArray();for(int i=Math.max(0,history.length()-200);i<history.length();i++)trimmed.put(history.get(i));
   p.edit().putLong("lastSignalTs",ts).putString("lastSignalDir",dir).putString("lastSignalType","NATIVE CANDIDATE · UNVALIDATED").putLong("lastSignalRecordedAt",System.currentTimeMillis()).putString("nativeHistory",trimmed.toString()).commit();
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"envarg_scan"):new Notification.Builder(this);
   b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("ENVARG OTC virtual candidate").setContentText(dir+" · 5m · unvalidated · no real order").setAutoCancel(true);
   ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(102,b.build());
  }catch(Exception e){p.edit().putString("lastError","Save candidate: "+e.getMessage()).apply();}
 }
 private synchronized void settlePending(TreeMap<Long,Double> pts){
  SharedPreferences p=getSharedPreferences(PREF,0);
  try{
   JSONArray history=new JSONArray(p.getString("nativeHistory","[]"));boolean changed=false;
   for(int i=0;i<history.length();i++){JSONObject v=history.optJSONObject(i);if(v==null||!"PENDING".equals(v.optString("result")))continue;
    long expiry=v.optLong("expiry");Double exit=pts.get(expiry);if(exit==null)continue;
    double entry=v.optDouble("entry");String dir=v.optString("dir");String outcome=exit==entry?"TIE":((exit>entry)==dir.equals("UP")?"WIN":"LOSS");
    v.put("result",outcome);v.put("reverseResult",outcome.equals("WIN")?"LOSS":outcome.equals("LOSS")?"WIN":"TIE");v.put("exit",exit);changed=true;
   }
   if(changed)p.edit().putString("nativeHistory",history.toString()).commit();
  }catch(Exception e){p.edit().putString("lastError","Settlement: "+e.getMessage()).apply();}
 }
 private void stopScanner(){active=false;handler.removeCallbacks(tick);getSharedPreferences(PREF,0).edit().putBoolean("running",false).apply();stopForeground(true);stopSelf();}
 @Override public void onDestroy(){active=false;handler.removeCallbacks(tick);worker.shutdownNow();getSharedPreferences(PREF,0).edit().putBoolean("running",false).apply();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}