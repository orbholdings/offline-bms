package com.simon.offlinebms;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

public final class GaugeView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int soc=-1,ink=0xffedf4f8,muted=0xff91a8b5,accent=0xff55dbb1,line=0xff284052;
    private String state="Awaiting battery";
    public GaugeView(Context c){super(c);}
    public void theme(int ink,int muted,int accent,int line){this.ink=ink;this.muted=muted;this.accent=accent;this.line=line;invalidate();}
    public void reading(int percent,String state){soc=percent;this.state=state;setContentDescription(percent<0?"Waiting for charge reading":percent+" percent state of charge, "+state);invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth()-dp(40),getHeight()-dp(18)),x=getWidth()/2f,y=getHeight()/2f,r=size/2-dp(14);RectF arc=new RectF(x-r,y-r,x+r,y+r);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(10));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(line);c.drawArc(arc,135,270,false,p);
        if(soc>=0){p.setColor(soc<=20?0xffefb35a:accent);c.drawArc(arc,135,270*soc/100f,false,p);}p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setColor(muted);p.setTextSize(dp(11));c.drawText("STATE OF CHARGE",x,y-dp(37),p);p.setColor(ink);p.setTextSize(dp(55));c.drawText(soc<0?"—":String.format(Locale.UK,"%d%%",soc),x,y+dp(18),p);p.setColor(muted);p.setTextSize(dp(13));c.drawText(state,x,y+dp(45),p);
    }
}
