package local.douyin.clean;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.database.Cursor;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs inside Douyin. No Activity, service, cookies or module process are needed for export. */
public final class LocalDownloads {
    public static final class Item {
        final List<String> urls;final String localPath;final boolean video;
        public Item(String url,String localPath,boolean video){this(url==null?Collections.emptyList():Collections.singletonList(url),localPath,video,0);}
        private Item(List<String> urls,String localPath,boolean video,int unused){this.urls=new ArrayList<>(urls);this.localPath=localPath;this.video=video;}
        public static Item urls(List<String> urls,String localPath,boolean video){return new Item(urls,localPath,video,0);}
    }
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    public static void tip(Context c,String message){Context app=c.getApplicationContext();MAIN.post(()->Toast.makeText(app,message,Toast.LENGTH_SHORT).show());}
    public static void save(Context c,List<Item> source){
        Context app=c.getApplicationContext();List<Item> items=new ArrayList<>(source);
        if(items.isEmpty()){tip(app,"当前内容没有可用的下载地址");return;}
        if(items.size()>100){tip(app,"本次图片数量超过支持范围");return;}
        if(!BUSY.compareAndSet(false,true)){tip(app,"正在保存，请稍等");return;}
        tip(app,"开始保存…");
        WORK.execute(()->{
            int saved=0;String failure="D00",signature="",directory="";
            try {
                for(int i=0;i<items.size();i++){
                    try{directory=write(app,items.get(i),i);saved++;}
                    catch(Failure e){failure=e.code;signature=e.signature;}
                    catch(Exception e){failure="D00";} // Never log signed URLs or paths.
                }
                if(saved==items.size())tip(app,(items.size()==1?"已保存至":"已保存 "+saved+" 张图片至")+"\n"+directory);
                else tip(app,saved==0?"保存失败（"+failure+"）："+reason(failure):"已保存 "+saved+"/"+items.size()+" 张至\n"+directory);
                try{BundleReport.download(app,saved,items.size(),failure,signature,directory);}catch(Exception ignored){}
            } finally { BUSY.set(false); }
        });
    }
    private static final class Failure extends IOException {
        final String code,signature;Failure(String c){this(c,"");}Failure(String c,String s){super(c);code=c;signature=s;}
    }
    private static String reason(String code){
        if(code.startsWith("H"))return "服务器返回 "+code.substring(1);
        if(code.equals("D01"))return "没有可用原文件";
        if(code.equals("D02"))return "网络连接失败";
        if(code.equals("D03"))return "文件格式暂不支持";
        if(code.equals("D04"))return "文件超过大小限制";
        if(code.equals("D05"))return "创建下载文件失败";
        if(code.equals("D06"))return "写入下载目录失败";
        if(code.equals("D07"))return "确认保存结果失败";
        if(code.equals("D08"))return "缓存目录不可用";
        return "请重新打开内容后再试";
    }
    private static String format(File file,boolean video) throws IOException{
        byte[] head=new byte[32];int len;try(InputStream in=new FileInputStream(file)){len=in.read(head);}
        if(len<0)return null;head=Arrays.copyOf(head,len);return video?ImageRules.videoFormat(head):ImageRules.format(head);
    }
    private static String obtain(Context c,Item item,File temp,long limit) throws Exception{
        String failure="D01",signature="";
        if(item.localPath!=null&&!item.localPath.isEmpty()&&!item.video){
            try{
                Uri uri=Uri.parse(item.localPath);InputStream input;
                if("content".equals(uri.getScheme()))input=c.getContentResolver().openInputStream(uri);
                else input=new FileInputStream("file".equals(uri.getScheme())?uri.getPath():item.localPath);
                if(input==null)throw new IOException();
                try(InputStream in=input;OutputStream out=new FileOutputStream(temp)){copy(in,out,limit);}
                String ext=format(temp,false);if(ext!=null)return ext;failure="D03";signature=signature(temp);
            }catch(Exception ignored){} // An invalid cached path must not suppress the remote alternatives.
        }
        for(String url:item.urls){
            if(!ImageRules.allowed(url))continue;
            if(!item.video&&HostImages.fetch(c,url,temp,limit)){
                String ext=format(temp,false);if(ext!=null)return ext;failure="D03";signature=signature(temp);
            }
            try{fetch(url,temp,limit);String ext=format(temp,item.video);if(ext!=null)return ext;failure="D03";signature=signature(temp);}
            catch(Failure e){failure=e.code;}catch(Exception e){failure="D02";}
        }
        throw new Failure(failure,"D03".equals(failure)?signature:"");
    }
    private static String signature(File file){
        try(InputStream in=new FileInputStream(file)){byte[] head=new byte[16];int n=in.read(head);StringBuilder out=new StringBuilder();
            for(int i=0;i<n;i++)out.append(String.format(java.util.Locale.ROOT,"%02X",head[i]&255));return out.toString();
        }catch(Exception ignored){return "";}
    }
    private static String write(Context c,Item item,int index) throws Exception {
        File temp;try{temp=File.createTempFile("clean_export_",".part",c.getCacheDir());}catch(Exception e){throw new Failure("D08");}
        Uri output=null;
        try {
            long limit=item.video?512L*1024*1024:20L*1024*1024;
            String ext=obtain(c,item,temp,limit);
            ContentValues v=new ContentValues();
            String filename="clean_"+(item.video?"video":"image")+"_"+System.currentTimeMillis()+"_"+index+"."+ext;
            v.put(MediaStore.Downloads.DISPLAY_NAME,filename);
            v.put(MediaStore.Downloads.MIME_TYPE,(item.video?"video/":"image/")+(ext.equals("jpg")?"jpeg":ext));
            v.put(MediaStore.Downloads.RELATIVE_PATH,Config.DOWNLOAD_DIRECTORY);
            v.put(MediaStore.Downloads.IS_PENDING,1);
            try{output=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(output==null)throw new IOException();}
            catch(Exception e){throw new Failure("D05");}
            try(InputStream in=new FileInputStream(temp);OutputStream out=c.getContentResolver().openOutputStream(output)){
                if(out==null)throw new IOException();copy(in,out,limit);
            }catch(Exception e){throw new Failure("D06");}
            ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);
            try{if(c.getContentResolver().update(output,done,null,null)!=1)throw new IOException();}
            catch(Exception e){throw new Failure("D07");}
            String directory=new File(Environment.getExternalStorageDirectory(),Config.DOWNLOAD_DIRECTORY).getAbsolutePath();
            try(Cursor result=c.getContentResolver().query(output,new String[]{MediaStore.MediaColumns.DATA},null,null,null)){
                if(result!=null&&result.moveToFirst()){String path=result.getString(0);if(path!=null&&!path.isEmpty())directory=new File(path).getParent();}
            }catch(Exception ignored){}
            output=null;return directory;
        }finally{
            if(output!=null)try{c.getContentResolver().delete(output,null,null);}catch(Exception ignored){}
            if(!temp.delete())temp.deleteOnExit();
        }
    }
    private static void copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] data=new byte[32*1024];long total=0;int n;
        while((n=in.read(data))!=-1){total+=n;if(total>limit)throw new Failure("D04");out.write(data,0,n);}
    }
    private static void fetch(String url,File target,long limit) throws Exception {
        for(int redirects=0;redirects<5;redirects++){
            if(!ImageRules.allowed(url))throw new IOException("Unsupported source");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Accept","*/*");
            String agent=System.getProperty("http.agent");if(agent!=null)c.setRequestProperty("User-Agent",agent);
            c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
            try {
                int status=c.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){String next=c.getHeaderField("Location");if(next==null)throw new IOException();url=new URL(new URL(url),next).toString();continue;}
                if(status!=200)throw new Failure("H"+status);
                if(c.getContentLengthLong()>limit)throw new Failure("D04");
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(target)){copy(in,out,limit);}return;
            } finally { c.disconnect(); }
        }
        throw new IOException("Redirect limit");
    }
    private LocalDownloads(){}
}
