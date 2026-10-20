package com.simon.offlinebms;

/** Integrates measured samples only; gaps and separate connections never invent energy. */
public final class EnergyStats {
    public int samples;
    public long firstTime,lastTime,observedMs;
    public double chargeWh,dischargeWh,chargeAh,dischargeAh;
    public double minVoltage=Double.NaN,maxVoltage=Double.NaN,peakChargeW,peakDischargeW;
    private double lastPower,lastCurrent;
    private String lastSession="";
    public void add(long time,double voltage,double current,String session) {
        if(!Double.isFinite(voltage)||!Double.isFinite(current)||voltage<0)return;
        String id=session==null?"":session;
        double power=voltage*current;
        if(samples>0 && time>lastTime && time-lastTime<=90000 && id.equals(lastSession)) {
            double hours=(time-lastTime)/3600000.0;
            chargeWh+=positiveArea(lastPower,power)*hours;
            dischargeWh+=positiveArea(-lastPower,-power)*hours;
            chargeAh+=positiveArea(lastCurrent,current)*hours;
            dischargeAh+=positiveArea(-lastCurrent,-current)*hours;
            observedMs+=time-lastTime;
        }
        if(samples==0)firstTime=time;
        minVoltage=Double.isNaN(minVoltage)?voltage:Math.min(minVoltage,voltage);
        maxVoltage=Double.isNaN(maxVoltage)?voltage:Math.max(maxVoltage,voltage);
        peakChargeW=Math.max(peakChargeW,power);peakDischargeW=Math.max(peakDischargeW,-power);
        lastTime=time;lastPower=power;lastCurrent=current;lastSession=id;samples++;
    }
    static double positiveArea(double a,double b) {
        if(a>=0 && b>=0)return (a+b)/2;
        if(a<=0 && b<=0)return 0;
        double positive=Math.max(a,b);
        return positive*positive/(2*(Math.abs(a)+Math.abs(b)));
    }
    public static double availableAh(double remaining,double capacity,double reservePercent) {
        if(!Double.isFinite(remaining)||!Double.isFinite(capacity)||remaining<0||capacity<=0)return Double.NaN;
        return Math.max(0,Math.min(remaining,capacity)-capacity*Math.max(0,Math.min(100,reservePercent))/100);
    }
    public static double runtimeHours(double availableAh,double current) {
        return Double.isFinite(availableAh)&&availableAh>=0&&current<-.1?availableAh/-current:Double.NaN;
    }
    public static double loadHours(double usableWh,double watts,double efficiencyPercent) {
        return Double.isFinite(usableWh)&&usableWh>=0&&Double.isFinite(watts)&&watts>0&&efficiencyPercent>0&&efficiencyPercent<=100?usableWh*efficiencyPercent/100/watts:Double.NaN;
    }
}
