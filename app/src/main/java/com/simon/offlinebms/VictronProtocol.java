package com.simon.offlinebms;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.util.*;

/** Victron's published Instant Readout; caller supplies its own device key. */
public final class VictronProtocol {
    public static final class Data {
        public int type,model,counter,state=-1;public long alarms;public boolean alarmsKnown=true;
        public final LinkedHashMap<String,Double> values=new LinkedHashMap<>();
        public final LinkedHashMap<String,String> units=new LinkedHashMap<>();
        public final LinkedHashMap<String,String> details=new LinkedHashMap<>();
        void add(String key,double value,String unit){if(Double.isFinite(value)){values.put(key,value);units.put(key,unit);}}
        public String typeName(){return VictronProtocol.typeName(type);}
    }
    public static String typeName(int type){switch(type){case 1:return "Solar charger";case 2:return "Battery monitor / sense";case 3:return "Inverter";case 4:return "DC/DC converter";case 5:return "SmartLithium";case 6:return "Inverter RS";case 8:return "AC charger";case 9:return "Smart BatteryProtect";case 10:return "Lynx Smart BMS";case 11:return "Multi RS";case 12:return "VE.Bus";case 13:return "DC energy meter";case 15:return "Orion XS";default:return "Unsupported record 0x"+Integer.toHexString(type);}}
    public static String normalizeAddress(String input){
        String s=input==null?"":input.trim();
        if(!s.matches("(?i)[0-9a-f]{12}")&&!s.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")&&!s.matches("(?i)[0-9a-f]{2}(-[0-9a-f]{2}){5}"))
            throw new IllegalArgumentException("Enter the 12-digit Bluetooth address, with or without colons.");
        s=s.replace(":","").replace("-","").toUpperCase(Locale.ROOT);
        StringBuilder mac=new StringBuilder();for(int i=0;i<12;i+=2){if(i>0)mac.append(':');mac.append(s,i,i+2);}return mac.toString();
    }
    public static byte[] key(String hex){String s=hex.replaceAll("\\s","");if(!s.matches("(?i)[0-9a-f]{32}"))throw new IllegalArgumentException("Use the 32-character Instant Readout encryption key, not the Bluetooth PIN");byte[] k=new byte[16];for(int i=0;i<16;i++)k[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return k;}
    public static Data decode(byte[] advertisement,byte[] key)throws Exception{
        if(advertisement==null||advertisement.length<9||(advertisement[0]&255)!=0x10)throw new IllegalArgumentException("Not a Victron Instant Readout advertisement");
        if(key==null||key.length!=16||(advertisement[7]&255)!=(key[0]&255))throw new IllegalArgumentException("Instant Readout key does not match this device");
        byte[] nonce=new byte[16];nonce[0]=advertisement[5];nonce[1]=advertisement[6];Cipher c=Cipher.getInstance("AES/CTR/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(nonce));byte[] plain=c.doFinal(Arrays.copyOfRange(advertisement,8,advertisement.length));Data d=parse(plain,advertisement[4]&255);d.model=(advertisement[2]&255)|((advertisement[3]&255)<<8);d.counter=(advertisement[5]&255)|((advertisement[6]&255)<<8);return d;
    }
    static long bits(byte[] data,int start,int length){if(start<0||length<1||length>32||start+length>data.length*8)throw new IllegalArgumentException("Truncated Victron record");long value=0;for(int i=0;i<length;i++)value|=(long)((data[(start+i)/8]>>((start+i)%8))&1)<<i;return value;}
    private static double value(byte[] p,int start,int width,boolean signed,long missing,double scale){long raw=bits(p,start,width);if(raw==missing)return Double.NaN;if(signed&&(raw&(1L<<(width-1)))!=0)raw-=1L<<width;return raw*scale;}
    public static Data parse(byte[] p,int type){Data d=new Data();d.type=type;switch(type){
        case 1:d.state=(int)bits(p,0,8);d.alarmsKnown=bits(p,8,8)!=255;d.alarms=d.alarmsKnown?bits(p,8,8):0;d.add("Battery voltage",value(p,16,16,true,0x7fff,.01),"V");d.add("Battery current",value(p,32,16,true,0x7fff,.1),"A");d.add("Yield today",value(p,48,16,false,0xffff,.01),"kWh");d.add("Solar power",value(p,64,16,false,0xffff,1),"W");d.add("Load current",value(p,80,9,false,0x1ff,.1),"A");break;
        case 2:case 13:d.add(type==2?"Time to go":"Monitor mode",value(p,0,16,type==13,type==2?0xffff:-1,1),type==2?"min":"");d.add("Battery voltage",value(p,16,16,true,0x7fff,.01),"V");d.alarms=bits(p,32,16);int aux=(int)bits(p,64,2);double av=value(p,48,16,false,0xffff,.01);if(aux==2)d.add("Temperature",av-273.15,"°C");else if(aux==0)d.add("Aux voltage",value(p,48,16,true,0x7fff,.01),"V");else if(aux==1)d.add("Midpoint voltage",av,"V");d.add("Battery current",value(p,66,22,true,0x3fffff,.001),"A");if(type==2){d.add("Consumed capacity",value(p,88,20,false,0xfffff,-.1),"Ah");double soc=value(p,108,10,false,0x3ff,.1);if(soc>100)throw new IllegalArgumentException("Invalid Victron SOC");d.add("State of charge",soc,"%");}break;
        case 3:d.state=(int)bits(p,0,8);d.alarms=bits(p,8,16);d.add("Battery voltage",value(p,24,16,true,0x7fff,.01),"V");d.add("Apparent AC power",value(p,40,16,false,0xffff,1),"VA");d.add("AC voltage",value(p,56,15,false,0x7fff,.01),"V");d.add("AC current",value(p,71,11,false,0x7ff,.1),"A");break;
        case 4:d.state=(int)bits(p,0,8);d.alarmsKnown=bits(p,8,8)!=255;d.alarms=d.alarmsKnown?bits(p,8,8):0;d.add("Input voltage",value(p,16,16,false,0xffff,.01),"V");d.add("Output voltage",value(p,32,16,true,0x7fff,.01),"V");d.add("Off reason bitmask",bits(p,48,32),"");break;
        case 5:
            long bmsFlags=bits(p,0,32);d.alarms=bits(p,32,16);
            if(bmsFlags!=0)d.details.put("BMS status flags","0x"+Long.toHexString(bmsFlags));
            for(int cell=0;cell<8;cell++){
                long raw=bits(p,48+cell*7,7);String label="Cell "+(cell+1);
                if(raw==0)d.details.put(label,"Below 2.61 V");
                else if(raw==126)d.details.put(label,"Above 3.85 V");
                else if(raw!=127)d.add(label+" voltage",2.60+raw*.01,"V");
            }
            d.add("Battery voltage",value(p,104,12,false,0xfff,.01),"V");
            int balancer=(int)bits(p,116,4);
            if(balancer!=15)d.details.put("Balancer",balancer==0?"Unknown":balancer==1?"Balanced":balancer==2?"Balancing":balancer==3?"Imbalance":"Code "+balancer);
            d.add("Temperature",value(p,120,7,false,0x7f,1)-40,"°C");break;
        case 6:d.state=(int)bits(p,0,8);d.alarmsKnown=bits(p,8,8)!=255;d.alarms=d.alarmsKnown?bits(p,8,8):0;d.add("Battery voltage",value(p,16,16,true,0x7fff,.01),"V");d.add("Battery current",value(p,32,16,true,0x7fff,.1),"A");d.add("Solar power",value(p,48,16,false,0xffff,1),"W");d.add("Yield today",value(p,64,16,false,0xffff,.01),"kWh");d.add("AC output power",value(p,80,16,true,0x7fff,1),"W");break;
        case 8:
            // Published AC Charger layout: offsets below exclude its 32-bit record header.
            int state=(int)bits(p,0,8);d.state=state==255?-1:state;
            long error=bits(p,8,8);d.alarmsKnown=error!=255;d.alarms=d.alarmsKnown?error:0;
            for(int output=1;output<=3;output++){
                int offset=16+(output-1)*24;
                double volts=value(p,offset,13,false,0x1fff,.01),amps=value(p,offset+13,11,false,0x7ff,.1);
                d.add("Output "+output+" voltage",volts,"V");d.add("Output "+output+" current",amps,"A");
                d.add("Output "+output+" power",volts*amps,"W");
            }
            d.add("Temperature",value(p,88,7,false,0x7f,1)-40,"°C");
            d.add("AC input current",value(p,95,9,false,0x1ff,.1),"A");break;
        case 9:
            // BatteryProtect's published table uses offsets without the nonce header.
            int protectState=(int)bits(p,0,8),outputState=(int)bits(p,8,8);long protectError=bits(p,16,8);
            long alarms=bits(p,24,16),warnings=bits(p,40,16);
            d.state=protectState==255?-1:protectState;d.alarmsKnown=protectError!=255;
            d.alarms=(d.alarmsKnown?protectError:0)|alarms|warnings;
            if(outputState!=255)d.details.put("Output state","Code "+outputState);
            if(alarms!=0)d.details.put("Alarm flags","0x"+Long.toHexString(alarms));
            if(warnings!=0)d.details.put("Warning flags","0x"+Long.toHexString(warnings));
            d.add("Input voltage",value(p,56,16,true,0x7fff,.01),"V");
            d.add("Output voltage",value(p,72,16,false,0xffff,.01),"V");
            long protectOff=bits(p,88,32);if(protectOff!=0)d.details.put("Off reason","0x"+Long.toHexString(protectOff));break;
        case 10:d.alarms=bits(p,0,8)|bits(p,72,18);d.add("Time to go",value(p,8,16,false,0xffff,1),"min");d.add("Battery voltage",value(p,24,16,true,0x7fff,.01),"V");d.add("Battery current",value(p,40,16,true,0x7fff,.1),"A");d.add("State of charge",value(p,90,10,false,0x3ff,.1),"%");d.add("Consumed capacity",value(p,100,20,false,0xfffff,-.1),"Ah");d.add("Temperature",value(p,120,7,false,0x7f,1)-40,"°C");break;
        case 11:case 12:d.state=(int)bits(p,0,8);d.alarmsKnown=bits(p,8,8)!=255;d.alarms=d.alarmsKnown?bits(p,8,8):0;d.add("Battery current",value(p,16,16,true,0x7fff,.1),"A");d.add("Battery voltage",value(p,32,14,false,0x3fff,.01),"V");d.add("Active AC input",value(p,46,2,false,3,1),"");if(type==11){d.add("AC input power",value(p,48,16,true,0x7fff,1),"W");d.add("AC output power",value(p,64,16,true,0x7fff,1),"W");d.add("Solar power",value(p,80,16,false,0xffff,1),"W");d.add("Yield today",value(p,96,16,false,0xffff,.01),"kWh");}else{d.add("AC input power",value(p,48,19,true,0x3ffff,1),"W");d.add("AC output power",value(p,67,19,true,0x3ffff,1),"W");long alarm=bits(p,86,2);if(alarm!=3)d.alarms|=alarm<<8;d.add("Temperature",value(p,88,7,false,0x7f,1)-40,"°C");d.add("State of charge",value(p,95,7,false,0x7f,1),"%");}break;
        case 15:
            int orionState=(int)bits(p,0,8);long orionError=bits(p,8,8);
            d.state=orionState==255?-1:orionState;d.alarmsKnown=orionError!=255;d.alarms=d.alarmsKnown?orionError:0;
            double outputV=value(p,16,16,true,0x7fff,.01),outputI=value(p,32,16,true,0x7fff,.1);
            double inputV=value(p,48,16,false,0xffff,.01),inputI=value(p,64,16,false,0xffff,.1);
            d.add("Output voltage",outputV,"V");d.add("Output current",outputI,"A");d.add("Output power",outputV*outputI,"W");
            d.add("Input voltage",inputV,"V");d.add("Input current",inputI,"A");d.add("Input power",inputV*inputI,"W");
            long orionOff=bits(p,80,32);if(orionOff!=0)d.details.put("Off reason","0x"+Long.toHexString(orionOff));break;
        default:throw new IllegalArgumentException(type==7?"GX Bluetooth format is provisional and is not supported; GX/Cerbo network access needs a separate interface":typeName(type)+" is not implemented yet");
    }
        for(Map.Entry<String,Double> e:d.values.entrySet()){String u=d.units.get(e.getKey());if(u.equals("V")&&(e.getValue()<0||e.getValue()>655.34))throw new IllegalArgumentException("Invalid Victron voltage");if(u.equals("°C")&&(e.getValue()<-100||e.getValue()>200))throw new IllegalArgumentException("Invalid Victron temperature");if(u.equals("%")&&(e.getValue()<0||e.getValue()>100))throw new IllegalArgumentException("Invalid Victron charge percentage");}
        if(d.values.containsKey("Battery voltage")&&d.values.containsKey("Battery current"))d.add("Battery power",d.values.get("Battery voltage")*d.values.get("Battery current"),"W");return d;
    }
}
