package local.douyin.clean;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import de.robv.android.xposed.XposedHelpers;

/** Uses the host's public encoded-image pipeline for the one image selected by the user. */
public final class HostImages {
    private static ClassLoader loader;
    public static void init(ClassLoader cl){loader=cl;}
    public static boolean fetch(Context context,String url,File file,long limit){
        if(loader==null||!ImageRules.allowed(url))return false;
        Object source=null,ref=null;
        try{
            Class<?> fresco=XposedHelpers.findClass("com.facebook.drawee.backends.pipeline.Fresco",loader);
            Class<?> requests=XposedHelpers.findClass("com.facebook.imagepipeline.request.ImageRequest",loader);
            Object request=XposedHelpers.callStaticMethod(requests,"fromUri",url);
            Object pipeline=XposedHelpers.callStaticMethod(fresco,"getImagePipeline");
            source=XposedHelpers.callMethod(pipeline,"fetchEncodedImage",request,context);
            long deadline=SystemClock.uptimeMillis()+20000;
            while(!Boolean.TRUE.equals(XposedHelpers.callMethod(source,"isFinished"))){
                if(SystemClock.uptimeMillis()>=deadline)return false;Thread.sleep(25);
            }
            ref=XposedHelpers.callMethod(source,"getResult");if(ref==null)return false;
            Object buffer=XposedHelpers.callMethod(ref,"get");int size=(Integer)XposedHelpers.callMethod(buffer,"size");
            if(size<=0||size>limit)return false;
            byte[] bytes=new byte[Math.min(32768,size)];
            try(OutputStream out=new FileOutputStream(file)){
                for(int offset=0;offset<size;){int wanted=Math.min(bytes.length,size-offset);
                    int n=(Integer)XposedHelpers.callMethod(buffer,"read",offset,bytes,0,wanted);
                    if(n<=0||n>wanted)throw new IOException();out.write(bytes,0,n);offset+=n;
                }
            }
            return true;
        }catch(Throwable ignored){return false;}
        finally{
            if(ref!=null)try{XposedHelpers.callMethod(ref,"close");}catch(Throwable ignored){}
            if(source!=null)try{XposedHelpers.callMethod(source,"close");}catch(Throwable ignored){}
        }
    }
    private HostImages(){}
}
