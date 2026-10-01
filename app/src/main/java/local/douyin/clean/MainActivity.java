package local.douyin.clean;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import android.view.View;
import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity {
    private LinearLayout body;
    private final int ink = Color.rgb(232, 239, 246), muted = Color.rgb(148, 165, 184);
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    @Override public void onResume() { super.onResume(); render(); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private GradientDrawable bg(int color) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(18)); return d;
    }
    private void section(String title) {
        TextView t = text(title, 14, Color.rgb(119,224,202)); t.setPadding(dp(4), dp(24), 0, dp(12)); body.addView(t);
    }
    private void row(String key, String title, String note) {
        LinearLayout card = new LinearLayout(this); card.setPadding(dp(16),dp(16),dp(12),dp(16)); card.setGravity(16);
        card.setBackground(bg(Color.rgb(23,34,49)));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(1);
        labels.addView(text(title,16,ink)); TextView sub = text(note,12,muted); sub.setPadding(0,dp(5),dp(12),0); labels.addView(sub);
        card.addView(labels, new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle = new Switch(this); toggle.setContentDescription(title); toggle.setChecked(Config.prefs(this).getBoolean(key,false));
        toggle.setOnCheckedChangeListener((v,checked) -> {Config.prefs(this).edit().putBoolean(key,checked).apply();Config.changed(this);});
        card.addView(toggle); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(8); body.addView(card,lp);
    }
    private void render() {
        getWindow().setStatusBarColor(Color.rgb(13,22,34)); getWindow().setNavigationBarColor(Color.rgb(13,22,34));
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(13,22,34));
        body = new LinearLayout(this); body.setOrientation(1); body.setPadding(dp(20),dp(20),dp(20),dp(32)); scroll.addView(body); setContentView(scroll);
        TextView title = text("净音",32,ink); title.setTypeface(null,Typeface.BOLD); body.addView(title);
        body.addView(text("让首页安静一点 · 0.3.2",14,muted));
        String target = "未安装抖音极速版";
        try { PackageInfo p=getPackageManager().getPackageInfo(Config.TARGET,0); target="抖音极速版 "+p.versionName+" · "+(p.getLongVersionCode()==Config.VERSION?"版本匹配":"未适配，Hook 停用"); } catch(Exception ignored) {}
        TextView info=text(target+"\n适配 40.5.0 / 400501 · Android 10+",13,ink); info.setPadding(0,dp(20),0,dp(10)); body.addView(info);
        row("enabled","模块总开关","启用一次即可随抖音运行；配置更新会通知抖音，无需每次打开净音。");
        section("表情保存");
        row("save_favorite","收藏表情保存","原长按菜单并列显示“移除表情”和“下载表情”。");
        row("save_chat","聊天表情保存","聊天表情进入预览后使用保存入口；只保存你主动选择的图片。");
        row("save_comment","评论表情保存","“添加表情”旁添加同样式的绿色“下载表情”。");
        section("分享面板保存");
        row("save_video","保存视频","分享面板底部新增保存行，直接保存可用的原下载地址。");
        row("save_post_images","保存图片","分享面板新增保存图片，按顺序保存当前图文作品的图片。");
        body.addView(text("保存目录："+Config.DOWNLOAD_DIRECTORY+"\n点击直接下载，仅显示轻提示，保留原图片/动图格式。",12,muted));
        section("首页与抽屉");
        row("hide_mini","隐藏常用小程序","收起左侧抽屉中的常用小程序卡片。");
        row("hide_ads","过滤推荐广告","根据推荐列表的广告标记过滤；不保证涵盖所有广告形态。");
        row("hide_live_feed","过滤推荐直播","从列表中移除标记为直播的推荐卡片。");
        row("hide_live_entry","移除直播顶栏与页面","从首页顶栏和横向分页列表移除直播频道；重开抖音后生效。抽屉直播广场也隐藏。");
        row("hide_hot_feed","过滤热点推荐卡片","过滤有明确热点卡片类型的条目，不按视频正文关键词删除。");
        row("hide_hot_entry","移除热点顶栏与页面","移除热点/热榜频道及分页，保留推荐页面；重开抖音后生效。相关搜索也隐藏。");
        row("hide_shop","移除商城与团购页面","从顶栏与分页列表一起移除对应频道；重开抖音后生效。");
        section("实验功能");
        row("anti_recall","已接收消息防撤回 · 实验","仅尝试显示仍有本地内容的他人消息；不恢复未收到或已删除的内容，可能影响本地消息操作。");
        section("运行状态");
        String report=getSharedPreferences("status",0).getString("status","尚未收到 Hook 报告。先在 LSPosed 启用模块，作用域只选抖音极速版，再重开抖音。");
        long time=getSharedPreferences("status",0).getLong("time",0);
        body.addView(text(report+(time>0?"\n最近报告："+DateFormat.getDateTimeInstance().format(new Date(time)):""),13,muted));
        body.addView(text("最近保存结果："+getSharedPreferences("status",0).getString("download","暂无"),13,muted));
        body.addView(text("实际保存路径："+getSharedPreferences("status",0).getString("directory","成功保存后显示"),13,muted));
        String signature=getSharedPreferences("status",0).getString("signature","");
        if(!signature.isEmpty())body.addView(text("未识别格式文件头："+signature,12,muted));
        body.addView(text("LSPosed 随抖音启动加载模块，净音只是设置页，不需要常驻前台。\n报告仅证明 Hook 装载，实际菜单仍需实测。配置只存在本机。",12,muted));
        Button reset=new Button(this); reset.setText("关闭所有功能"); reset.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("关闭所有功能，并在重开抖音后恢复已隐藏的入口。已下载文件不会删除。").setNegativeButton("取消",null).setPositiveButton("关闭",(d,w)->{Config.prefs(this).edit().clear().apply();Config.changed(this);render();}).show()); body.addView(reset);
    }
}
