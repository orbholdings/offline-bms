package com.simon.offlinebms;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;
public final class CellChartView extends View {
    private Reading reading;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int ink,muted,accent,line;
    public CellChartView(Context c){super(c);}
    public void data(Reading r,int ink,int muted,int accent,int line){reading=r;this.ink=ink;this.muted=muted;this.accent=accent;this.line=line;setContentDescription(r==null?"Waiting for cell readings":"Cell voltage chart; minimum "+r.minCell()+" volts, maximum "+r.maxCell()+" volts");invalidate();}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);p.setTypeface(Typeface.DEFAULT);p.setTextSize(dp(11));p.setColor(muted);if(reading==null||reading.cells.length==0){c.drawText("Cell readings will appear here",dp(12),getHeight()/2f,p);return;}
        double low=reading.minCell()-.01,high=reading.maxCell()+.01;float l=dp(40),right=getWidth()-dp(8),top=dp(18),bottom=getHeight()-dp(29);p.setTextAlign(Paint.Align.LEFT);c.drawText(String.format(Locale.UK,"%.3f",high),0,top+dp(4),p);c.drawText(String.format(Locale.UK,"%.3f",low),0,bottom,p);
        p.setColor(line);c.drawLine(l,bottom,right,bottom,p);float step=(right-l)/reading.cells.length;
        for(int i=0;i<reading.cells.length;i++){float x=l+step*i+step*.16f,y=bottom-(bottom-top)*(float)((reading.cells[i]-low)/(high-low));boolean balancing=(reading.basic.balanceMask&(1L<<i))!=0;p.setColor(balancing?accent:Math.abs(reading.cells[i]-reading.minCell())<.0004?0xffe5b268:0xff80a7df);c.drawRoundRect(x,y,l+step*(i+1)-step*.16f,bottom,dp(3),dp(3),p);p.setColor(muted);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(reading.cells.length>20?8:10));c.drawText(Integer.toString(i+1),l+step*(i+.5f),bottom+dp(18),p);}
    }
}
