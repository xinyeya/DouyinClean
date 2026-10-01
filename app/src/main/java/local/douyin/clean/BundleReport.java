package local.douyin.clean;

import android.content.Context;
import android.os.Bundle;

/** Bounded diagnostics contain counters/codes, never a content URL, file path or message. */
public final class BundleReport {
    public static void download(Context c,int saved,int total,String code,String signature,String directory){
        Bundle b=new Bundle();b.putString("download","已保存 "+saved+"/"+total+(saved==total?"":"；失败码 "+code));
        b.putString("signature",signature);b.putString("directory",directory);
        c.getContentResolver().call(Config.URI,"diagnostic",null,b);
    }
    private BundleReport(){}
}
