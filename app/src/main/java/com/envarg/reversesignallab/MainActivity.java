package com.envarg.reversesignallab;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.net.Uri;
public class MainActivity extends Activity {
 WebView web; ValueCallback<Uri[]> pending; final int PICK=100;
 @Override public void onCreate(Bundle b){super.onCreate(b);
 web=new WebView(this); web.getSettings().setJavaScriptEnabled(true);
 web.getSettings().setAllowFileAccess(true);
 web.getSettings().setAllowFileAccessFromFileURLs(true);
 web.setWebChromeClient(new WebChromeClient(){
 @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p){
 if(pending!=null)pending.onReceiveValue(null); pending=cb;
 Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
 startActivityForResult(Intent.createChooser(i,"Select CSV"),PICK);return true;}});
 setContentView(web);web.loadUrl("file:///android_asset/index.html");}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);
 if(r==PICK&&pending!=null){pending.onReceiveValue(c==RESULT_OK&&d!=null?new Uri[]{d.getData()}:null);pending=null;}}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}