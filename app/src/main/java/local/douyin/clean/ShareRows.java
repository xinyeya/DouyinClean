package local.douyin.clean;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Structural footer inside the share sheet; the original actions retain their listeners. */
public final class ShareRows {
    private static final String TAG="local-clean-share-row";
    private static final String WRAPPER="local-clean-share-wrapper";
    public static LinearLayout create(android.content.Context c){
        LinearLayout row=new LinearLayout(c);row.setTag(TAG);row.setOrientation(LinearLayout.HORIZONTAL);
        int pad=NativeMenus.dp(c,12);row.setPadding(pad,pad/2,pad,pad/2);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1,NativeMenus.dp(c,64)));return row;
    }
    public static void action(LinearLayout row,String label,View.OnClickListener click){
        TextView b=new TextView(row.getContext());b.setText(label);b.setTextSize(14);b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);b.setContentDescription(label);
        GradientDrawable bg=new GradientDrawable();bg.setColor(NativeMenus.GREEN);bg.setCornerRadius(NativeMenus.dp(row.getContext(),8));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(NativeMenus.dp(row.getContext(),4),0,NativeMenus.dp(row.getContext(),4),0);
        row.addView(b,p);b.setOnClickListener(click);
    }
    public static boolean after(View content,LinearLayout row){
        if(content.getParent() instanceof LinearLayout){
            LinearLayout wrapper=(LinearLayout)content.getParent();
            View old=wrapper.findViewWithTag(TAG);
            if(WRAPPER.equals(wrapper.getTag())){if(old!=null&&old.getParent()==wrapper)wrapper.removeView(old);wrapper.addView(row);return true;}
        }
        if(!(content.getParent() instanceof FrameLayout))return false;
        FrameLayout parent=(FrameLayout)content.getParent();remove(parent);
        int index=parent.indexOfChild(content);ViewGroup.LayoutParams original=content.getLayoutParams();
        LinearLayout wrapper=new LinearLayout(content.getContext());wrapper.setTag(WRAPPER);wrapper.setOrientation(LinearLayout.VERTICAL);
        parent.removeView(content);parent.addView(wrapper,index,original);
        boolean fill=original.height==ViewGroup.LayoutParams.MATCH_PARENT||original.height>0;
        wrapper.addView(content,new LinearLayout.LayoutParams(-1,fill?0:original.height,fill?1:0));
        wrapper.addView(row);return true;
    }
    public static boolean common(View root,LinearLayout row){
        remove(root);
        TextView cancel=findCancel(root,0);
        if(cancel!=null){
            View child=cancel;
            for(int i=0;i<8&&child.getParent() instanceof ViewGroup;i++){
                ViewGroup p=(ViewGroup)child.getParent();
                if(p instanceof LinearLayout&&((LinearLayout)p).getOrientation()==LinearLayout.VERTICAL){
                    p.addView(row,p.indexOfChild(child));return true;
                }
                child=p;if(child==root)break;
            }
        }
        if(root instanceof LinearLayout&&((LinearLayout)root).getOrientation()==LinearLayout.VERTICAL){((LinearLayout)root).addView(row);return true;}
        if(root instanceof FrameLayout&&((FrameLayout)root).getChildCount()==1){
            View child=((FrameLayout)root).getChildAt(0);
            if(child instanceof LinearLayout&&WRAPPER.equals(child.getTag())){((LinearLayout)child).addView(row);return true;}
            return after(child,row);
        }
        return false;
    }
    public static void remove(View root){View old=root.findViewWithTag(TAG);if(old!=null&&old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);}
    public static boolean nativeFunctions(View anchor,LinearLayout row){
        if(anchor==null||!(anchor.getParent() instanceof ViewGroup))return false;
        ViewGroup parent=(ViewGroup)anchor.getParent();
        boolean nativeVertical=parent instanceof LinearLayout&&((LinearLayout)parent).getOrientation()==LinearLayout.VERTICAL;
        if(!nativeVertical&&"androidx.appcompat.widget.LinearLayoutCompat".equals(parent.getClass().getName())){
            try{nativeVertical=((Integer)parent.getClass().getMethod("getOrientation").invoke(parent))==LinearLayout.VERTICAL;}catch(Exception ignored){}
        }
        if(!nativeVertical)return false;
        remove(parent);parent.addView(row,parent.getChildCount(),new ViewGroup.LayoutParams(-1,NativeMenus.dp(parent.getContext(),64)));parent.requestLayout();return true;
    }
    private static TextView findCancel(View v,int depth){
        if(depth>14)return null;
        if(v instanceof TextView&&"取消".contentEquals(((TextView)v).getText()))return (TextView)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--){TextView t=findCancel(g.getChildAt(i),depth+1);if(t!=null)return t;}}
        return null;
    }
    private ShareRows(){}
}
