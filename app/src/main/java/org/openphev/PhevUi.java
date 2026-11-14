// SPDX-License-Identifier: GPL-3.0-or-later
package org.openphev;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Small native components; no web view, remote fonts or asset downloads. */
public final class PhevUi {
    public static int BG=0xfff3f5f2, INK=0xff172e28, MUTED=0xff60706a, GREEN=0xff16744d,
        LINE=0xffdce4df, PALE=0xffe8f2eb, AMBER=0xff885500, SURFACE=Color.WHITE, ON_PRIMARY=Color.WHITE;
    public static boolean dark;
    public static void configure(Context c){
        dark=(c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        BG=dark?0xff101a17:0xfff3f5f2;SURFACE=dark?0xff1b2923:Color.WHITE;
        INK=dark?0xffedf5ef:0xff172e28;MUTED=dark?0xffb0c1b7:0xff60706a;
        GREEN=dark?0xff91ddb0:0xff16744d;PALE=dark?0xff263f32:0xffe8f2eb;
        LINE=dark?0xff3b5144:0xffdce4df;AMBER=dark?0xffffcb7b:0xff885500;ON_PRIMARY=dark?0xff10291b:Color.WHITE;
    }
    private PhevUi(){}
    public static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    public static GradientDrawable shape(Context c,int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));if(stroke!=0)d.setStroke(dp(c,1),stroke);return d;}
    public static RippleDrawable ripple(Context c,int fill,int stroke,int radius){return new RippleDrawable(ColorStateList.valueOf(0x2216744d),shape(c,fill,stroke,radius),shape(c,Color.WHITE,0,radius));}
    public static TextView label(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    public static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static Button button(Context c,String label,boolean primary,Runnable action){Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(c,50));b.setMinimumWidth(0);b.setMinWidth(0);b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));b.setBackgroundTintList(null);b.setBackground(ripple(c,primary?GREEN:SURFACE,primary?0:LINE,16));b.setTextColor(primary?ON_PRIMARY:INK);b.setOnClickListener(v->action.run());return b;}
    public static final class Feature extends LinearLayout {
        private final TextView state; private final View dot;private final String name;private final boolean actionable;
        public Feature(Context c,String name,String icon,Runnable action){
            super(c);this.name=name;actionable=action!=null;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(c,80));setPadding(dp(c,16),dp(c,14),dp(c,14),dp(c,14));
            Mark mark=new Mark(c,icon);addView(mark,new LayoutParams(dp(c,32),dp(c,32)));
            LinearLayout words=column(c);LayoutParams wp=new LayoutParams(0,-2,1);wp.setMargins(dp(c,14),0,dp(c,8),0);addView(words,wp);
            TextView title=label(c,name,17,INK);title.setTypeface(Typeface.create("sans-serif-medium",0));words.addView(title);
            state=label(c,"Not connected",12,MUTED);state.setPadding(0,dp(c,4),0,0);words.addView(state);
            dot=new View(c);addView(dot,new LayoutParams(dp(c,9),dp(c,9)));
            setFocusable(actionable);setClickable(actionable);if(actionable)setOnClickListener(v->action.run());
            for(int i=0;i<getChildCount();i++)getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            show(new LiveStatus.Indicator("Not connected",LiveStatus.Tone.NEUTRAL));
        }
        public void show(LiveStatus.Indicator status){
            int tone=status.tone==LiveStatus.Tone.ACTIVE?GREEN:status.tone==LiveStatus.Tone.WAITING?AMBER:MUTED;
            state.setText(status.text);state.setTextColor(tone);dot.setBackground(shape(getContext(),tone,0,8));
            int fill=status.tone==LiveStatus.Tone.ACTIVE?PALE:SURFACE;
            setBackground(actionable?ripple(getContext(),fill,status.tone==LiveStatus.Tone.ACTIVE?0xff8cb89c:LINE,18):shape(getContext(),fill,0,18));
            setContentDescription(name+", "+status.text);setStateDescription(status.text);
        }
        @Override public CharSequence getAccessibilityClassName(){return actionable?Button.class.getName():TextView.class.getName();}
        @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);setAlpha(enabled?1f:0.56f);}
    }
    public static final class Mark extends View {
        private final Paint p=new Paint(3);private final String kind;
        public Mark(Context c,String kind){super(c);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/32f,getHeight()/32f);p.setColor(GREEN);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("heat")){for(int i=0;i<3;i++){float x=8+i*8;Path q=new Path();q.moveTo(x,26);q.cubicTo(x-6,19,x+6,14,x,6);canvas.drawPath(q,p);}}
            else if(kind.equals("cool")){for(int i=0;i<3;i++){canvas.save();canvas.rotate(i*60,16,16);canvas.drawLine(16,3,16,29,p);canvas.drawLine(12,6,16,10,p);canvas.drawLine(20,6,16,10,p);canvas.drawLine(12,26,16,22,p);canvas.drawLine(20,26,16,22,p);canvas.restore();}}
            else if(kind.equals("screen")){Path q=new Path();q.moveTo(3,23);q.lineTo(7,7);q.quadTo(16,3,25,7);q.lineTo(29,23);q.quadTo(16,19,3,23);canvas.drawPath(q,p);for(int i=0;i<3;i++){float x=10+i*6;canvas.drawLine(x,27,x,13,p);canvas.drawLine(x-2,16,x,13,p);canvas.drawLine(x+2,16,x,13,p);}}
            else {Path q=new Path();q.moveTo(18,7);q.lineTo(18,25);q.cubicTo(3,25,3,7,18,7);canvas.drawPath(q,p);for(int i=0;i<3;i++)canvas.drawLine(22,9+i*7,29,9+i*7,p);}
            canvas.restore();
        }
    }
}
