package com.simon.offlinebms;

import java.nio.file.*;
import java.util.*;

/** Replays actual status captures; no device identity, settings or credentials. */
public final class JkCapturedTests {
    private static int checks;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    private static void close(double actual,double expected,String text){check(Math.abs(actual-expected)<.000001,text+": "+actual);}
    private static byte[] hex(Path path)throws Exception{String h=Files.readString(path).trim();byte[] f=new byte[h.length()/2];for(int i=0;i<f.length;i++)f[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);return f;}
    private static void le(byte[] f,int n,int v){f[n]=(byte)v;f[n+1]=(byte)(v>>8);}
    private static void rejects(byte[] frame,String expected){try{JkProtocol.parse(frame,"JK02_32S");throw new AssertionError("Accepted "+expected);}catch(IllegalArgumentException e){check(e.getMessage().contains(expected),"Expected "+expected+", got "+e.getMessage());}}
    public static void main(String[] args)throws Exception{
        byte[] first=null;
        for(String name:new String[]{"first","last"}){
            byte[] frame=hex(Path.of(args[0],"jk32-fw11-55-"+name+".hex"));byte[] original=frame.clone();if(first==null)first=frame.clone();check(frame.length==300,"Capture length");
            JkProtocol.Assembler assembler=new JkProtocol.Assembler();List<byte[]> frames=new ArrayList<>();int start=0;
            for(int length:new int[]{128,22,128,22}){List<byte[]> part=assembler.feed(Arrays.copyOfRange(frame,start,start+length));start+=length;check(part.size()==(start==300?1:0),"Captured chunk boundaries");frames.addAll(part);}
            check(assembler.rejected==0,"Captured checksum passes");check(Arrays.equals(frame,frames.get(0)),"Assembler preserves bytes");
            for(String variant:new String[]{"JK02_32S","Auto"}){
                JkProtocol.Sample sample=JkProtocol.parse(frames.get(0),variant);check(sample.cells.length==4,"Actual four-cell pack");
                double[] expected={3.285,3.281,3.285,3.284};for(int i=0;i<4;i++)close(sample.cells[i],expected[i],"Captured cell "+(i+1));
                close(sample.basic.voltage,13.134,"Captured pack voltage");close(sample.basic.current,0,"Captured idle current");check(sample.basic.soc==89,"Captured SOC");close(sample.basic.capacityAh,280,"Captured capacity");close(sample.basic.remainingAh,name.equals("first")?248.965:248.964,"Captured remaining capacity");
                check(sample.basic.cycles==4,"Captured cycles");check(sample.basic.chargeEnabled()&&sample.basic.dischargeEnabled(),"Both MOSFET states");check(sample.basic.vendorAlarmFlags==0,"Captured alarm bitmask");check(sample.basic.temperatures.length==0&&sample.basic.temperatureLabels.length==0,"Unavailable temperature fields omitted");check(sample.basic.firmware.equals("JK02 32S"),"Actual layout chosen");
            }
            try{JkProtocol.parse(frame,"JK02_24S");throw new AssertionError("Wrong explicit variant accepted");}catch(JkProtocol.VariantMismatchException e){check(e.detectedVariant.equals("JK02_32S")&&e.getMessage().contains("or Auto"),"Wrong profile gets a specific correction");}
            check(Arrays.equals(frame,original),"Parser leaves capture unchanged");
        }
        for(int raw:new int[]{-1999,-2001,32767}){byte[] invalid=first.clone();le(invalid,162,raw);rejects(invalid,"T1 temperature");}
        byte[] cold=first.clone();le(cold,162,-100);JkProtocol.Sample realTemperature=JkProtocol.parse(cold,"Auto");check(Arrays.equals(realTemperature.basic.temperatureLabels,new String[]{"T1"}),"Real sensor label retained");close(realTemperature.basic.temperatures[0],-10,"Real negative temperature retained");
        byte[] zeroVoltage=first.clone();Arrays.fill(zeroVoltage,150,154,(byte)0);rejects(zeroVoltage,"Pack voltage");
        byte[] invalidSoc=first.clone();invalidSoc[173]=(byte)101;rejects(invalidSoc,"State of charge");
        byte[] invalidFet=first.clone();invalidFet[198]=2;rejects(invalidFet,"MOSFET states");
        byte[] invalidCapacity=first.clone();Arrays.fill(invalidCapacity,178,182,(byte)0);rejects(invalidCapacity,"Capacity");
        byte[] corrupt=first.clone();corrupt[100]^=1;check(new JkProtocol.Assembler().feed(corrupt).isEmpty(),"Bad checksum still rejected");
        System.out.println("PASS: "+checks+" captured JK32 replay and validation checks");
    }
}
