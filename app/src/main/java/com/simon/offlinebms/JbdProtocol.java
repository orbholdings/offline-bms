package com.simon.offlinebms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Original, dependency-free implementation of the published JBD read protocol. */
public final class JbdProtocol {
    private JbdProtocol() {}
    public static byte[] readCommand(int register) {
        if (register != 3 && register != 4 && register != 5) throw new IllegalArgumentException("Only telemetry registers are allowed");
        int crc = (-register) & 0xffff;
        return new byte[]{(byte)0xdd, (byte)0xa5, (byte)register, 0, (byte)(crc >> 8), (byte)crc, 0x77};
    }
    public static final class Frame {
        public final int register, status;
        public final byte[] payload;
        Frame(int register, int status, byte[] payload) { this.register=register; this.status=status; this.payload=payload; }
    }
    public static final class Assembler {
        private final byte[] buffer = new byte[1024];
        private int size;
        public int rejected;
        public void reset() { size=0; rejected=0; }
        public List<Frame> feed(byte[] chunk) {
            List<Frame> frames=new ArrayList<>();
            if (chunk == null) return frames;
            for (byte b : chunk) {
                if (size==buffer.length) { size=0; rejected++; }
                buffer[size++]=b;
                drain(frames);
            }
            return frames;
        }
        private void drop(int n) { System.arraycopy(buffer,n,buffer,0,size-n); size-=n; }
        private void drain(List<Frame> out) {
            while (size>0) {
                if ((buffer[0]&255)!=0xdd) { drop(1); continue; }
                if (size<4) return;
                int reg=buffer[1]&255;
                if (reg!=3 && reg!=4 && reg!=5 && reg!=6) { drop(1); continue; }
                int n=buffer[3]&255, full=n+7;
                if(n>(reg==4?64:128)) { rejected++; drop(1); continue; }
                if (size<full) return;
                int sum=0;
                for (int i=2;i<4+n;i++) sum+=(buffer[i]&255);
                int expected=(-sum)&65535;
                int got=((buffer[4+n]&255)<<8)|(buffer[5+n]&255);
                if ((buffer[6+n]&255)!=0x77 || expected!=got) { rejected++; drop(1); continue; }
                out.add(new Frame(reg,buffer[2]&255,Arrays.copyOfRange(buffer,4,4+n)));
                drop(full);
            }
        }
    }
    public static final class Basic {
        public double voltage, current, remainingAh, capacityAh;
        public int soc, cycles, cellCount, protection, fet, version;
        public long balanceMask;
        public double[] temperatures = new double[0];
        public String[] temperatureLabels = new String[0];
        public String productionDate="Unknown";
        public boolean extendedScale;
        public String vendor="JBD",firmware="";
        public long vendorAlarmFlags;
        public boolean balancingActive;
        public double power() { return voltage*current; }
        public boolean chargeEnabled() { return (fet&1)!=0; }
        public boolean dischargeEnabled() { return (fet&2)!=0; }
        public String versionText() { return firmware.isEmpty()?(version>>4)+"."+(version&15):firmware; }
    }
    private static int u16(byte[] p,int i) { return ((p[i]&255)<<8)|(p[i+1]&255); }
    public static Basic parseBasic(byte[] p) {
        if (p==null || p.length<23) throw new IllegalArgumentException("Basic response is too short");
        int ntc=p[22]&255;
        if (ntc>16 || p.length<23+ntc*2) throw new IllegalArgumentException("Temperature data is incomplete");
        int cells=p[21]&255, soc=p[19]&255;
        if (cells<1 || cells>32 || soc>100) throw new IllegalArgumentException("Implausible cell count or charge percentage");
        Basic b=new Basic();
        int tail=23+ntc*2;
        b.extendedScale=p.length>tail && (p[tail]&255)==0x88;
        double divisor=b.extendedScale?10.0:100.0;
        b.voltage=u16(p,0)/100.0;
        b.current=((short)u16(p,2))/divisor;
        b.remainingAh=u16(p,4)/divisor;
        b.capacityAh=u16(p,6)/divisor;
        b.cycles=u16(p,8);
        int date=u16(p,10), day=date&31, month=(date>>5)&15, year=2000+(date>>9);
        if (day>=1 && day<=31 && month>=1 && month<=12) b.productionDate=String.format(java.util.Locale.ROOT,"%04d-%02d-%02d",year,month,day);
        b.balanceMask=u16(p,12)|((long)u16(p,14)<<16);
        b.protection=u16(p,16); b.version=p[18]&255; b.soc=soc; b.fet=p[20]&255; b.cellCount=cells;
        b.temperatures=new double[ntc];
        for(int i=0;i<ntc;i++) b.temperatures[i]=(u16(p,23+i*2)-2731)/10.0;
        return b;
    }
    public static double[] parseCells(byte[] p,int expectedCount) {
        if (p==null || p.length==0 || p.length%2!=0 || p.length>64) throw new IllegalArgumentException("Invalid cell voltage response");
        if(expectedCount>0 && p.length/2!=expectedCount) throw new IllegalArgumentException("Cell count does not match basic data");
        double[] cells=new double[p.length/2];
        for(int i=0;i<cells.length;i++) {
            int mv=u16(p,i*2);
            if(mv>6500) throw new IllegalArgumentException("Implausible cell voltage");
            cells[i]=mv/1000.0;
        }
        return cells;
    }
    public static String[] protectionLabels(int flags) {
        String[] labels={"Cell overvoltage","Cell undervoltage","Pack overvoltage","Pack undervoltage","Charging temperature too high","Charging temperature too low","Discharging temperature too high","Discharging temperature too low","Charging overcurrent","Discharging overcurrent","Short circuit","BMS IC error","MOSFET software lock","Protection bit 13","Protection bit 14","Protection bit 15"};
        List<String> active=new ArrayList<>();
        for(int i=0;i<16;i++) if((flags&(1<<i))!=0) active.add(labels[i]);
        return active.toArray(new String[0]);
    }
    public static String parseModel(byte[] payload) {
        int offset=payload.length>1 && (payload[0]&255)==payload.length-1?1:0;
        return new String(payload,offset,payload.length-offset,java.nio.charset.Charset.forName("GB18030")).replaceAll("[\\p{Cntrl}]","").trim();
    }
    public static String hex(byte[] a) {
        StringBuilder s=new StringBuilder();
        for(byte b:a) { if(s.length()>0)s.append(' ');s.append(String.format(java.util.Locale.ROOT,"%02X",b&255)); }
        return s.toString();
    }
}
