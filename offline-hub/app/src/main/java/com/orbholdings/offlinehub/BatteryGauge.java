package com.orbholdings.offlinehub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class BatteryGauge extends View {
    private final Paint paint=new Paint(3);
    private final int teal=Color.rgb(73,218,191);
    public BatteryGauge(Context context){super(context);}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w);
        setMeasuredDimension(width,Math.min(width,Math.round(265*getResources().getDisplayMetrics().density)));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float d=getResources().getDisplayMetrics().density;
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float radius=Math.min(getWidth(),getHeight())*.42f;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(13*d);paint.setStrokeCap(Paint.Cap.ROUND);
        RectF ring=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
        paint.setColor(Color.rgb(49,72,83));canvas.drawArc(ring,135,270,false,paint);
        paint.setColor(teal);canvas.drawArc(ring,135,270*.82f,false,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
        paint.setColor(Color.WHITE);paint.setTextSize(53*d);canvas.drawText("82%",cx,cy-12*d,paint);
        paint.setTextSize(12*d);paint.setColor(Color.rgb(154,169,189));canvas.drawText("STATE OF CHARGE",cx,cy+12*d,paint);
        paint.setColor(Color.WHITE);paint.setTextSize(19*d);
        canvas.drawText("12.9 V   ·   +4.2 A",cx,cy+44*d,paint);
        paint.setColor(teal);paint.setTextSize(11*d);
        canvas.drawText("↗ CHARGING  ·  DEMO",cx,cy+65*d,paint);
    }
}
