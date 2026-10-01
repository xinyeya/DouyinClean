package local.douyin.clean;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.net.Uri;

public final class Config {
    public static final String APP = "local.douyin.clean";
    public static final String TARGET = "com.ss.android.ugc.aweme.lite";
    public static final long VERSION = 400501L;
    public static final Uri URI = Uri.parse("content://" + APP + ".config");
    public static final String[] KEYS = {"enabled", "save_favorite", "save_chat", "save_comment", "save_video", "save_post_images", "hide_mini", "hide_ads", "hide_live_feed", "hide_live_entry", "hide_hot_feed", "hide_hot_entry", "hide_shop", "anti_recall"};
    public static final String DOWNLOAD_DIRECTORY = "Download/净音表情";
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("switches", Context.MODE_PRIVATE); }
    public static Bundle read(Context c) {
        Bundle b = new Bundle();
        for (String k : KEYS) b.putBoolean(k, prefs(c).getBoolean(k, false));
        return b;
    }
    public static boolean on(Bundle b, String k) { return b != null && b.getBoolean("enabled", false) && b.getBoolean(k, false); }
    public static void changed(Context c) { c.getContentResolver().notifyChange(URI, null); }
    private Config() {}
}
