package com.envarg.reversesignallab;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
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
 private final Runnable tick=new Runnable(){public void run(){if(!active)return;worker.execute(()->probe());handler.postDelayed(this,15000L);}};
 @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("envarg_scan","Virtual scanner status",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);NotificationChannel alert=new NotificationChannel("envarg_health","Market health alerts",NotificationManager.IMPORTANCE_HIGH);alert.setDescription("GOOD or EXCELLENT market conditions; not guaranteed wins");getSystemService(NotificationManager.class).createNotificationChannel(alert);}}
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
  if(pts.size()<5)return;settlePending(pts);assessMarketHealth(pts);long latest=pts.lastKey();if(System.currentTimeMillis()-latest>660000L)return;
  Double prev=pts.get(latest-900000L),entry=pts.get(latest);if(prev==null||entry==null||entry.equals(prev))return;
  String dir=entry>prev?"UP":"DOWN";SharedPreferences p=getSharedPreferences(PREF,0);long old=p.getLong("lastSignalTs",0);
  if(latest>old){saveCandidate(latest,entry,dir,pts);}
 }
 private void assessMarketHealth(TreeMap<Long,Double> pts){
  if(pts.size()<41)return;
  long last=pts.lastKey();if(System.currentTimeMillis()-last>660000L)return;
  java.util.ArrayList<Map.Entry<Long,Double>> a=new java.util.ArrayList<>(pts.entrySet());
  a=new java.util.ArrayList<>(a.subList(a.size()-41,a.size()));
  double[] d=new double[40];double first=0,late=0;
  for(int i=1;i<a.size();i++){if(a.get(i).getKey()-a.get(i-1).getKey()!=300000L)return;d[i-1]=(a.get(i).getValue()-a.get(i-1).getValue())/a.get(i-1).getValue();if(i<=30)first+=Math.abs(d[i-1]);else late+=Math.abs(d[i-1]);}
  double ratio=first>0?(late/10)/(first/30):999;
  int flips=0,prev=0;for(int i=20;i<40;i++){int sign=Double.compare(d[i],0);if(sign!=0){if(prev!=0&&sign!=prev)flips++;prev=sign;}}
  double path=0;for(int i=21;i<41;i++)path+=Math.abs(a.get(i).getValue()-a.get(i-1).getValue());
  double efficiency=path>0?Math.abs(a.get(40).getValue()-a.get(20).getValue())/path:0;
  SharedPreferences pref=getSharedPreferences(PREF,0);
  int count=0,wins=0,streak=0;
  try{JSONArray h=new JSONArray(pref.getString("nativeHistory","[]"));for(int i=h.length()-1;i>=0&&count<20;i--){JSONObject x=h.optJSONObject(i);if(x==null)continue;String outcome=x.optString("result");if(!outcome.equals("WIN")&&!outcome.equals("LOSS"))continue;if(count==0||streak==count){if(outcome.equals("LOSS"))streak++;}count++;if(outcome.equals("WIN"))wins++;}}catch(Exception ignored){}
  // Native history is a separate unvalidated 3-candle candidate stream, NOT main V7 trades.
  String level="NORMAL";
  if(ratio>1.8||flips>=13||efficiency<.19||streak>=3||(count>=10&&wins*1.0/count<.4))level="BAD";
  else if(flips<=5&&efficiency>=.68&&ratio<=1.25&&count>=20&&wins*1.0/count>=.7&&streak==0)level="EXCELLENT";
  else if(flips<=8&&efficiency>=.48&&ratio<=1.45&&count>=10&&wins*1.0/count>=.6&&streak<2)level="GOOD";
  String previous=pref.getString("nativeHealthLevel","UNAVAILABLE");
  pref.edit().putString("nativeHealthLevel",level).putLong("nativeHealthCandle",last).apply();
  if((level.equals("GOOD")||level.equals("EXCELLENT"))&&!level.equals(previous))notifyMarketHealth(level,last);
 }
 private void notifyMarketHealth(String level,long candle){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"envarg_health"):new Notification.Builder(this);
  Intent open=new Intent(this,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  n.setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle("ENVARG · "+level+" MARKET")
   .setContentText("EUR/USD OTC 5m conditions look favorable. Not a guaranteed WIN.")
   .setStyle(new Notification.BigTextStyle().bigText("EUR/USD OTC market health: "+level+". Review the app and strict risk checks. This is an unvalidated market-condition alert, NOT a trading instruction."))
   .setContentIntent(pending).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
  ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(204,n.build());
 }
 private String nativeSkipReason(JSONArray history,TreeMap<Long,Double> pts,String dir){
  int streak=0,settled=0,wins=0;boolean streakOpen=true;
  for(int i=history.length()-1;i>=0;i--){
   JSONObject x=history.optJSONObject(i);if(x==null)continue;
   String outcome=x.optString("result");if(!outcome.equals("WIN")&&!outcome.equals("LOSS"))continue;
   if(streakOpen){if(outcome.equals("LOSS"))streak++;else streakOpen=false;}
   if(settled<20){settled++;if(outcome.equals("WIN"))wins++;}
  }
  // This is a conservative candidate gate, not a predictive model.
  if(streak>=2)return "2+ CONSECUTIVE LOSSES · SHADOW ONLY";
  if(settled>=10&&wins*1.0/settled<.5)return "RECENT NATIVE WIN RATE BELOW 50%";
  // Avoid re-issuing the same 3-candle direction after two matching losses.
  int similarLoss=0,similarTotal=0;
  for(int i=history.length()-1;i>=0;i--){
   JSONObject x=history.optJSONObject(i);if(x==null||!dir.equals(x.optString("dir")))continue;
   String result=x.optString("result");if(!result.equals("WIN")&&!result.equals("LOSS"))continue;
   similarTotal++;if(result.equals("LOSS"))similarLoss++;if(similarTotal>=8)break;
  }
  if(similarTotal>=5&&similarLoss*1.0/similarTotal>=.6)return "REPEATED DIRECTION LOSS "+similarLoss+"/"+similarTotal;
  return "";
 }
 private synchronized void saveCandidate(long ts,double entry,String dir,TreeMap<Long,Double> pts){
  SharedPreferences p=getSharedPreferences(PREF,0);
  try{
   JSONArray history=new JSONArray(p.getString("nativeHistory","[]"));
   String skipReason=nativeSkipReason(history,pts,dir);boolean skipped=!skipReason.isEmpty();
   JSONObject item=new JSONObject();item.put("selected",!skipped);item.put("skipReason",skipReason);item.put("ts",ts);item.put("entry",entry);item.put("expiry",ts+300000L);item.put("dir",dir);item.put("result","PENDING");item.put("type",skipped?"SHADOW ONLY · SAFETY SKIP":"NATIVE 3-CANDLE CANDIDATE · NOT V7 AI");
   history.put(item);JSONArray trimmed=new JSONArray();for(int i=Math.max(0,history.length()-200);i<history.length();i++)trimmed.put(history.get(i));
   p.edit().putLong("lastSignalTs",ts).putString("lastSignalDir",dir).putString("lastSignalType",skipped?"SHADOW SKIP · "+skipReason:"NATIVE CANDIDATE · UNVALIDATED").putLong("lastSignalRecordedAt",System.currentTimeMillis()).putString("nativeHistory",trimmed.toString()).commit();
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"envarg_scan"):new Notification.Builder(this);
   b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle(skipped?"ENVARG candidate SKIPPED":"ENVARG OTC virtual candidate").setContentText(skipped?skipReason:dir+" · 5m · unvalidated · no real order").setAutoCancel(true);
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