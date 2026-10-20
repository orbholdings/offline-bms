package com.simon.offlinebms;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.text.SimpleDateFormat;
import java.util.*;
public final class ChartView extends View {
    private List<double[]> rows=Collections.emptyList();private int metric=1,selected=-1,ink=0xffeff6fa,muted=0xff99acbc,accent=0xff55dbb1,line=0xff284052;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ChartView(Context c){super(c);setContentDescription("Battery history chart. Drag to inspect a saved point.");setClickable(true);}
    public void theme(int ink,int muted,int accent,int line){this.ink=ink;this.muted=muted;this.accent=accent;this.line=line;invalidate();}
    public void setData(List<double[]> rows,int metric){this.rows=rows;this.metric=metric;selected=-1;invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override public boolean onTouchEvent(MotionEvent e){if(rows.isEmpty())return false;float fraction=Math.max(0,Math.min(1,(e.getX()-dp(47))/(getWidth()-dp(64))));double target=rows.get(0)[0]+fraction*(rows.get(rows.size()-1)[0]-rows.get(0)[0]);int nearest=0;for(int i=1;i<rows.size();i++)if(Math.abs(rows.get(i)[0]-target)<Math.abs(rows.get(nearest)[0]-target))nearest=i;selected=nearest;if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE)getParent().requestDisallowInterceptTouchEvent(true);if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();}invalidate();return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float l=dp(47),t=dp(36),r=getWidth()-dp(16),b=getHeight()-dp(35);p.setTypeface(Typeface.DEFAULT);p.setTextSize(dp(11));p.setColor(muted);p.setTextAlign(Paint.Align.LEFT);
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;for(double[] v:rows)if(Double.isFinite(v[metric])){low=Math.min(low,v[metric]);high=Math.max(high,v[metric]);}
        if(!Double.isFinite(low)){c.drawText("No recorded values in this period",dp(10),getHeight()/2f,p);return;}
        double pad=Math.max(metric==3?.1:1,(high-low)*.15);low-=pad;high+=pad;if(metric==1){low=Math.max(0,low);high=Math.min(100,high);}if(high<=low)high=low+1;
        double start=rows.get(0)[0],end=rows.get(rows.size()-1)[0];if(start==end)end=start+10000;
        p.setStrokeWidth(dp(1));for(int i=0;i<4;i++){float y=t+(b-t)*i/3;p.setColor(line);c.drawLine(l,y,r,y,p);p.setColor(muted);c.drawText(String.format(Locale.UK,metric==3?"%.1f":"%.0f",high-(high-low)*i/3),0,y+dp(4),p);}
        Path path=new Path();boolean begun=false;double previous=0;double gap=Math.max(90000,(end-start)/700*3);for(double[] v:rows){if(!Double.isFinite(v[metric])){begun=false;continue;}float x=l+(r-l)*(float)((v[0]-start)/(end-start)),y=b-(b-t)*(float)((v[metric]-low)/(high-low));if(!begun||v[0]-previous>gap)path.moveTo(x,y);else path.lineTo(x,y);begun=true;previous=v[0];}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));p.setColor(accent);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        SimpleDateFormat f=new SimpleDateFormat("dd MMM HH:mm",Locale.UK);p.setColor(muted);c.drawText(f.format(new Date((long)start)),l,b+dp(25),p);String e=f.format(new Date((long)end));c.drawText(e,r-p.measureText(e),b+dp(25),p);
        if(selected>=0&&selected<rows.size()){double[] v=rows.get(selected);if(Double.isFinite(v[metric])){float x=l+(r-l)*(float)((v[0]-start)/(end-start)),y=b-(b-t)*(float)((v[metric]-low)/(high-low));p.setColor(accent);c.drawLine(x,t,x,b,p);c.drawCircle(x,y,dp(4),p);p.setColor(ink);c.drawText(f.format(new Date((long)v[0]))+"  •  "+String.format(Locale.UK,"%.2f",v[metric]),l,dp(18),p);}}
        else if(rows.size()==1){double[] v=rows.get(0);p.setColor(accent);c.drawCircle(l,b-(b-t)*(float)((v[metric]-low)/(high-low)),dp(4),p);}
    }
}
