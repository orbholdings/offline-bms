package com.simon.offlinebms;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class NavIcon extends View {
    private final String kind;private final int color;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public NavIcon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();float scale=Math.min(getWidth(),getHeight())/24f;c.translate((getWidth()-24*scale)/2f,(getHeight()-24*scale)/2f);c.scale(scale,scale);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(kind.equals("Overview")){c.drawArc(new RectF(3,3,21,21),140,260,false,p);c.drawLine(12,12,16,8,p);c.drawLine(9,20,15,20,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,1.3f,p);}
        else if(kind.equals("Cells")){for(int x:new int[]{3,14}){c.drawRoundRect(x,4,x+7,11,1.5f,1.5f,p);c.drawRoundRect(x,14,x+7,21,1.5f,1.5f,p);}}
        else if(kind.equals("History")){c.drawLine(3,3,3,21,p);c.drawLine(3,21,21,21,p);Path x=new Path();x.moveTo(6,16);x.lineTo(10,11);x.lineTo(14,14);x.lineTo(20,6);c.drawPath(x,p);}
        else if(kind.equals("Batteries")){for(int y:new int[]{4,14}){c.drawRoundRect(3,y,19,y+7,1.5f,1.5f,p);c.drawLine(21,y+2,21,y+5,p);c.drawLine(7,y+2,7,y+5,p);c.drawLine(5.5f,y+3.5f,8.5f,y+3.5f,p);}}
        else{Path gear=new Path();for(int i=0;i<32;i++){double a=i*Math.PI/16;float r=i%4==0||i%4==3?7.4f:9.2f;float x=(float)(12+r*Math.cos(a)),y=(float)(12+r*Math.sin(a));if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,p);c.drawCircle(12,12,3.1f,p);}c.restore();
    }
}
