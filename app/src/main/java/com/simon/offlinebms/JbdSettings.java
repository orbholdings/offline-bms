package com.simon.offlinebms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Explicit classic-JBD parameter map. Telemetry, calibration and control paths stay separate. */
public final class JbdSettings {
    private JbdSettings(){}
    public static final class Field {
        public final String id,label,group,unit;
        public final int register,decimals,bit;
        final double min,max;
        final boolean negative,temperature;
        Field(String id,String label,String group,int reg,String unit,int decimals,double min,double max,boolean negative,boolean temperature,int bit){this.id=id;this.label=label;this.group=group;register=reg;this.unit=unit;this.decimals=decimals;this.min=min;this.max=max;this.negative=negative;this.temperature=temperature;this.bit=bit;}
        public double value(byte[] bytes){int raw=u16(bytes);if(bit!=0)return (raw&bit)!=0?1:0;if(negative)raw=-(short)raw;return temperature?(raw-2731)/10.0:raw/Math.pow(10,decimals);}
        public String display(byte[] bytes){return bit!=0?(value(bytes)==1?"On":"Off"):String.format(Locale.UK,"%."+decimals+"f",value(bytes))+(unit.isEmpty()?"":" "+unit);}
        public String input(byte[] bytes){return String.format(Locale.UK,"%."+decimals+"f",value(bytes));}
        public boolean plausible(byte[] bytes){try{double n=value(bytes);return n>=min&&n<=max;}catch(IllegalArgumentException e){return false;}}
        public byte[] encode(String text,byte[] original){
            BigDecimal n;
            try{n=new BigDecimal(text.trim()).setScale(decimals,RoundingMode.UNNECESSARY);}catch(Exception e){throw new IllegalArgumentException("Use up to "+decimals+" decimal places");}
            if(n.compareTo(BigDecimal.valueOf(min))<0||n.compareTo(BigDecimal.valueOf(max))>0)throw new IllegalArgumentException("Encoding range: "+min+"–"+max+" "+unit);
            int raw;
            if(bit!=0){int old=u16(original);raw=n.intValue()==1?old|bit:old&~bit;}
            else{if(temperature)n=n.add(new BigDecimal("273.1"));raw=n.movePointRight(decimals).intValueExact();if(negative)raw=-raw;raw&=65535;}
            return word(raw);
        }
    }
    private static Field f(String id,String label,String group,int reg,String unit,int digits,double min,double max){return new Field(id,label,group,reg,unit,digits,min,max,false,false,0);}
    private static Field t(String id,String label,int reg){return new Field(id,label,"Temperature settings",reg,"°C",1,-40,100,false,true,0);}
    public static final List<Field> FIELDS=Collections.unmodifiableList(Arrays.asList(
        f("design_capacity","Design capacity","Basic settings",0x10,"Ah",2,.01,655.35),
        f("cycle_capacity","Cycle capacity","Basic settings",0x11,"Ah",2,.01,655.35),
        f("self_discharge","Estimated self-discharge","Basic settings",0x14,"%",1,0,100),
        f("cell_over","Cell overvoltage protection","Voltage protection",0x24,"V",3,.5,5),
        f("cell_over_release","Cell overvoltage release","Voltage protection",0x25,"V",3,.5,5),
        f("cell_under","Cell undervoltage protection","Voltage protection",0x26,"V",3,.5,5),
        f("cell_under_release","Cell undervoltage release","Voltage protection",0x27,"V",3,.5,5),
        f("pack_over","Pack overvoltage protection","Voltage protection",0x20,"V",2,1,200),
        f("pack_over_release","Pack overvoltage release","Voltage protection",0x21,"V",2,1,200),
        f("pack_under","Pack undervoltage protection","Voltage protection",0x22,"V",2,1,200),
        f("pack_under_release","Pack undervoltage release","Voltage protection",0x23,"V",2,1,200),
        f("charge_current","Charge overcurrent protection","Current settings",0x28,"A",2,.01,327.67),
        new Field("discharge_current","Discharge overcurrent protection","Current settings",0x29,"A",2,.01,327.68,true,false,0),
        t("charge_hot","Charge high-temperature protection",0x18),t("charge_hot_release","Charge high-temperature release",0x19),
        t("charge_cold","Charge low-temperature protection",0x1a),t("charge_cold_release","Charge low-temperature release",0x1b),
        t("discharge_hot","Discharge high-temperature protection",0x1c),t("discharge_hot_release","Discharge high-temperature release",0x1d),
        t("discharge_cold","Discharge low-temperature protection",0x1e),t("discharge_cold_release","Discharge low-temperature release",0x1f),
        f("balance_start","Balance start voltage","Balance settings",0x2a,"V",3,.5,5),
        f("balance_window","Balance voltage difference","Balance settings",0x2b,"mV",0,0,1000),
        new Field("balance_enabled","Enable balancing","Balance settings",0x2d,"",0,0,1,false,false,4),
        new Field("charge_balance","Balance during charge flag","Balance settings",0x2d,"",0,0,1,false,false,8),
        f("soc_100","Voltage at 100% SOC","Capacity voltage",0x12,"V",3,.5,5),
        f("soc_80","Voltage at 80% SOC","Capacity voltage",0x32,"V",3,.5,5),
        f("soc_60","Voltage at 60% SOC","Capacity voltage",0x33,"V",3,.5,5),
        f("soc_40","Voltage at 40% SOC","Capacity voltage",0x34,"V",3,.5,5),
        f("soc_20","Voltage at 20% SOC","Capacity voltage",0x35,"V",3,.5,5),
        f("soc_0","Voltage at 0% SOC","Capacity voltage",0x13,"V",3,.5,5)
    ));
    public static Field field(String id){for(Field f:FIELDS)if(f.id.equals(id))return f;throw new IllegalArgumentException("Unsupported parameter");}
    public static final int[] READ_ORDER;
    static{LinkedHashSet<Integer> regs=new LinkedHashSet<>(Arrays.asList(0x2f,0x10,0x24,0x26,0x20,0x22,0x28,0x29));for(Field f:FIELDS)regs.add(f.register);regs.addAll(Arrays.asList(0x15,0x16,0x17,0x2c,0x2e,0xaa));READ_ORDER=regs.stream().mapToInt(Integer::intValue).toArray();}
    public static boolean readable(int reg){for(int r:READ_ORDER)if(r==reg)return true;return false;}
    public static boolean writable(int reg){for(Field f:FIELDS)if(f.register==reg)return true;return false;}
    public static int u16(byte[] p){if(p==null||p.length!=2)throw new IllegalArgumentException("Expected a 16-bit parameter");return (p[0]&255)<<8|p[1]&255;}
    public static byte[] word(int n){if(n<0||n>65535)throw new IllegalArgumentException("16-bit value required");return new byte[]{(byte)(n>>8),(byte)n};}
    private static byte[] command(int mode,int reg,byte[] data){byte[] out=new byte[data.length+7];out[0]=(byte)0xdd;out[1]=(byte)mode;out[2]=(byte)reg;out[3]=(byte)data.length;System.arraycopy(data,0,out,4,data.length);int sum=reg+data.length;for(byte b:data)sum+=b&255;int crc=(-sum)&65535;out[out.length-3]=(byte)(crc>>8);out[out.length-2]=(byte)crc;out[out.length-1]=0x77;return out;}
    public static byte[] read(int reg){if(!readable(reg))throw new IllegalArgumentException("Unsupported parameter read");return command(0xa5,reg,new byte[0]);}
    public static byte[] write(Field f,byte[] value){if(f!=field(f.id)||!writable(f.register)||value==null||value.length!=2||!f.plausible(value))throw new IllegalArgumentException("Unsupported parameter write");return command(0x5a,f.register,value);}
    public static byte[] enter(){return command(0x5a,0,word(0x5678));}
    public static byte[] exit(boolean commit){return command(0x5a,1,word(commit?0x2828:0));}
    public static byte[] password(String password){return JbdAuthentication.legacyLogin(password,false);}
    public static final class Snapshot {
        public final String address,model,firmware;
        public final int cells;
        public final double telemetryCapacity;
        public final boolean extended;
        public final long timestamp;
        private final Map<Integer,byte[]> values;
        public Snapshot(String address,String model,String firmware,int cells,double capacity,boolean extended,long time,Map<Integer,byte[]> source){this.address=address;this.model=model;this.firmware=firmware;this.cells=cells;telemetryCapacity=capacity;this.extended=extended;timestamp=time;values=new LinkedHashMap<>();for(Map.Entry<Integer,byte[]> e:source.entrySet())if(readable(e.getKey())&&e.getValue()!=null)values.put(e.getKey(),e.getValue().clone());}
        public byte[] get(int register){byte[] value=values.get(register);return value==null?null:value.clone();}
        public Map<Integer,byte[]> raw(){Map<Integer,byte[]> out=new LinkedHashMap<>();for(Map.Entry<Integer,byte[]> e:values.entrySet())out.put(e.getKey(),e.getValue().clone());return out;}
        public String layoutProblem(){
            if(extended)return "Extended JBD current/capacity scales are not supported by this parameter editor";
            try{
                if(cells<1||cells>32||u16(get(0x2f))!=cells)return "The parameter cell count does not match live readings";
                if(telemetryCapacity<=0||Math.abs(u16(get(0x10))/100.0-telemetryCapacity)>.011)return "Capacity encoding does not match the classic JBD layout";
                for(String id:new String[]{"cell_over","cell_under","pack_over","pack_under","charge_current","discharge_current"}){Field f=field(id);if(!f.plausible(get(f.register)))return "The returned protection values do not match the supported layout";}
                if(u16(get(0x24))<=u16(get(0x26))||u16(get(0x20))<=u16(get(0x22)))return "Voltage protection thresholds are inconsistent";
                if(u16(get(0x20))/100.0>cells*5.5||u16(get(0x22))/100.0<cells*.5)return "Pack thresholds do not match this battery";
                return "";
            }catch(IllegalArgumentException e){return "This firmware did not return the required classic JBD parameters";}
        }
        public byte[] change(Field f,String text){
            String problem=layoutProblem();if(!problem.isEmpty())throw new IllegalArgumentException(problem);
            byte[] old=get(f.register);if(!f.plausible(old))throw new IllegalArgumentException("This parameter is unavailable or uses a different encoding");
            byte[] value=f.encode(text,old);Map<Integer,byte[]> next=raw();next.put(f.register,value);
            if(f.group.equals("Voltage protection")){
                ordered(next,new int[]{0x26,0x27,0x25,0x24},true,"Cell release thresholds must sit between under/overvoltage protection");
                ordered(next,new int[]{0x22,0x23,0x21,0x20},true,"Pack release thresholds must sit between under/overvoltage protection");
            }
            if(f.group.equals("Temperature settings")){
                ordered(next,new int[]{0x1a,0x1b,0x19,0x18},true,"Charge temperature release thresholds must sit between cold/hot protection");
                ordered(next,new int[]{0x1e,0x1f,0x1d,0x1c},true,"Discharge temperature release thresholds must sit between cold/hot protection");
            }
            if(f.group.equals("Capacity voltage"))ordered(next,new int[]{0x13,0x35,0x34,0x33,0x32,0x12},false,"SOC voltages must increase from 0% to 100%");
            if(f.id.equals("balance_start")&&(u16(value)<u16(get(0x26))||u16(value)>u16(get(0x24))))throw new IllegalArgumentException("Balance start must be within the cell protection voltage range");
            if(f.id.equals("charge_balance")&&(u16(value)&8)!=0&&(u16(value)&4)==0)throw new IllegalArgumentException("Enable balancing first");
            return value;
        }
        private static void ordered(Map<Integer,byte[]> values,int[] regs,boolean strict,String message){int previous=-1;for(int r:regs){int n;try{n=u16(values.get(r));}catch(IllegalArgumentException e){throw new IllegalArgumentException("Related parameters are unavailable; read them again before editing");}if(previous>=0&&(strict?n<=previous:n<previous))throw new IllegalArgumentException(message);previous=n;}}
    }
    /** Settings responses never enter the telemetry assembler. */
    public static final class Assembler {
        private final byte[] buffer=new byte[512];private int size;
        public void reset(){size=0;}
        private void drop(int n){System.arraycopy(buffer,n,buffer,0,size-n);size-=n;}
        public List<JbdProtocol.Frame> feed(byte[] chunk){List<JbdProtocol.Frame> out=new ArrayList<>();if(chunk==null)return out;for(byte b:chunk){if(size==buffer.length)size=0;buffer[size++]=b;while(size>0){if((buffer[0]&255)!=0xdd){drop(1);continue;}if(size<4)break;int reg=buffer[1]&255,n=buffer[3]&255;if((reg!=0&&reg!=1&&reg!=6&&!readable(reg))||n>64){drop(1);continue;}int length=n+7;if(size<length)break;int sum=0;for(int i=2;i<4+n;i++)sum+=buffer[i]&255;int crc=(-sum)&65535;if((buffer[length-1]&255)!=0x77||crc!=((buffer[4+n]&255)<<8|(buffer[5+n]&255))){drop(1);continue;}out.add(new JbdProtocol.Frame(reg,buffer[2]&255,Arrays.copyOfRange(buffer,4,4+n)));drop(length);}}return out;}
    }
}
