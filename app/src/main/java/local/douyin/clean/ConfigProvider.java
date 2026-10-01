package local.douyin.clean;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Only own UID and the installed target UID can read feature booleans. No chat data. */
public final class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private void checkCaller() {
        int caller = Binder.getCallingUid();
        if (caller == Process.myUid()) return;
        try {
            if (caller == getContext().getPackageManager().getApplicationInfo(Config.TARGET, 0).uid) return;
        } catch (Exception ignored) { }
        throw new SecurityException("Caller not allowed");
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        checkCaller();
        if ("config".equals(method)) return Config.read(getContext());
        if ("diagnostic".equals(method) && extras != null) {
            String value=extras.getString("download", "");
            if(value.length()<=120&&value.matches("[已保存失败码； /0-9DH]+"))getContext().getSharedPreferences("status",0).edit().putString("download",value).apply();
            String signature=extras.getString("signature", ""),directory=extras.getString("directory", "");
            if(signature.matches("[0-9A-F]{0,32}"))getContext().getSharedPreferences("status",0).edit().putString("signature",signature).apply();
            if(directory.length()<=200&&directory.startsWith("/")&&directory.endsWith(Config.DOWNLOAD_DIRECTORY))getContext().getSharedPreferences("status",0).edit().putString("directory",directory).apply();
            return Bundle.EMPTY;
        }
        if ("report".equals(method) && extras != null) {
            // Save only a bounded capability status, never URLs or message content.
            String value = extras.getString("status", "");
            if (value.length() <= 1600) getContext().getSharedPreferences("status", 0).edit()
                .putString("status", value).putLong("time", System.currentTimeMillis()).apply();
            return Bundle.EMPTY;
        }
        throw new IllegalArgumentException("Unknown operation");
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { checkCaller(); return null; }
    @Override public String getType(Uri u) { checkCaller(); return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
