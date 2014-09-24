package com.yujin.update;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.Attributes;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import viin.patch.update.PatchUpdate;
import android.app.DownloadManager;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
/**
 * 原理：
 * 1,从后台服务器获取最新版本信息
 * 2,把最新版本和自己本地版本进行比较,如果最新版本比自己本地版本要新,则进行更新,否则不更新
 * 3,如果更新的,先把后台服务器存放的apk文件下载到手机内存里面.
 * 4,把下载到手机里面最新版本的apk进行安装,替代旧的版本
 * @author yujin
 *
 */
public class MainActivity extends ListActivity {
    public static final String TAG = "MainActivity";
    public static final String apkUrl = "http://img2.paimao.com/dl/baoweiluobo2_0916.apk";
    public DownloadManager mDownloadManager = null;
    private BroadcastReceiver receiver;
    private DownloadChangeObserver downloadObserver;
    private long downloadId;
    private String path;
    private static boolean isCancle = false;
    Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            
        };
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] strs = {"DownloadManager升级","HttpURLConnection升级","patch升级"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 
                android.R.layout.simple_expandable_list_item_1, strs);
        setListAdapter(adapter);
        path = getSDPath();
    }
    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);  
        filter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        receiver = new BroadcastReceiver() {
          @Override  
          public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "action: " + action);
            long reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);  
            Log.i(TAG, "reference: " + reference);
          }
        };
        registerReceiver(receiver, filter);
    }
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // TODO Auto-generated method stub
        //super.onListItemClick(l, v, position, id);
        Log.i(TAG, "position: " + position + " id: " +id);
        switch((int)id) {
        case 0:
            downloadFileDownloadManager(apkUrl);
            break;
        case 1:
            downFileHttp(apkUrl);
            break;
        case 2:
            downloadFilePatch();
            break;
            default:
                Log.i(TAG, "Invalid click");
        }
    }
    
    public void downFileHttp(final String url) {
        //pBar.show();
        new Thread() {
            public void run() {
                HttpClient client = new DefaultHttpClient();
                HttpGet get = new HttpGet(url);
                HttpResponse response;
                try {
                    response = client.execute(get);
                    HttpEntity entity = response.getEntity();
                    long length = entity.getContentLength();
                    InputStream is = entity.getContent();
                    FileOutputStream fileOutputStream = null;
                    if (is != null) {
                        File file = new File(Environment.getExternalStorageDirectory(), "aaa.apk");
                        fileOutputStream = new FileOutputStream(file);
                        byte[] buf = new byte[1024];
                        int ch = -1;
                        int count = 0;
                        while ((ch = is.read(buf)) != -1) {
                            fileOutputStream.write(buf, 0, ch);
                            count += ch;
                            if (length > 0) {
                            }
                        }
                    }
                    fileOutputStream.flush();
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
    
    public void downloadFileDownloadManager(final String url) {
        // TODO Auto-generated method stub
        mDownloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        downloadId = mDownloadManager.enqueue(request);
        request.setDestinationUri(Uri.parse(path));
        mDownloadManager.enqueue(request);
    }
    
    public void downloadFilePatch() {
        File file = new File(path);
        if(file.exists()){
            if(!file.isDirectory()){
                file.delete();
                file.mkdir();
            }
        }else {
            file.mkdir();
        }
        String patch = "app.patch";
        String oldapk = "old.apk";
        
        String oldapk_filepath = path + oldapk;
        String newapk_savepath = path + "new.apk";
        String patchpath = path + patch;
        /**
         * 由于assets里面放置的文件可以很大,但读取单个文件超过1M的时候,就会报Data exceeds UNCOMPRESS_DATA_MAX (1314625 vs 1048576)错误.
         * 在此,我把app.patch分包为每个大小为1M的文件,app.patch分包为3个文件,如app1.patch,app2.patch....
         * old.apk没有超过1M,暂时没有分包
         * 分包代码在本页未尾由注释代码给出
         */
        AssetManager am = getAssets();
        //先把assets里面patch文件写入sd的1目录下面
        try {
            //String[]    patches    =    new    String[]{"app1.patch","app2.patch","app3.patch"};  
             String[] patches = new String[]{"app1.patch"}; 
            InputStream is = null;
            byte[] buffer = new byte[1024];
            int tmp = -1;
            OutputStream outputStream = new FileOutputStream(new File(patchpath));
            for (int i = 0; i < patches.length; i++) {
                is = am.open(patches[i]);
                while((tmp = is.read(buffer)) != -1){
                    outputStream.write(buffer, 0, tmp);    
                }
                outputStream.flush();
                is.close();
            }
            outputStream.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }  
        //先把assets里面old apk文件写入sd的1目录下面
        try {
            InputStream is = am.open(oldapk);
            byte[] buffer = new    byte[1024 * 2];
            int    tmp = -1;
            OutputStream outputStream = new FileOutputStream(new File(oldapk_filepath));
            while((tmp = is.read(buffer)) != -1){
                outputStream.write(buffer, 0, tmp);
            }
            outputStream.flush();
            is.close();
            outputStream.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        //把旧的apk和补丁合在一起,就结合成最新版本的apk
        PatchUpdate    patchInterence    =    new    PatchUpdate();
        patchInterence.patch(oldapk_filepath, newapk_savepath, patchpath);
        Log.i(TAG, "-------------------------------------------:"+path);
        Toast.makeText(MainActivity.this, "新的apk已经写入到"    +    path    +    "目录下面", Toast.LENGTH_LONG).show();
    }
    
    private String getSDPath() {  
        File sdDir = null;  
        boolean sdCardExist = Environment.getExternalStorageState().equals(  
                android.os.Environment.MEDIA_MOUNTED); // determine whether sd card is exist  
        if (sdCardExist) {  
            sdDir = Environment.getExternalStorageDirectory();// get the root directory  
        }  
        return sdDir.toString();  
    }  
    private boolean checkNewVersion() {  
        try {  
            URL url=new URL(apkUrl);  
            SAXParserFactory factory=SAXParserFactory.newInstance();  
            factory.setNamespaceAware(true);  
            factory.setValidating(false);  
            SAXParser parser=factory.newSAXParser();  
            InputStream is = url.openStream();  
            parser.parse(is, new DefaultHandler(){  
                private String cur="";  
                private int step;  
                  
                @Override  
                public void startDocument() throws SAXException {  
                    step = 0;  
                }  
                  
                public void startElement(String uri, String localName,  
                        String qName, Attributes attributes)  
                        throws SAXException {  
                    cur = localName;  
                }  
                  
                @Override  
                public void characters(char[] ch, int start, int length)  
                        throws SAXException {  
                    String str = new String(ch, start, length).trim();  
                    if (str == null || str.equals(""))  
                        return;  
                    if (cur.equals("url")) {  
                        //apkUrl = str;  
                    }  
                    if (cur.equals("map_version")) {  
                        //mapVersion = str;  
                    }  
                }  
                  
                @Override  
                public void endElement(String uri, String localName,  
                        String qName) throws SAXException {  
                    step = step + 1;  
                }  
                  
                @Override  
                public void endDocument() throws SAXException {  
                    super.endDocument();  
                }  
            });  
        } catch (MalformedURLException e) {  
            e.printStackTrace();  
        } catch (ParserConfigurationException e) {  
            e.printStackTrace();  
        } catch (SAXException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
        //if (diffVersion(mapVersion))
        if (true)
            return true;  
        else  
            return false;  
    }  

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
    }
    class DownloadChangeObserver extends ContentObserver {
        
        public DownloadChangeObserver(){
            super(mHandler);
        }
      
        @Override
        public void onChange(boolean selfChange) {
            Log.i(TAG, "<---onChange--->");
        }
    }
}
