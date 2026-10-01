package local.douyin.clean;

import java.util.*;

/** Match channel identities. homepage_hot is the recommended feed, not the trending page. */
public final class TabRules {
    public static boolean blocked(String id,String type,String title,boolean live,boolean shop,boolean hot){
        if("homepage_hot".equals(id)||"homepage_hot_container".equals(id)||"homepage_follow".equals(id))return false;
        if(live&&(one(id,"homepage_tablive","homepage_live")||one(type,"live","tablive")||"直播".equals(title)))return true;
        if(shop&&(one(id,"homepage_mall","homepage_groupon","homepage_shop","homepage_ecom")||one(type,"mall","groupon","ecom")||one(title,"商城","团购")))return true;
        return hot&&(one(id,"homepage_trending","homepage_hotspot","homepage_hot_search","homepage_hot_search_list")||one(type,"trending","hotspot","hot_search")||one(title,"热点","热榜"));
    }
    private static boolean one(String s,String... values){for(String value:values)if(value.equals(s))return true;return false;}
    private TabRules(){}
}
