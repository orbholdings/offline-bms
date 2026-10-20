package com.simon.offlinebms;

import java.util.*;

/** Two-sample confirmation, recovery hysteresis, and edge-triggered local alerts. */
public final class AlertEngine {
    public static final class Config {
        public boolean enabled,socEnabled=true,tempEnabled,spreadEnabled,cellsEnabled;
        public double lowSoc=20,highTemp=55,spreadMv=100,lowCell=2.8,highCell=3.65;
    }
    public static final class Sample {
        public int soc,protection;
        public double minCell,maxCell,spreadMv,highTemp;
        public Sample(int soc,int protection,double min,double max,double spread,double temp){this.soc=soc;this.protection=protection;minCell=min;maxCell=max;spreadMv=spread;highTemp=temp;}
    }
    private final Map<String,Integer> strikes=new HashMap<>();
    private final LinkedHashSet<String> active=new LinkedHashSet<>();
    public List<String> update(Config c,Sample s) {
        List<String> raised=new ArrayList<>();
        if(!c.enabled){active.clear();strikes.clear();return raised;}
        check("Low state of charge",c.socEnabled && s.soc<=c.lowSoc+(active.contains("Low state of charge")?3:0),false,raised);
        check("High battery temperature",c.tempEnabled && Double.isFinite(s.highTemp) && s.highTemp>=c.highTemp-(active.contains("High battery temperature")?2:0),false,raised);
        check("Large cell voltage spread",c.spreadEnabled && Double.isFinite(s.spreadMv) && s.spreadMv>=c.spreadMv-(active.contains("Large cell voltage spread")?10:0),false,raised);
        check("Low cell voltage",c.cellsEnabled && Double.isFinite(s.minCell) && s.minCell<=c.lowCell+(active.contains("Low cell voltage")?.05:0),false,raised);
        check("High cell voltage",c.cellsEnabled && Double.isFinite(s.maxCell) && s.maxCell>=c.highCell-(active.contains("High cell voltage")?.05:0),false,raised);
        check("BMS protection active",s.protection!=0,true,raised);
        return raised;
    }
    private void check(String key,boolean condition,boolean immediate,List<String> raised) {
        if(!condition){active.remove(key);strikes.remove(key);return;}
        int n=strikes.getOrDefault(key,0)+1;strikes.put(key,Math.min(n,2));
        if((immediate||n>=2)&&active.add(key))raised.add(key);
    }
    public List<String> active(){return new ArrayList<>(active);}
}
