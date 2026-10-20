package com.simon.offlinebms;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Checks wire examples and malformed input, without any Android or third-party dependency. */
public final class ProtocolTests {
    static int passed;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
    static void near(double a,double b,String label){check(Math.abs(a-b)<0.000001,label+" expected "+b+" got "+a);}
    static byte[] hex(String s){String[] words=s.trim().split("\\s+");byte[] a=new byte[words.length];for(int i=0;i<a.length;i++)a[i]=(byte)Integer.parseInt(words[i],16);return a;}
    static byte[] response(int reg,int status,byte[] p){byte[] f=new byte[p.length+7];f[0]=(byte)0xdd;f[1]=(byte)reg;f[2]=(byte)status;f[3]=(byte)p.length;System.arraycopy(p,0,f,4,p.length);int sum=status+p.length;for(byte v:p)sum+=v&255;int cs=(-sum)&65535;f[p.length+4]=(byte)(cs>>8);f[p.length+5]=(byte)cs;f[p.length+6]=0x77;return f;}
    static void u16(byte[] a,int pos,int value){a[pos]=(byte)(value>>8);a[pos+1]=(byte)value;}
    static void invalid(Runnable r,String label){boolean threw=false;try{r.run();}catch(IllegalArgumentException e){threw=true;}check(threw,label);}
    public static void main(String[] args){
        check(JbdProtocol.hex(JbdProtocol.readCommand(3)).equals("DD A5 03 00 FF FD 77"),"Published basic read command");
        check(JbdProtocol.hex(JbdProtocol.readCommand(4)).equals("DD A5 04 00 FF FC 77"),"Cell read command");
        invalid(()->JbdProtocol.readCommand(0x00),"Factory writes not available");
        invalid(()->JbdProtocol.readCommand(0xfb),"MOSFET control not available");
        byte[] sample=hex("dd 03 00 1b 05 34 00 00 19 ab 27 10 00 00 2a 75 00 00 00 00 00 00 20 42 03 04 02 09 7f 0b a3 fc 71 77");
        JbdProtocol.Assembler a=new JbdProtocol.Assembler();
        check(a.feed(Arrays.copyOfRange(sample,0,20)).isEmpty(),"First MTU chunk waits for full frame");
        List<JbdProtocol.Frame> list=a.feed(Arrays.copyOfRange(sample,20,sample.length));check(list.size()==1,"Published frame reassembles across BLE chunks");
        JbdProtocol.Basic published=JbdProtocol.parseBasic(list.get(0).payload);
        near(published.voltage,13.32,"Published voltage");near(published.remainingAh,65.71,"Published remaining capacity");near(published.capacityAh,100,"Published capacity");check(published.soc==66,"SOC percentage");check(published.cellCount==4,"Cell count");near(published.temperatures[0],-30,"Negative temperature conversion");near(published.temperatures[1],24.8,"Temperature conversion");check(published.chargeEnabled()&&published.dischargeEnabled(),"FET flags");
        byte[] data=new byte[27];u16(data,0,5280);u16(data,2,-1234);u16(data,4,50000);u16(data,6,65535);u16(data,12,0x8001);u16(data,14,0x8000);u16(data,16,0x0401);data[18]=0x24;data[19]=70;data[20]=2;data[21]=16;data[22]=2;u16(data,23,2981);u16(data,25,3031);
        JbdProtocol.Basic b=JbdProtocol.parseBasic(data);near(b.current,-12.34,"Signed discharge current");near(b.remainingAh,500,"Unsigned capacity wrap");near(b.capacityAh,655.35,"Maximum unsigned capacity");near(b.power(),-651.552,"Signed power");check(b.balanceMask==0x80008001L,"High cells balancing bit mask");check(b.versionText().equals("2.4"),"Firmware nibbles");near(b.temperatures[0],25,"25C conversion");check(!b.chargeEnabled()&&b.dischargeEnabled(),"Separate FET states");check(JbdProtocol.protectionLabels(b.protection).length==2,"Multiple protection flags");check(JbdProtocol.protectionLabels(1<<15).length==1,"Unknown protection preserved");
        data[2]=0x04;data[3]=(byte)0xd2;near(JbdProtocol.parseBasic(data).current,12.34,"Charging sign");
        byte[] extended=Arrays.copyOf(data,28);extended[27]=(byte)0x88;near(JbdProtocol.parseBasic(extended).current,123.4,"Extended firmware current sentinel");near(JbdProtocol.parseBasic(extended).remainingAh,5000,"Extended capacity scale");
        double[] cells=JbdProtocol.parseCells(hex("0c f8 0c fa 0c f3 0c f9"),4);near(cells[0],3.32,"Cell voltage scale");near(cells[2],3.315,"Millivolt precision");
        invalid(()->JbdProtocol.parseBasic(new byte[5]),"Short basic frame rejected");byte[] shortTemps=Arrays.copyOf(data,24);invalid(()->JbdProtocol.parseBasic(shortTemps),"Missing temperature bytes rejected");byte[] invalidSoc=data.clone();invalidSoc[19]=(byte)101;invalid(()->JbdProtocol.parseBasic(invalidSoc),"Invalid SOC rejected");invalid(()->JbdProtocol.parseCells(new byte[]{1,2,3},0),"Odd cell payload rejected");invalid(()->JbdProtocol.parseCells(new byte[]{1,2},16),"Cell count mismatch rejected");invalid(()->JbdProtocol.parseCells(hex("ff ff"),1),"Invalid cell measurement rejected");
        a.reset();byte[] corrupt=sample.clone();corrupt[7]^=1;check(a.feed(corrupt).isEmpty(),"Bad checksum rejected");check(a.feed(sample).size()==1,"Parser recovers after corrupt frame");
        a.reset();byte[] badEnd=sample.clone();badEnd[badEnd.length-1]=0;check(a.feed(badEnd).isEmpty(),"Bad footer rejected");check(a.feed(sample).size()==1,"Recovery after bad footer");
        a.reset();byte[] combined=new byte[sample.length*2+3];combined[0]=7;combined[1]=(byte)0xaa;combined[2]=9;System.arraycopy(sample,0,combined,3,sample.length);System.arraycopy(sample,0,combined,3+sample.length,sample.length);check(a.feed(combined).size()==2,"Noise and consecutive frames");
        a.reset();check(a.feed(response(3,0x80,new byte[0])).get(0).status==0x80,"Checksum includes error status");
        a.reset();a.feed(hex("dd 03 00 ff"));check(a.feed(sample).size()==1,"Corrupt length cannot block following frame");
        a.reset();for(byte v:sample){List<JbdProtocol.Frame> frames=a.feed(new byte[]{v});if(!frames.isEmpty())check(frames.size()==1,"Single-byte fragmentation");}
        byte[] model="SP24S004L24S200A".getBytes(StandardCharsets.US_ASCII);check(JbdProtocol.parseModel(model).equals("SP24S004L24S200A"),"Model without prefix");byte[] prefixed=new byte[model.length+1];prefixed[0]=(byte)model.length;System.arraycopy(model,0,prefixed,1,model.length);check(JbdProtocol.parseModel(prefixed).equals("SP24S004L24S200A"),"Length-prefixed model");
        Random random=new Random(24);a.reset();for(int i=0;i<10000;i++){byte[] noise=new byte[40];random.nextBytes(noise);a.feed(noise);}check(true,"Bounded parser survives 400KB of noise");
        System.out.println("PASS: "+passed+" protocol checks");
    }
}
