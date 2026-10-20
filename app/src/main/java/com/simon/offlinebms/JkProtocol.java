package com.simon.offlinebms;
import java.util.*;
/** Independent JK02 telemetry with a narrowly scoped, verified balancing control. */
public final class JkProtocol {
    public static byte[] readCommand(int command){if(command!=0x96&&command!=0x97)throw new IllegalArgumentException("Only JK measurement and identity reads are allowed");byte[] out=new byte[20];out[0]=(byte)0xaa;out[1]=0x55;out[2]=(byte)0x90;out[3]=(byte)0xeb;out[4]=(byte)command;int sum=0;for(int i=0;i<19;i++)sum+=out[i]&255;out[19]=(byte)sum;return out;}
    public static byte[] balancingCommand(boolean enabled){byte[] out=new byte[20];out[0]=(byte)0xaa;out[1]=0x55;out[2]=(byte)0x90;out[3]=(byte)0xeb;out[4]=0x1f;out[5]=4;out[6]=(byte)(enabled?1:0);int sum=0;for(int i=0;i<19;i++)sum+=out[i]&255;out[19]=(byte)sum;return out;}
    public static final class BalanceSettings {
        public final boolean enabled;
        public final double startVoltage,differenceMv;
        BalanceSettings(boolean enabled,double startVoltage,double differenceMv){this.enabled=enabled;this.startVoltage=startVoltage;this.differenceMv=differenceMv;}
    }
    public static BalanceSettings balanceSettings(byte[] frame,String variant,JbdProtocol.Basic live){
        if(!variant.equals("JK02_24S")&&!variant.equals("JK02_32S"))throw new IllegalArgumentException("Read a supported JK02 telemetry layout first");
        if(frame==null||frame.length!=300||(frame[0]&255)!=0x55||(frame[1]&255)!=0xaa||(frame[2]&255)!=0xeb||(frame[3]&255)!=0x90||(frame[4]&255)!=1)throw new IllegalArgumentException("Not a JK02 settings frame");
        int sum=0;for(int i=0;i<299;i++)sum+=frame[i]&255;if((sum&255)!=(frame[299]&255))throw new IllegalArgumentException("Invalid JK settings checksum");
        if(live==null||live.cellCount<1||live.cellCount>(variant.equals("JK02_32S")?32:24)||u32(frame,114)!=live.cellCount)throw new IllegalArgumentException("JK settings cell count does not match live readings");
        long enabled=u32(frame,126);if(enabled>1||u32(frame,118)>1||u32(frame,122)>1)throw new IllegalArgumentException("Unsupported JK settings switch encoding");
        double capacity=u32(frame,130)/1000.0,start=u32(frame,138)/1000.0,difference=u32(frame,26);
        if(live.capacityAh<=0||Math.abs(capacity-live.capacityAh)>Math.max(.1,live.capacityAh*.05)||start<=0||start>6.5||difference>1000)throw new IllegalArgumentException("JK settings do not match the supported layout");
        return new BalanceSettings(enabled==1,start,difference);
    }
    public static final class Assembler{
        private final byte[] buffer=new byte[640];private int size;public int rejected;
        public void reset(){size=0;rejected=0;}
        private void drop(int n){System.arraycopy(buffer,n,buffer,0,size-n);size-=n;}
        public List<byte[]> feed(byte[] chunk){List<byte[]> frames=new ArrayList<>();if(chunk==null)return frames;for(byte b:chunk){if(size==buffer.length){size=0;rejected++;}buffer[size++]=b;while(size>=4){if((buffer[0]&255)!=0x55||(buffer[1]&255)!=0xaa||(buffer[2]&255)!=0xeb||(buffer[3]&255)!=0x90){drop(1);continue;}if(size<300)break;int sum=0;for(int i=0;i<299;i++)sum+=buffer[i]&255;if((sum&255)!=(buffer[299]&255)){drop(1);rejected++;continue;}frames.add(Arrays.copyOf(buffer,300));drop(300);}}return frames;}
    }
    public static final class Sample {public final JbdProtocol.Basic basic;public final double[] cells;Sample(JbdProtocol.Basic b,double[] c){basic=b;cells=c;}}
    public static final class VariantMismatchException extends IllegalArgumentException {
        public final String detectedVariant;
        VariantMismatchException(String selected,String detected){super("Selected "+selected+" does not match these readings; choose "+detected+" or Auto");detectedVariant=detected;}
    }
    private static int u16(byte[] f,int n){return (f[n]&255)|((f[n+1]&255)<<8);}
    private static long u32(byte[] f,int n){return (long)u16(f,n)|((long)u16(f,n+2)<<16);}
    public static Sample parse(byte[] f,String variant){
        if(f==null||f.length!=300||(f[4]&255)!=2)throw new IllegalArgumentException("Not a JK cell-info frame");
        if("JK02_24S".equals(variant))return selectedLayout(f,false);
        if("JK02_32S".equals(variant))return selectedLayout(f,true);
        Sample a=null,b=null;String error24="",error32="";
        try{a=layout(f,false);}catch(IllegalArgumentException e){error24=e.getMessage();}
        try{b=layout(f,true);}catch(IllegalArgumentException e){error32=e.getMessage();}
        if(a!=null&&b==null)return a;if(b!=null&&a==null)return b;
        throw new IllegalArgumentException(a==null?"JK02_24S: "+error24+"; JK02_32S: "+error32:"Ambiguous JK layout; choose JK02 24S or 32S in the profile");
    }
    private static Sample selectedLayout(byte[] frame,boolean newer){
        try{return layout(frame,newer);}catch(IllegalArgumentException original){
            boolean alternativeValid=false;try{layout(frame,!newer);alternativeValid=true;}catch(IllegalArgumentException ignored){}
            if(alternativeValid)throw new VariantMismatchException(newer?"JK02_32S":"JK02_24S",newer?"JK02_24S":"JK02_32S");
            throw original;
        }
    }
    private static Sample layout(byte[] f,boolean newer){int slots=newer?32:24,offset=newer?32:0;List<Double> cells=new ArrayList<>();double sum=0;boolean ended=false;for(int i=0;i<slots;i++){int mv=u16(f,6+i*2);if(mv==0){ended=true;continue;}if(ended)throw new IllegalArgumentException("Cell "+(i+1)+" follows an unused channel");if(mv>6500)throw new IllegalArgumentException("Cell "+(i+1)+" voltage outside supported range: "+mv+" mV");cells.add(mv/1000.0);sum+=mv/1000.0;}
        JbdProtocol.Basic b=new JbdProtocol.Basic();b.vendor="JK";b.firmware="JK02 "+(newer?"32S":"24S");b.cellCount=cells.size();b.voltage=u32(f,118+offset)/1000.0;b.current=(int)u32(f,126+offset)/1000.0;b.soc=f[141+offset]&255;b.remainingAh=u32(f,142+offset)/1000.0;b.capacityAh=u32(f,146+offset)/1000.0;b.cycles=(int)Math.min(Integer.MAX_VALUE,u32(f,150+offset));int charge=f[166+offset]&255,discharge=f[167+offset]&255;b.fet=(charge!=0?1:0)|(discharge!=0?2:0);
        b.vendorAlarmFlags=newer?u32(f,134+offset):u16(f,136);b.protection=b.vendorAlarmFlags==0?0:1;b.balancingActive=f[140+offset]!=0;
        // Absent-sensor bits are independent of the signed temperature words. A missing
        // sensor may carry a sentinel; it must neither reject telemetry nor invent 0 C.
        int absent=f[182+offset]&255;int[] tempOffsets={newer?144:134,130+offset,132+offset};String[] names={"MOS","T1","T2"};
        List<Double> temperatures=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(int i=0;i<tempOffsets.length;i++){
            if((absent&(1<<i))!=0)continue;
            int rawTemperature=(short)u16(f,tempOffsets[i]);
            // Captured JK_B2A8S20P firmware 11.55 uses 0xF830 (-200 C)
            // when temperature is unavailable, even with low absent bits clear.
            // Omit this exact marker; continue validating every real temperature.
            if(rawTemperature==-2000)continue;
            double temp=rawTemperature/10.0;
            if(temp<-100||temp>200)throw new IllegalArgumentException(names[i]+" temperature outside supported range: "+temp+" C");
            temperatures.add(temp);labels.add(names[i]);
        }
        b.temperatures=new double[temperatures.size()];for(int i=0;i<b.temperatures.length;i++)b.temperatures[i]=temperatures.get(i);b.temperatureLabels=labels.toArray(new String[0]);
        if(b.cellCount<1)throw new IllegalArgumentException("No cell voltages");
        if(b.voltage<=0||b.voltage>650)throw new IllegalArgumentException("Pack voltage outside supported range: "+b.voltage+" V");
        if(Math.abs(b.voltage-sum)>Math.max(.5,sum*.05))throw new IllegalArgumentException("Pack voltage "+b.voltage+" V disagrees with cell sum "+String.format(Locale.ROOT,"%.3f",sum)+" V");
        if(Math.abs(b.current)>4000)throw new IllegalArgumentException("Current outside supported range: "+b.current+" A");
        if(b.soc>100)throw new IllegalArgumentException("State of charge outside supported range: "+b.soc);
        if(b.capacityAh<=0||b.capacityAh>100000)throw new IllegalArgumentException("Capacity outside supported range: "+b.capacityAh+" Ah");
        if(b.remainingAh>b.capacityAh*1.05)throw new IllegalArgumentException("Remaining capacity exceeds nominal capacity");
        if(charge>1||discharge>1)throw new IllegalArgumentException("Unexpected MOSFET states: "+charge+"/"+discharge);
        double[] values=new double[cells.size()];for(int i=0;i<values.length;i++)values[i]=cells.get(i);return new Sample(b,values);
    }
    public static String model(byte[] f){if(f.length<38||(f[4]&255)!=3)return "JK BMS";return ascii(f,6,16)+" • firmware "+ascii(f,30,8);}
    private static String ascii(byte[] f,int start,int n){StringBuilder b=new StringBuilder();for(int i=start;i<start+n&&f[i]!=0;i++)if(f[i]>=32&&f[i]<127)b.append((char)f[i]);return b.toString();}
    public static String[] alarmLabels(long flags){if(flags==0)return new String[0];return new String[]{"JK alarm flags: 0x"+Long.toHexString(flags).toUpperCase(Locale.ROOT)+" (vendor bitmask)"};}
}
