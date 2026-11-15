package com.envarg.olympiatradesignal;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONObject;
public class MainActivity extends Activity {
 WebView web; ValueCallback<Uri[]> pending; final int PICK=100; final ExecutorService network=Executors.newFixedThreadPool(2);
 @Override public void onCreate(Bundle b){super.onCreate(b);
 if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},221);}
 web=new WebView(this); web.getSettings().setJavaScriptEnabled(true);
 web.getSettings().setAllowFileAccess(true);
 web.getSettings().setAllowFileAccessFromFileURLs(false);
 web.addJavascriptInterface(new SourceBridge(),"EnvargSource");
 // Prevent remote pages from inheriting the privileged native JS bridge.
 web.setWebViewClient(new WebViewClient(){
  private boolean handle(String url){
   if(url!=null&&url.startsWith("file:///android_asset/"))return false;
   try{Uri u=Uri.parse(url);String scheme=u.getScheme();if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)){startActivity(new Intent(Intent.ACTION_VIEW,u));}}catch(Exception ignored){}
   return true;
  }
  @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return handle(request.getUrl().toString());}
  @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return handle(url);}
 });
 web.setWebChromeClient(new WebChromeClient(){
 @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p){
 if(pending!=null)pending.onReceiveValue(null); pending=cb;
 Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
 startActivityForResult(Intent.createChooser(i,"Select CSV"),PICK);return true;}});
 setContentView(web);web.loadUrl("file:///android_asset/index.html");}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);
 if(r==PICK&&pending!=null){pending.onReceiveValue(c==RESULT_OK&&d!=null?new Uri[]{d.getData()}:null);pending=null;}}
 public class SourceBridge {
  @JavascriptInterface public String readOneWayHistory(){return getSharedPreferences("envarg_v11",MODE_PRIVATE).getString("oneway_days","[]");}
  @JavascriptInterface public void saveOneWayHistory(String json){
   if(json==null||json.length()>1500000)return;
   try{org.json.JSONArray a=new org.json.JSONArray(json);if(a.length()>5000)return;getSharedPreferences("envarg_v11",MODE_PRIVATE).edit().putString("oneway_days",json).apply();}catch(Exception ignored){}
  }
  @JavascriptInterface public String readMarketHistory(){return getSharedPreferences("envarg_v11",MODE_PRIVATE).getString("daily_market","[]");}
  @JavascriptInterface public void saveMarketHistory(String json){
   if(json==null||json.length()>1500000)return;
   try{org.json.JSONArray a=new org.json.JSONArray(json);if(a.length()>12000)return;getSharedPreferences("envarg_v11",MODE_PRIVATE).edit().putString("daily_market",json).apply();}catch(Exception ignored){}
  }
  @JavascriptInterface public String readResearchMemory(){return getSharedPreferences("envarg_v11",MODE_PRIVATE).getString("memory","[]");}
  @JavascriptInterface public void saveResearchMemory(String json){
   if(json==null||json.length()>1500000)return;
   try{org.json.JSONArray a=new org.json.JSONArray(json);if(a.length()>3000)return;getSharedPreferences("envarg_v11",MODE_PRIVATE).edit().putString("memory",json).apply();}catch(Exception ignored){}
  }
  @JavascriptInterface public void notifyMarketCondition(String level,String message){
   if(!("GOOD".equals(level)||"EXCELLENT".equals(level)))return;
   runOnUiThread(()->{
    try{
     android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
     String channel="envarg_market_v11";
     if(android.os.Build.VERSION.SDK_INT>=26){android.app.NotificationChannel ch=new android.app.NotificationChannel(channel,"Market condition alerts",android.app.NotificationManager.IMPORTANCE_DEFAULT);nm.createNotificationChannel(ch);}
     android.app.Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(MainActivity.this,channel):new android.app.Notification.Builder(MainActivity.this);
     b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("ENVARG "+level+" Market · Research").setContentText(message).setAutoCancel(true);
     nm.notify(1101,b.build());
    }catch(Exception ignored){}
   });
  }

  @JavascriptInterface public void notifyResearchWindow(String direction,String symbol,long startMs,long endMs,double stake,int score){
   if(!("UP".equals(direction)||"DOWN".equals(direction))||!"EURUSD_OTC".equals(symbol))return;
   long now=System.currentTimeMillis();
   if(startMs>now+15000||endMs<=now+15000||endMs-now>90000||score<85||!Double.isFinite(stake)||stake<=0||stake>100000)return;
   if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
   runOnUiThread(()->{try{
    String channel="olympia_research_windows_v1";
    android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    if(android.os.Build.VERSION.SDK_INT>=26){android.app.NotificationChannel ch=new android.app.NotificationChannel(channel,"Olympia research UP/DOWN alerts",android.app.NotificationManager.IMPORTANCE_HIGH);ch.enableVibration(true);nm.createNotificationChannel(ch);}
    java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("hh:mm:ss a",java.util.Locale.getDefault());
    String time=fmt.format(new java.util.Date(startMs))+" – "+fmt.format(new java.util.Date(endMs));
    String title=("UP".equals(direction)?"🟢 UP":"🔴 DOWN")+" · EUR/USD OTC · RESEARCH";
    String body="Possible virtual window "+time+" · max illustrative stake $"+String.format(java.util.Locale.US,"%.2f",stake)+" · strength "+score+"/100. Not a guaranteed or live trade signal. Recheck before acting.";
    Intent open=new Intent(MainActivity.this,MainActivity.class);
    android.app.PendingIntent pending=android.app.PendingIntent.getActivity(MainActivity.this,0,open,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
    android.app.Notification.Builder n=android.os.Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(MainActivity.this,channel):new android.app.Notification.Builder(MainActivity.this);
    n.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new android.app.Notification.BigTextStyle().bigText(body)).setColor("UP".equals(direction)?0xff16c784:0xfff05263).setColorized(true).setContentIntent(pending).setAutoCancel(true).setPriority(android.app.Notification.PRIORITY_HIGH).setDefaults(android.app.Notification.DEFAULT_ALL).setWhen(now).setTimeoutAfter(Math.max(1000,endMs-now));
    nm.notify(2050,n.build());
   }catch(Exception ignored){}});
  }

  @JavascriptInterface public void startMonitor(){runOnUiThread(()->{try{Intent i=new Intent(MainActivity.this,ScannerService.class);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){getSharedPreferences(ScannerService.PREF,0).edit().putString("lastError",e.toString()).apply();}});}
  @JavascriptInterface public void stopMonitor(){runOnUiThread(()->{Intent i=new Intent(MainActivity.this,ScannerService.class);i.setAction("STOP");startService(i);});}
  @JavascriptInterface public String monitorStatus(){android.content.SharedPreferences p=getSharedPreferences(ScannerService.PREF,0);JSONObject j=new JSONObject();try{j.put("running",p.getBoolean("running",false));j.put("lastCheck",p.getLong("lastCheck",0));j.put("sourceItems",p.getInt("sourceItems",0));j.put("error",p.getString("lastError",""));j.put("nativeHistory",new org.json.JSONArray(p.getString("nativeHistory","[]")));j.put("lastSignalTs",p.getLong("lastSignalTs",0));j.put("lastSignalDir",p.getString("lastSignalDir",""));j.put("lastSignalType",p.getString("lastSignalType",""));j.put("lastSignalRecordedAt",p.getLong("lastSignalRecordedAt",0));}catch(Exception ignored){}return j.toString();}

  @JavascriptInterface public void fetchFiveMinute(String symbol){
   if(symbol==null||!symbol.matches("[A-Z0-9_]{3,24}"))return;
   network.execute(()->{
    String result="";String error="";
    HttpURLConnection conn=null;
    try{
     URL u=new URL("https://gw-plus.olymptrade.com/api/assets/v1/"+symbol);
     conn=(HttpURLConnection)u.openConnection();conn.setRequestMethod("GET");conn.setConnectTimeout(7000);conn.setReadTimeout(9000);conn.setRequestProperty("Accept","application/json");
     int code=conn.getResponseCode();if(code!=200)throw new Exception("HTTP "+code);
     InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>1024*1024)throw new Exception("Response too large");}in.close();result=out.toString("UTF-8");new JSONObject(result);
    }catch(Exception ex){error=ex.getClass().getSimpleName()+": "+ex.getMessage();}finally{if(conn!=null)conn.disconnect();}
    final String payload=result,err=error;runOnUiThread(()->web.evaluateJavascript("window.envargNativeFeed("+JSONObject.quote(symbol)+","+JSONObject.quote(payload)+","+JSONObject.quote(err)+")",null));
   });
  }
 }
 @Override protected void onDestroy(){network.shutdownNow();super.onDestroy();}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}