import local.douyin.clean.TabRules;
public class TabRulesCheck {
 private static int checks;
 private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  for(String id:new String[]{"homepage_hot","homepage_hot_container","homepage_follow"})check(!TabRules.blocked(id,"trending","热榜",true,true,true),"protect recommended/follow feed");
  for(String id:new String[]{"homepage_tablive","homepage_live"}){check(TabRules.blocked(id,null,null,true,false,false),"remove live channel");check(!TabRules.blocked(id,null,null,false,true,true),"independent live switch");}
  for(String id:new String[]{"homepage_mall","homepage_groupon","homepage_shop","homepage_ecom"}){check(TabRules.blocked(id,null,null,false,true,false),"remove commerce channel");check(!TabRules.blocked(id,null,null,true,false,true),"independent shop switch");}
  for(String id:new String[]{"homepage_trending","homepage_hotspot","homepage_hot_search"}){check(TabRules.blocked(id,null,null,false,false,true),"remove trending channel");check(!TabRules.blocked(id,null,null,true,true,false),"independent trending switch");}
  check(!TabRules.blocked("homepage_nearby","nearby","商水",true,true,true),"preserve city channel");
  check(!TabRules.blocked("homepage_unknown",null,"推荐直播视频",true,true,true),"no substring removal");
  check(TabRules.blocked("custom_v2","live","新直播",true,false,false),"typed live channel");
  check(TabRules.blocked("custom_v2",null,"团购",false,true,false),"renamed commerce ID");
  check(!TabRules.blocked(null,null,null,true,true,true),"unknown metadata");
  System.out.println("PASS: "+checks+" channel removal rule checks");
 }
}
