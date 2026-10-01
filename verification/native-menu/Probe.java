package local.douyin.clean.probe;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.view.*;
import android.widget.*;
import local.douyin.clean.NativeMenus;
import local.douyin.clean.ShareRows;
import local.douyin.clean.LocalDownloads;
import android.provider.MediaStore;
import android.net.Uri;
import android.database.Cursor;
import android.util.Base64;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class Probe extends Instrumentation {
 private int checks=0; private Throwable error;
 private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){
  runOnMainSync(()->{try{test();}catch(Throwable e){error=e;}});
  if(error==null)try{downloads();}catch(Throwable e){error=e;}
  Bundle out=new Bundle();out.putString("result",error==null?"PASS: "+checks+" native menu behavior checks":error.toString());
  finish(error==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
 }
 private void downloads() throws Exception{
  Context c=getTargetContext();File fixture=new File(c.getCacheDir(),"probe_image.gif");
  byte[] bytes=Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",Base64.DEFAULT);
  Method write=LocalDownloads.class.getDeclaredMethod("write",Context.class,LocalDownloads.Item.class,int.class);write.setAccessible(true);
  try{
   try(OutputStream out=new FileOutputStream(fixture)){out.write(bytes);}
   String directory=(String)write.invoke(null,c,new LocalDownloads.Item(null,fixture.getAbsolutePath(),false),9101);
   check(directory.equals(android.os.Environment.getExternalStorageDirectory().getAbsolutePath()+"/Download/净音表情"),"success returns actual absolute directory");
   write.invoke(null,c,new LocalDownloads.Item(null,fixture.toURI().toString(),false),9102);
   List<Uri> outputs=new ArrayList<>();
   try(Cursor cursor=c.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name","mime_type","relative_path","is_pending","_size"},"owner_package_name=?",new String[]{c.getPackageName()},null)){
    check(cursor!=null,"MediaStore query own outputs");
    while(cursor.moveToNext()){
     if(!cursor.getString(1).matches("clean_image_.*_910[12]\\.gif"))continue;
     Uri uri=android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0));outputs.add(uri);
     check("image/gif".equals(cursor.getString(2)),"native original GIF MIME");
     check(cursor.getString(3).equals("Download/净音表情/"),"default directory");
     check(cursor.getInt(4)==0&&cursor.getLong(5)==bytes.length,"finished file exact size");
     try(InputStream in=c.getContentResolver().openInputStream(uri)){
      byte[] actual=new byte[bytes.length];int n=in.read(actual);check(n==bytes.length&&Arrays.equals(actual,bytes),"original GIF bytes preserved");
     }
    }
   }finally{
    try(Cursor cursor=c.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name"},"owner_package_name=?",new String[]{c.getPackageName()},null)){
     if(cursor!=null)while(cursor.moveToNext())if(cursor.getString(1).matches("clean_image_.*_910[12]\\.gif"))c.getContentResolver().delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0)),null,null);
    }
   }
   check(outputs.size()==2,"plain and file URI source exports");
   try{write.invoke(null,c,new LocalDownloads.Item(null,"/nonexistent-probe.gif",false),9103);throw new AssertionError("invalid source accepted");}
   catch(InvocationTargetException expected){check("D01".equals(expected.getCause().getMessage()),"missing source has error code");}
  }finally{fixture.delete();}
 }
 private void test(){
  Context c=getTargetContext();final int[] clicks={0,0,0};
  FrameLayout popup=new FrameLayout(c);TextView remove=new TextView(c);remove.setText("移除表情");
  FrameLayout.LayoutParams original=new FrameLayout.LayoutParams(-1,80);original.topMargin=1;
  popup.addView(remove,original);remove.setOnClickListener(v->clicks[0]++);
  check(NativeMenus.popup(remove,v->clicks[1]++),"favorite injection");
  check(popup.getChildCount()==1&&remove.getParent() instanceof LinearLayout,"one native row");
  LinearLayout row=(LinearLayout)remove.getParent();check(row.getChildCount()==2,"remove plus download");
  remove.performClick();row.getChildAt(1).performClick();check(clicks[0]==1&&clicks[1]==1,"original and new listeners");
  NativeMenus.removePopup(remove);check(remove.getParent()==popup&&popup.getChildCount()==1,"favorite cleanup");
  check(remove.getLayoutParams()==original&&original.topMargin==1,"favorite layout restoration");
  NativeMenus.popup(remove,v->clicks[2]++);((LinearLayout)remove.getParent()).getChildAt(1).performClick();
  check(clicks[2]==1&&clicks[1]==1,"favorite next item callback");NativeMenus.removePopup(remove);
  LinearLayout comment=new LinearLayout(c);comment.setOrientation(LinearLayout.HORIZONTAL);
  TextView share=new TextView(c),add=new TextView(c);add.setText("添加表情");
  comment.addView(share,new LinearLayout.LayoutParams(0,88,1));LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(0,88,1);addLp.leftMargin=12;comment.addView(add,addLp);
  check(NativeMenus.beside(add,"detail-download","下载表情",v->clicks[1]++),"comment injection");
  check(comment.getChildCount()==3&&comment.indexOfChild(add)==1,"download beside add");
  NativeMenus.beside(add,"detail-download","下载表情",v->clicks[2]++);check(comment.getChildCount()==3,"comment duplicate avoided");
  comment.getChildAt(2).performClick();check(clicks[2]==2,"rebind callback refreshed");
  NativeMenus.remove(comment,"detail-download");check(comment.getChildCount()==2&&((LinearLayout.LayoutParams)add.getLayoutParams()).leftMargin==12,"comment native params restored");
  FrameLayout panel=new FrameLayout(c);View recycler=new View(c);panel.addView(recycler,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout actions=ShareRows.create(c);ShareRows.action(actions,"保存视频",v->clicks[1]++);ShareRows.action(actions,"保存图片",v->clicks[2]++);
  check(ShareRows.after(recycler,actions),"share footer insertion");LinearLayout wrapper=(LinearLayout)recycler.getParent();
  check(wrapper.getChildCount()==2&&wrapper.getChildAt(1)==actions,"footer below original actions");
  check(((LinearLayout.LayoutParams)recycler.getLayoutParams()).weight==1,"fill area gives footer space");
  LinearLayout replacement=ShareRows.create(c);ShareRows.action(replacement,"保存视频",v->clicks[0]++);
  check(ShareRows.after(recycler,replacement)&&wrapper.getChildCount()==2&&wrapper.getChildAt(1)==replacement,"share update no duplicate");
  ShareRows.remove(panel);check(wrapper.getChildCount()==1&&wrapper.getChildAt(0)==recycler,"disable preserves host content");
  check(ShareRows.after(recycler,ShareRows.create(c))&&wrapper.getChildCount()==2,"re-enable reuses wrapper");
  LinearLayout nativePanel=new LinearLayout(c);nativePanel.setOrientation(LinearLayout.VERTICAL);View host=new View(c);TextView cancel=new TextView(c);cancel.setText("取消");nativePanel.addView(host);nativePanel.addView(cancel);
  LinearLayout footer=ShareRows.create(c);check(ShareRows.common(nativePanel,footer)&&nativePanel.indexOfChild(footer)==1&&nativePanel.indexOfChild(cancel)==2,"footer before cancel");
  check(ShareRows.common(nativePanel,ShareRows.create(c))&&nativePanel.getChildCount()==3,"common re-open no duplicate");
  LinearLayout functionParent=new LinearLayout(c);functionParent.setOrientation(LinearLayout.VERTICAL);View function=new View(c);functionParent.addView(function);
  LinearLayout extra=ShareRows.create(c);check(ShareRows.nativeFunctions(function,extra)&&functionParent.getChildCount()==2,"real share function container footer");
  check(ShareRows.nativeFunctions(function,ShareRows.create(c))&&functionParent.getChildCount()==2,"function refresh footer no duplicate");
  functionParent.setOrientation(LinearLayout.HORIZONTAL);ShareRows.remove(functionParent);check(!ShareRows.nativeFunctions(function,ShareRows.create(c)),"wrong function orientation preserved");
  FrameLayout unsupported=new FrameLayout(c);unsupported.addView(new View(c));unsupported.addView(new View(c));
  check(!ShareRows.common(unsupported,ShareRows.create(c))&&unsupported.getChildCount()==2,"unmatched layout preserves host");
 }
}
