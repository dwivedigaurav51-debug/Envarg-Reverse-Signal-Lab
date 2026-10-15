package com.envarg.reversesignallab;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
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
 web=new WebView(this); web.getSettings().setJavaScriptEnabled(true);
 web.getSettings().setAllowFileAccess(true);
 web.getSettings().setAllowFileAccessFromFileURLs(false);
 web.addJavascriptInterface(new SourceBridge(),"EnvargSource");
 web.setWebChromeClient(new WebChromeClient(){
 @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p){
 if(pending!=null)pending.onReceiveValue(null); pending=cb;
 Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
 startActivityForResult(Intent.createChooser(i,"Select CSV"),PICK);return true;}});
 setContentView(web);web.loadUrl("file:///android_asset/index.html");}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);
 if(r==PICK&&pending!=null){pending.onReceiveValue(c==RESULT_OK&&d!=null?new Uri[]{d.getData()}:null);pending=null;}}
 public class SourceBridge {
  @JavascriptInterface public void startMonitor(){runOnUiThread(()->{try{Intent i=new Intent(MainActivity.this,ScannerService.class);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){getSharedPreferences(ScannerService.PREF,0).edit().putString("lastError",e.toString()).apply();}});}
  @JavascriptInterface public void stopMonitor(){runOnUiThread(()->{Intent i=new Intent(MainActivity.this,ScannerService.class);i.setAction("STOP");startService(i);});}
  @JavascriptInterface public String monitorStatus(){android.content.SharedPreferences p=getSharedPreferences(ScannerService.PREF,0);JSONObject j=new JSONObject();try{j.put("running",p.getBoolean("running",false));j.put("lastCheck",p.getLong("lastCheck",0));j.put("sourceItems",p.getInt("sourceItems",0));j.put("error",p.getString("lastError",""));}catch(Exception ignored){}return j.toString();}

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