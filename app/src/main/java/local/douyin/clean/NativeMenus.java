package local.douyin.clean;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Adds siblings to verified native LinearLayout button rows, never a floating overlay. */
public final class NativeMenus {
    public static final int GREEN=Color.rgb(36,153,130);
    private static final class State {
        final String name;final TextView anchor;final LinearLayout.LayoutParams original;
        State(String n,TextView a,LinearLayout.LayoutParams p){name=n;anchor=a;original=p;}
    }
    private static final class PopupState {
        final TextView anchor;final ViewGroup.LayoutParams original;final int index;
        PopupState(TextView a,ViewGroup.LayoutParams p,int i){anchor=a;original=p;index=i;}
    }
    public static void removePopup(TextView anchor){
        if(!(anchor.getParent() instanceof LinearLayout))return;
        LinearLayout row=(LinearLayout)anchor.getParent();if(!(row.getTag() instanceof PopupState))return;
        PopupState s=(PopupState)row.getTag();if(!(row.getParent() instanceof FrameLayout))return;
        FrameLayout parent=(FrameLayout)row.getParent();row.removeView(anchor);parent.removeView(row);
        parent.addView(anchor,Math.min(s.index,parent.getChildCount()),s.original);
    }
    public static boolean popup(TextView anchor,View.OnClickListener click){
        if(!(anchor.getParent() instanceof FrameLayout)||anchor.getVisibility()!=View.VISIBLE)return false;
        FrameLayout parent=(FrameLayout)anchor.getParent();int index=parent.indexOfChild(anchor);
        ViewGroup.LayoutParams original=anchor.getLayoutParams();
        LinearLayout row=new LinearLayout(anchor.getContext());row.setOrientation(LinearLayout.HORIZONTAL);
        row.setTag(new PopupState(anchor,original,index));parent.removeView(anchor);parent.addView(row,index,original);
        row.addView(anchor,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1));
        return beside(anchor,"favorite-download","下载",click);
    }
    public static TextView find(View root,String name){
        if(root instanceof TextView&&root.getTag() instanceof State&&((State)root.getTag()).name.equals(name))return (TextView)root;
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=find(g.getChildAt(i),name);if(found!=null)return found;}}
        return null;
    }
    public static void remove(View root,String name){
        TextView button=find(root,name);if(button==null)return;State s=(State)button.getTag();
        if(button.getParent() instanceof ViewGroup)((ViewGroup)button.getParent()).removeView(button);
        s.anchor.setLayoutParams(new LinearLayout.LayoutParams(s.original));
    }
    public static boolean beside(TextView anchor,String name,String label,View.OnClickListener click){
        if(!(anchor.getParent() instanceof LinearLayout))return false;
        LinearLayout row=(LinearLayout)anchor.getParent();if(row.getOrientation()!=LinearLayout.HORIZONTAL)return false;
        TextView existing=find(row,name);if(existing!=null){existing.setOnClickListener(click);return true;}
        if(!(anchor.getLayoutParams() instanceof LinearLayout.LayoutParams))return false;
        LinearLayout.LayoutParams original=new LinearLayout.LayoutParams((LinearLayout.LayoutParams)anchor.getLayoutParams());
        TextView button;
        try{button=(TextView)anchor.getClass().getConstructor(Context.class).newInstance(anchor.getContext());}
        catch(Exception ignored){button=new TextView(anchor.getContext());}
        button.setText(label);button.setTextSize(TypedValue.COMPLEX_UNIT_PX,anchor.getTextSize());button.setTypeface(anchor.getTypeface());button.setGravity(anchor.getGravity());
        button.setTextColor(Color.WHITE);button.setSingleLine(true);button.setPadding(anchor.getPaddingLeft(),anchor.getPaddingTop(),anchor.getPaddingRight(),anchor.getPaddingBottom());
        button.setMinHeight(anchor.getMinHeight());button.setTag(new State(name,anchor,original));button.setContentDescription(label);
        Drawable background=anchor.getBackground();
        if(background!=null&&background.getConstantState()!=null){Drawable copy=background.getConstantState().newDrawable().mutate();copy.setTint(GREEN);button.setBackground(copy);}
        else{GradientDrawable d=new GradientDrawable();d.setColor(GREEN);d.setCornerRadius(dp(anchor.getContext(),8));button.setBackground(d);}
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(original);first.width=0;first.weight=1;anchor.setLayoutParams(first);
        LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,original.height,1);second.topMargin=original.topMargin;second.bottomMargin=original.bottomMargin;
        second.leftMargin=dp(anchor.getContext(),6);row.addView(button,row.indexOfChild(anchor)+1,second);button.setOnClickListener(click);return true;
    }
    public static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    private NativeMenus(){}
}
