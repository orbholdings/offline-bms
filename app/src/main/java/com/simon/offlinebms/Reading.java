package com.simon.offlinebms;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Reading {
    public final long timestamp;
    public final JbdProtocol.Basic basic;
    public final double[] cells;
    public final String device;
    public final String address;
    public final boolean isDemo;
    public Reading(long timestamp,JbdProtocol.Basic basic,double[] cells,String device) {
        this(timestamp,basic,cells,device,false);
    }
    private Reading(long timestamp,JbdProtocol.Basic basic,double[] cells,String device,boolean demo){
        this.timestamp=timestamp;this.basic=basic;this.cells=cells.clone();this.device=device;isDemo=demo;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)\\[([0-9a-f]{2}(?::[0-9a-f]{2}){5})\\]$").matcher(device);
        address=m.find()?m.group(1).toUpperCase(java.util.Locale.ROOT):"legacy:"+device;
    }
    public double maxTemp(){double max=Double.NaN;for(double t:basic.temperatures)max=Double.isNaN(max)?t:Math.max(max,t);return max;}
    public String[] protectionLabels(){return basic.vendor.equals("JK")?JkProtocol.alarmLabels(basic.vendorAlarmFlags):JbdProtocol.protectionLabels(basic.protection);}
    public double minCell() { double m=Double.POSITIVE_INFINITY;for(double v:cells)m=Math.min(m,v);return cells.length==0?Double.NaN:m; }
    public double maxCell() { double m=Double.NEGATIVE_INFINITY;for(double v:cells)m=Math.max(m,v);return cells.length==0?Double.NaN:m; }
    public double deltaMv() { return (maxCell()-minCell())*1000; }
    public JSONObject json() throws JSONException {
        JSONObject j=new JSONObject();
        j.put("timestamp_unix_ms",timestamp);j.put("device",device);j.put("address",address);
        j.put("voltage_v",basic.voltage);j.put("current_a",basic.current);j.put("power_w",basic.power());j.put("soc_percent",basic.soc);
        j.put("remaining_ah",basic.remainingAh);j.put("capacity_ah",basic.capacityAh);j.put("cycles",basic.cycles);
        j.put("charge_enabled",basic.chargeEnabled());j.put("discharge_enabled",basic.dischargeEnabled());
        j.put("protection_flags",basic.protection);j.put("balance_mask",basic.balanceMask);j.put("firmware_version",basic.versionText());
        j.put("cell_count",basic.cellCount);j.put("extended_current_scale",basic.extendedScale);
        j.put("production_date",basic.productionDate);j.put("firmware_raw",basic.version);
        j.put("vendor",basic.vendor);j.put("vendor_alarm_flags",basic.vendorAlarmFlags);j.put("balancing_active",basic.balancingActive);
        JSONArray cellJson=new JSONArray(),tempJson=new JSONArray();
        for(double v:cells)cellJson.put(v);for(double t:basic.temperatures)tempJson.put(t);
        j.put("cell_voltages_v",cellJson);j.put("temperatures_c",tempJson);
        if(basic.temperatureLabels.length==basic.temperatures.length&&basic.temperatureLabels.length>0){JSONArray labels=new JSONArray();for(String label:basic.temperatureLabels)labels.put(label);j.put("temperature_labels",labels);}
        if(cells.length>0)j.put("cell_delta_mv",Math.round(deltaMv()));
        return j;
    }
    public static Reading fromJson(JSONObject j)throws JSONException{
        long time=j.getLong("timestamp_unix_ms");if(time<1262304000000L||time>System.currentTimeMillis()+86400000)throw new JSONException("Invalid reading time");
        String device=j.getString("device");if(device.isEmpty()||device.length()>160)throw new JSONException("Invalid device name");
        JbdProtocol.Basic b=new JbdProtocol.Basic();b.voltage=number(j,"voltage_v",0,650);b.current=number(j,"current_a",-4000,4000);b.remainingAh=number(j,"remaining_ah",0,100000);b.capacityAh=number(j,"capacity_ah",0,100000);
        b.soc=j.getInt("soc_percent");b.cycles=j.getInt("cycles");b.protection=j.getInt("protection_flags");b.balanceMask=j.getLong("balance_mask");b.cellCount=j.getInt("cell_count");b.version=j.optInt("firmware_raw",0);b.productionDate=j.optString("production_date","Unknown");b.extendedScale=j.optBoolean("extended_current_scale",false);
        b.vendor=j.optString("vendor","JBD");b.vendorAlarmFlags=j.optLong("vendor_alarm_flags",0);b.firmware=j.optString("firmware_version","");b.balancingActive=j.optBoolean("balancing_active",false);if(!b.vendor.equals("JBD")&&!b.vendor.equals("JK"))throw new JSONException("Invalid vendor");
        if(b.soc<0||b.soc>100||b.cycles<0||b.protection<0||b.protection>65535||b.cellCount<1||b.cellCount>32||b.version<0||b.version>255||b.productionDate.length()>30||b.firmware.length()>80||b.vendorAlarmFlags<0||b.vendorAlarmFlags>0xffffffffL||b.balanceMask<0||b.balanceMask>0xffffffffL)throw new JSONException("Invalid BMS fields");
        b.fet=(j.getBoolean("charge_enabled")?1:0)|(j.getBoolean("discharge_enabled")?2:0);
        JSONArray cells=j.getJSONArray("cell_voltages_v"),temps=j.getJSONArray("temperatures_c");if(cells.length()!=b.cellCount||temps.length()>16)throw new JSONException("Invalid cell or temperature count");
        double[] values=new double[cells.length()];for(int i=0;i<values.length;i++){values[i]=cells.getDouble(i);if(!Double.isFinite(values[i])||values[i]<0||values[i]>6.5)throw new JSONException("Invalid cell voltage");}
        b.temperatures=new double[temps.length()];for(int i=0;i<b.temperatures.length;i++){b.temperatures[i]=temps.getDouble(i);if(!Double.isFinite(b.temperatures[i])||b.temperatures[i]<-100||b.temperatures[i]>200)throw new JSONException("Invalid temperature");}
        JSONArray labels=j.optJSONArray("temperature_labels");if(labels!=null){if(labels.length()!=temps.length())throw new JSONException("Invalid temperature labels");b.temperatureLabels=new String[labels.length()];for(int i=0;i<labels.length();i++){String label=labels.getString(i);if(label.isEmpty()||label.length()>24||!label.matches("[A-Za-z0-9 _-]+"))throw new JSONException("Invalid temperature label");b.temperatureLabels[i]=label;}}
        Reading r=new Reading(time,b,values,device);String declared=j.optString("address",r.address);if(!declared.equalsIgnoreCase(r.address))throw new JSONException("Battery address does not match device");return r;
    }
    private static double number(JSONObject j,String key,double min,double max)throws JSONException{double n=j.getDouble(key);if(!Double.isFinite(n)||n<min||n>max)throw new JSONException("Invalid "+key);return n;}
    public static Reading demo() {
        JbdProtocol.Basic b=new JbdProtocol.Basic();b.voltage=52.78;b.current=-12.4;b.remainingAh=196;b.capacityAh=280;b.soc=70;b.cycles=146;
        b.cellCount=16;b.fet=3;b.version=0x24;b.temperatures=new double[]{23.4,24.1};b.balanceMask=1L<<7;b.productionDate="2024-08-12";
        double[] cells=new double[16];for(int i=0;i<16;i++)cells[i]=3.290+(i%5)*0.005;cells[7]=3.313;
        return new Reading(System.currentTimeMillis(),b,cells,"Demo battery • sample readings",true);
    }
}
