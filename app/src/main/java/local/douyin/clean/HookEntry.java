package local.douyin.clean;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Version-specific, local presentation hooks. No anti-detection hooks. */
public final class HookEntry implements IXposedHookLoadPackage {
    private volatile Bundle options=Bundle.EMPTY;
    private Context context;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean refreshing=new AtomicBoolean();
    private final Map<String,Integer> errors=new ConcurrentHashMap<>();
    private final Set<String> disabled=Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final List<String> report=new CopyOnWriteArrayList<>();
    private final Map<Activity,ViewTreeObserver.OnGlobalLayoutListener> listeners=new WeakHashMap<>();
    private boolean loaded;
    private boolean on(String key){return !disabled.contains(key)&&Config.on(options,key);}
    private void fail(String key) {
        Integer count=errors.get(key);int n=count==null?1:count+1;errors.put(key,n);
        if(n>=3&&disabled.add(key)){report.add(key+"：异常累计，已在本进程停用");publish();}
    }
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if(!Config.TARGET.equals(p.packageName)||!Config.TARGET.equals(p.processName))return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if(loaded)return;loaded=true;context=((Context)param.args[0]).getApplicationContext();
                if(context==null)context=(Context)param.args[0];
                try {
                    if(context.getPackageManager().getPackageInfo(Config.TARGET,0).getLongVersionCode()!=Config.VERSION){report.add("版本不匹配：全部功能停用");publish();return;}
                    report.add("进程装载 / 40.5.0；以下是装载状态，尚需逐项实测");
                    Bundle cached=new Bundle();SharedPreferences prefs=context.getSharedPreferences("local_clean_options",0);
                    for(String key:Config.KEYS)cached.putBoolean(key,prefs.getBoolean(key,false));options=cached;
                    try{Bundle current=context.getContentResolver().call(Config.URI,"config",null,null);if(current!=null)options=current;}catch(Exception ignored){}
                    HostImages.init(p.classLoader);
                    install(p.classLoader);refresh();
                    try{context.getContentResolver().registerContentObserver(Config.URI,true,new ContentObserver(new Handler(Looper.getMainLooper())){
                        @Override public void onChange(boolean selfChange){refresh();}
                    });}catch(Exception ignored){report.add("配置变化监听不可用：返回抖音时自动刷新");}
                } catch(Throwable e){report.add("初始化失败：保持原行为");publish();}
            }
        });
    }
    private void refresh(){
        if(!refreshing.compareAndSet(false,true))return;
        io.execute(()->{
            try{Bundle b=context.getContentResolver().call(Config.URI,"config",null,null);
                if(b!=null){options=b;SharedPreferences.Editor edit=context.getSharedPreferences("local_clean_options",0).edit();
                    for(String key:Config.KEYS)edit.putBoolean(key,b.getBoolean(key,false));edit.apply();}}
            catch(Exception e){if(!report.contains("配置暂时不可读：保留上次开关，下次自动重试"))report.add("配置暂时不可读：保留上次开关，下次自动重试");}
            finally{refreshing.set(false);}
            publish();
        });
    }
    private void publish(){
        if(context==null)return;
        io.execute(()->{try{Bundle b=new Bundle();b.putString("status",String.join("\n",report));context.getContentResolver().call(Config.URI,"report",null,b);}catch(Exception ignored){}});
    }
    private interface Install {void run() throws Throwable;}
    private void capability(String title,Install operation){
        try{operation.run();report.add(title+"：Hook 已装载");}
        catch(Throwable e){report.add(title+"：Hook 未装载（类或签名不可用）");}
    }
    private void install(ClassLoader cl){
        for(String name:new String[]{"com.ss.android.ugc.aweme.homepage.tab.data.HomeTabDataSourceDefault","X.0O2m"}){
            capability("顶栏页面数据 "+(name.startsWith("X.")?"服务端":"默认"),()->{
                Class<?> c=XposedHelpers.findClass(name,cl);
                XposedHelpers.findAndHookMethod(c,"getTopTabData",new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam p){filterTabs(p,false);}
                });
                XposedHelpers.findAndHookMethod(c,"LJIILLIIL",new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam p){filterTabs(p,true);}
                });
            });
        }
        capability("推荐容器内顶栏页面数据",()->XposedHelpers.findAndHookMethod("X.0O2x",cl,"LIZ",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){filterTabs(p,false);}
        }));
        capability("首页启动页回退",()->{
            Class<?> source=XposedHelpers.findClass("com.ss.android.ugc.aweme.homepage.tab.data.HomeTabDataSource",cl);
            XC_MethodHook landing=new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable()||!(p.getResult() instanceof String))return;
                String id=(String)p.getResult();
                if(TabRules.blocked(id,null,null,on("hide_live_entry"),on("hide_shop"),on("hide_hot_entry")))p.setResult("homepage_hot");
            }};
            XposedHelpers.findAndHookMethod(source,"LIZIZ",String.class,landing);
            XposedHelpers.findAndHookMethod(source,"LIZJ",String.class,boolean.class,landing);
        });
        capability("广告 / 直播 / 热点卡片",()->{
            Class<?> c=XposedHelpers.findClass("com.ss.android.ugc.aweme.feed.model.FeedItemList",cl);
            XC_MethodHook filter=new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable()||!(p.getResult() instanceof List))return;
                if(!on("hide_ads")&&!on("hide_live_feed")&&!on("hide_hot_feed"))return;
                try{List<?> list=(List<?>)p.getResult();List<Object> next=new ArrayList<>(list.size());
                    for(Object item:list)if(item==null||!blocked(item))next.add(item);
                    if(next.size()!=list.size())p.setResult(next);
                }catch(Throwable e){fail("hide_ads");fail("hide_live_feed");fail("hide_hot_feed");}
            }};
            for(String name:new String[]{"getItems","getItemsP","getItemsNotNull"})XposedHelpers.findAndHookMethod(c,name,filter);
            XC_MethodHook setter=new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){
                if(p.args[0]==null||(!on("hide_ads")&&!on("hide_live_feed")&&!on("hide_hot_feed")))return;
                try{List<?> list=(List<?>)p.args[0];List<Object> next=new ArrayList<>(list.size());for(Object item:list)if(item==null||!blocked(item))next.add(item);p.args[0]=next;}
                catch(Throwable e){fail("hide_ads");fail("hide_live_feed");fail("hide_hot_feed");}
            }};
            XposedHelpers.findAndHookMethod(c,"setItems",List.class,setter);
            XposedHelpers.findAndHookMethod(c,"setItemsP",List.class,setter);
        });
        capability("常用小程序",()->XposedHelpers.findAndHookMethod("com.ss.android.ugc.aweme.miniapp_impl.sidebar.recentlyapp.RecentlyUseAppsComponent",cl,"LJJIJIL",ViewGroup.class,Bundle.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){if(!on("hide_mini")||p.hasThrowable())return;try{if(p.getResult() instanceof View)hide((View)p.getResult());}catch(Throwable e){fail("hide_mini");}}
        }));
        capability("评论 / 聊天原生表情按钮",()->XposedHelpers.findAndHookMethod("X.0keQ",cl,"onBindBasicViewHolder","androidx.recyclerview.widget.RecyclerView$ViewHolder",int.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                try{View root=(View)XposedHelpers.getObjectField(p.args[0],"itemView");NativeMenus.remove(root,"detail-download");}catch(Throwable ignored){}
            }
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable()||(!on("save_comment")&&!on("save_chat")))return;
                if(!p.args[0].getClass().getName().equals("com.ss.android.ugc.aweme.emoji.similaremoji.EmojiSimilarHeadVh"))return;
                try{
                    Object params=XposedHelpers.getObjectField(p.thisObject,"d");
                    String scene=String.valueOf(XposedHelpers.getObjectField(params,"LJJIII"));
                    String key="COMMENT".equals(scene)?"save_comment":"IM".equals(scene)?"save_chat":null;
                    if(key==null||!on(key))return;
                    List<String> selected=imageSources(XposedHelpers.getObjectField(params,"LIZIZ"));
                    addSources(selected,XposedHelpers.getObjectField(params,"LJI"));
                    View root=(View)XposedHelpers.getObjectField(p.args[0],"itemView");View anchor=root.findViewById(0x7f0a4e4d);
                    if(anchor instanceof TextView)NativeMenus.beside((TextView)anchor,"detail-download","下载表情",v->{if(on(key))saveEmoji(v.getContext(),selected,null);});
                }catch(Throwable e){fail("save_comment");fail("save_chat");}
            }
        }));
        for(String name:new String[]{"PopHelper","BigEmojiPopHelper"}){
            capability("收藏原生菜单 "+name,()->{
                Class<?> c=XposedHelpers.findClass("com.ss.android.ugc.aweme.emoji.emoticonpanel.pop."+name,cl);
                String method=name.equals("PopHelper")?"LIZ":"LIZLLL";
                XposedHelpers.findAndHookMethod(c,method,"X.0LPP",new XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam p){
                        try{NativeMenus.removePopup((TextView)XposedHelpers.callMethod(p.thisObject,"LJIIIIZZ"));}catch(Throwable ignored){}
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p){
                        if(!on("save_favorite")||p.hasThrowable())return;
                        try{
                            Object base=XposedHelpers.getObjectField(p.args[0],"LIZIZ");
                            List<String> url=imageSources(XposedHelpers.callMethod(base,"getDetailEmoji"));
                            String local=(String)XposedHelpers.callMethod(base,"getLocalFilePath");
                            TextView anchor=(TextView)XposedHelpers.callMethod(p.thisObject,"LJIIIIZZ");
                            if(anchor.getVisibility()==View.VISIBLE)NativeMenus.popup(anchor,v->{if(on("save_favorite"))saveEmoji(v.getContext(),url,local);});
                        }catch(Throwable e){fail("save_favorite");}
                    }
                });
            });
        }
        capability("分享面板保存视频 / 图片",()->XposedHelpers.findAndHookMethod("com.ss.android.ugc.aweme.share.socialpanel.dialog.SocialActionsPanel",cl,"initView",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable()||(!on("save_video")&&!on("save_post_images")))return;
                try{
                    Object config=XposedHelpers.getObjectField(p.thisObject,"config");Object aweme=XposedHelpers.getObjectField(config,"LIZ");
                    Object content=XposedHelpers.getObjectField(p.thisObject,"mContentPanel");View recycler=(View)XposedHelpers.getObjectField(content,"f");
                    LinearLayout row=shareRow(recycler.getContext(),aweme);if(row!=null&&!ShareRows.after(recycler,row))shareUnsupported();
                }catch(Throwable e){fail("save_video");fail("save_post_images");}
            }
        }));
        capability("IM 分享面板保存行",()->{
            Class<?> dialog=XposedHelpers.findClass("com.ss.android.ugc.aweme.im.share.aweme.only.sharepanel.ui.SharePanelDialog",cl);
            XC_MethodHook add=new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())return;
                try{
                    View root=(View)XposedHelpers.callMethod(p.thisObject,"getDialogRootView");if(root==null)return;ShareRows.remove(root);
                    if(!on("save_video")&&!on("save_post_images"))return;
                    Object pkg=XposedHelpers.callMethod(p.thisObject,"getSharePackage");Object aweme=awemeFromPackage(pkg,cl);
                    if(aweme==null){once("IM 分享面板：没有当前作品模型");return;}
                    View anchor=(View)XposedHelpers.callMethod(p.thisObject,"getSecondLineFunctionRv");
                    if(anchor==null)anchor=(View)XposedHelpers.callMethod(p.thisObject,"getFirstLineFunctionRv");
                    LinearLayout row=shareRow(root.getContext(),aweme);
                    if(row!=null&&(!ShareRows.nativeFunctions(anchor,row)))shareUnsupported();
                    else if(row!=null)once("IM 分享面板：保存行已加入");
                }catch(Throwable e){once("IM 分享面板：插入失败 "+e.getClass().getSimpleName());fail("save_video");fail("save_post_images");}
            }};
            XposedHelpers.findAndHookMethod(dialog,"onCreate",Bundle.class,add);
            XposedHelpers.findAndHookMethod(dialog,"onFunctionListChanged",List.class,add);
        });
        capability("新版分享面板保存视频 / 图片",()->XposedHelpers.findAndHookMethod("com.ss.android.ugc.aweme.shareplatform.ui.CommonShareDialogPanel",cl,"show",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())return;
                try{
                    Object manager=XposedHelpers.getObjectField(p.thisObject,"uiManager");View root=(View)XposedHelpers.callMethod(manager,"rootView");
                    ShareRows.remove(root);if(!on("save_video")&&!on("save_post_images"))return;
                    Object model=XposedHelpers.getObjectField(p.thisObject,"panelModel");Object config=XposedHelpers.callMethod(model,"LJI");
                    Object pkg=XposedHelpers.getObjectField(config,"LJFF");
                    if(pkg==null||!pkg.getClass().getName().equals("com.ss.android.ugc.aweme.share.improve.pkg.AwemeSharePackage"))return;
                    Object aweme=XposedHelpers.getObjectField(pkg,"q");LinearLayout row=shareRow(root.getContext(),aweme);
                    if(row!=null&&!ShareRows.common(root,row))shareUnsupported();
                }catch(Throwable e){fail("save_video");fail("save_post_images");}
            }
        }));
        capability("本地防撤回（实验）",()->XposedHelpers.findAndHookMethod("com.bytedance.im.core.model.Message",cl,"isRecalled",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(!on("anti_recall")||p.hasThrowable()||!Boolean.TRUE.equals(p.getResult()))return;
                try{
                    // Keep sender's own recall, deleted messages and missing local content intact.
                    if(Boolean.TRUE.equals(XposedHelpers.callMethod(p.thisObject,"isSelf"))||Boolean.TRUE.equals(XposedHelpers.callMethod(p.thisObject,"isDeleted")))return;
                    Object body=XposedHelpers.callMethod(p.thisObject,"getContent");
                    if(body instanceof String&&!((String)body).isEmpty())p.setResult(false);
                }catch(Throwable e){fail("anti_recall");}
            }
        }));
        capability("首页入口 / 设置入口",()->{
            XposedHelpers.findAndHookMethod(Activity.class,"onResume",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
                Activity a=(Activity)p.thisObject;refresh();
                if(a.getClass().getName().equals("com.ss.android.ugc.aweme.setting.ui.DouYinSettingNewVersionActivity")){
                    try{View v=a.findViewById(android.R.id.content);if(v instanceof FrameLayout)addSettings((FrameLayout)v);}catch(Throwable e){fail("settings");}
                }
                if(a.getClass().getName().equals("com.ss.android.ugc.aweme.main.MainActivity"))attachHome(a);
            }});
            XposedHelpers.findAndHookMethod(Activity.class,"onPause",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){detachHome((Activity)p.thisObject);}});
        });
    }
    private boolean blocked(Object item){
        if(on("hide_ads")&&(truth(item,"getAd")||truth(item,"getIsAdAweme")))return true;
        if(on("hide_live_feed")&&truth(item,"isLive"))return true;
        return on("hide_hot_feed")&&(truth(item,"isHotSearchAweme")||truth(item,"isHotSpotRankCard")||truth(item,"isHotListAweme"));
    }
    private void once(String value){if(!report.contains(value)){report.add(value);publish();}}
    private Object awemeFromPackage(Object pkg,ClassLoader cl){
        if(pkg==null)return null;
        if(pkg.getClass().getName().equals("com.ss.android.ugc.aweme.share.improve.pkg.AwemeSharePackage"))return XposedHelpers.getObjectField(pkg,"q");
        try{Class<?> util=XposedHelpers.findClass("com.ss.android.ugc.aweme.share.ShareUtilImpl",cl);
            Object service=XposedHelpers.callStaticMethod(util,"LJJJJLI");return XposedHelpers.callMethod(service,"extractAwemeFromSharePackage",pkg);
        }catch(Throwable ignored){return null;}
    }
    private boolean blockedTab(Object item){
        String id=String.valueOf(XposedHelpers.callMethod(item,"getTabId"));
        String type=String.valueOf(XposedHelpers.callMethod(item,"getTabType"));
        String title=String.valueOf(XposedHelpers.callMethod(item,"getTabTitle"));
        return TabRules.blocked(id,type,title,on("hide_live_entry"),on("hide_shop"),on("hide_hot_entry"));
    }
    private void filterTabs(XC_MethodHook.MethodHookParam p,boolean ids){
        if(p.hasThrowable()||!(p.getResult() instanceof List)||(!on("hide_live_entry")&&!on("hide_shop")&&!on("hide_hot_entry")))return;
        try{List<?> original=(List<?>)p.getResult();List<Object> next=new ArrayList<>();
            for(Object item:original){boolean remove=false;
                if(ids&&item instanceof String){Object tab=XposedHelpers.callMethod(p.thisObject,"getTopTabItem",item);
                    remove=tab==null?TabRules.blocked((String)item,null,null,on("hide_live_entry"),on("hide_shop"),on("hide_hot_entry")):blockedTab(tab);
                }else if(item!=null)remove=blockedTab(item);
                if(!remove)next.add(item);
            }
            if(next.size()!=original.size()){p.setResult(next);once("顶栏：页面与标签列表已过滤");}
        }catch(Throwable e){once("顶栏数据过滤失败 "+e.getClass().getSimpleName());}
    }
    private boolean truth(Object o,String method){return Boolean.TRUE.equals(XposedHelpers.callMethod(o,method));}
    private List<String> imageSources(Object emoji){
        List<String> urls=new ArrayList<>();if(emoji==null)return urls;
        addSources(urls,XposedHelpers.callMethod(emoji,"getAnimateUrl"));
        addSources(urls,XposedHelpers.callMethod(emoji,"getStaticUrl"));return urls;
    }
    private void addSources(List<String> urls,Object model){if(model!=null)addUrlList(urls,XposedHelpers.callMethod(model,"getUrlList"));}
    private void addUrlList(List<String> urls,Object source){
        if(source instanceof List)for(Object u:(List<?>)source)if(u instanceof String){String next=ImageRules.normalized((String)u);
            if(next!=null&&!urls.contains(next)&&urls.size()<8)urls.add(next);}
    }
    private int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    private void saveEmoji(Context c,List<String> url,String local){
        if(url.isEmpty()&&(local==null||local.isEmpty())){LocalDownloads.tip(c,"这张表情暂时没有可用的原文件");return;}
        LocalDownloads.save(c,Collections.singletonList(LocalDownloads.Item.urls(url,local,false)));
    }
    private void shareUnsupported(){String message="分享面板布局未匹配：没有添加保存行";if(!report.contains(message)){report.add(message);publish();}}
    private LinearLayout shareRow(Context c,Object aweme){
        if(aweme==null)return null;
        LinearLayout row=ShareRows.create(c);
        if(on("save_video"))ShareRows.action(row,"↓ 保存视频",v->{if(on("save_video"))savePost(v.getContext(),aweme,true);});
        if(on("save_post_images"))ShareRows.action(row,"↓ 保存图片",v->{if(on("save_post_images"))savePost(v.getContext(),aweme,false);});
        return row.getChildCount()==0?null:row;
    }
    private void savePost(Context c,Object aweme,boolean video){
        try{
            List<LocalDownloads.Item> items=new ArrayList<>();
            if(video){
                Object media=XposedHelpers.callMethod(aweme,"getVideo");
                if(media!=null){List<String> urls=new ArrayList<>();addSources(urls,XposedHelpers.callMethod(media,"getDownloadAddr"));if(!urls.isEmpty())items.add(LocalDownloads.Item.urls(urls,null,true));}
            }else{
                Object images=XposedHelpers.getObjectField(aweme,"images");
                if(images instanceof List)for(Object image:(List<?>)images){
                    if(image==null)continue;
                    List<String> urls=new ArrayList<>();addUrlList(urls,XposedHelpers.getObjectField(image,"downloadUrlList"));
                    addUrlList(urls,XposedHelpers.getObjectField(image,"urlList"));
                    // Retain each selected picture; a missing URL is reported as a failed item rather than silently omitted.
                    items.add(LocalDownloads.Item.urls(urls,null,false));
                }
            }
            LocalDownloads.save(c,items);
        }catch(Throwable e){LocalDownloads.tip(c,"当前内容暂不支持保存");}
    }
    private void addSettings(FrameLayout parent){
        if(parent.findViewWithTag("clean-settings")!=null)return;
        TextView b=new TextView(parent.getContext());b.setTag("clean-settings");b.setText("净音设置");b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);b.setBackgroundColor(Color.rgb(31,94,84));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(parent.getContext(),110),dp(parent.getContext(),44),Gravity.BOTTOM|Gravity.END);lp.bottomMargin=dp(parent.getContext(),22);lp.rightMargin=dp(parent.getContext(),16);parent.addView(b,lp);
        b.setOnClickListener(v->{try{Intent i=new Intent();i.setClassName(Config.APP,Config.APP+".MainActivity");v.getContext().startActivity(i);}catch(Exception ignored){Toast.makeText(v.getContext(),"请从桌面打开净音设置",0).show();}});
    }
    private void attachHome(Activity a){
        if(listeners.containsKey(a))return;View root=a.getWindow().getDecorView();final long[] last={0};
        ViewTreeObserver.OnGlobalLayoutListener l=()->{
            long now=SystemClock.uptimeMillis();if(now-last[0]<250)return;last[0]=now;
            if(!on("hide_live_entry")&&!on("hide_hot_entry")&&!on("hide_shop"))return;
            try{trimHome(root);}catch(Throwable e){fail("hide_live_entry");fail("hide_hot_entry");fail("hide_shop");}
        };
        listeners.put(a,l);root.getViewTreeObserver().addOnGlobalLayoutListener(l);
    }
    private void detachHome(Activity a){ViewTreeObserver.OnGlobalLayoutListener l=listeners.remove(a);if(l!=null){ViewTreeObserver o=a.getWindow().getDecorView().getViewTreeObserver();if(o.isAlive())o.removeOnGlobalLayoutListener(l);}}
    private void trimHome(View root){
        List<TextView> texts=new ArrayList<>();collect(root,texts,new int[]{1600},0);
        boolean recommended=false,follow=false,drawer=false;
        for(TextView t:texts){String s=t.getText().toString();if(!t.isShown())continue;int[] xy=new int[2];t.getLocationOnScreen(xy);
            if(xy[1]<dp(root.getContext(),190)){recommended|=s.equals("推荐");follow|=s.equals("关注");}
            drawer|=s.equals("常用功能")||s.equals("常用小程序");
        }
        if(!drawer&&!(recommended&&follow))return; // Never scan text bubbles in chat/comment pages.
        for(TextView t:texts){if(!t.isShown())continue;String s=t.getText().toString();int[] xy=new int[2];t.getLocationOnScreen(xy);
            boolean top=xy[1]<dp(root.getContext(),190)&&recommended&&follow;
            // Top channels are removed from the page adapter data, never by hiding label text.
            if(drawer&&on("hide_live_entry")&&s.equals("直播广场"))hideEntry(t,140);
            if(!drawer&&on("hide_hot_entry")&&(s.equals("相关搜索")||s.startsWith("相关搜索：")||s.startsWith("相关搜索:"))){
                View target=t;
                if(t.getParent() instanceof ViewGroup){ViewGroup row=(ViewGroup)t.getParent();if(row.getHeight()>0&&row.getHeight()<=dp(root.getContext(),72)&&row.getChildCount()<=6)target=row;}
                hide(target);
            }
        }
    }
    private void collect(View v,List<TextView> out,int[] budget,int depth){
        if(budget[0]--<=0||depth>24)return;
        if(v instanceof TextView)out.add((TextView)v);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount()&&budget[0]>0;i++)collect(g.getChildAt(i),out,budget,depth+1);}
    }
    private void hide(View view){view.setVisibility(View.GONE);ViewGroup.LayoutParams lp=view.getLayoutParams();if(lp!=null){lp.height=0;view.setLayoutParams(lp);}}
    private void hideEntry(TextView text,int maxHeight){
        View target=text;View candidate=text;
        for(int depth=0;depth<2&&candidate.getParent() instanceof ViewGroup;depth++){
            ViewGroup parent=(ViewGroup)candidate.getParent();
            if(parent.getWidth()>dp(text.getContext(),165)||parent.getHeight()>dp(text.getContext(),maxHeight)||parent.getChildCount()>4)break;
            if(parent.isClickable())target=parent;candidate=parent;
        }
        hide(target);
    }
}
