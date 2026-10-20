package com.simon.offlinebms;

import java.util.*;

/** Independent status vectors for documented missing-sensor bits and rejection reasons. */
public final class JkDiagnosticsTests {
    private static int checks;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    private static void close(double a,double b,String text){check(Math.abs(a-b)<.00001,text);}
    private static void le(byte[] f,int n,long v,int count){for(int i=0;i<count;i++)f[n+i]=(byte)(v>>(8*i));}
    private static void rejects(byte[] f,String protocol,String text){try{JkProtocol.parse(f,protocol);throw new AssertionError("Accepted "+text);}catch(IllegalArgumentException e){check(e.getMessage().contains(text),"Rejection should explain "+text+": "+e.getMessage());}}
    private static byte[] frame(boolean newer){
        byte[] f=new byte[300];f[0]=0x55;f[1]=(byte)0xaa;f[2]=(byte)0xeb;f[3]=(byte)0x90;f[4]=2;int offset=newer?32:0;
        int[] cells={3313,3312,3310,3308};for(int i=0;i<cells.length;i++)le(f,6+i*2,cells[i],2);
        le(f,118+offset,13243,4);le(f,126+offset,-1250,4);f[141+offset]=90;le(f,142+offset,252500,4);le(f,146+offset,280000,4);f[166+offset]=1;f[167+offset]=1;
        le(f,newer?144:134,250,2);le(f,130+offset,220,2);le(f,132+offset,-15,2);return f;
    }
    public static void main(String[] args){
        for(boolean newer:new boolean[]{false,true}){
            String protocol=newer?"JK02_32S":"JK02_24S";int offset=newer?32:0,mos=newer?144:134;
            byte[] all=frame(newer);JkProtocol.Sample base=JkProtocol.parse(all,protocol);check(base.cells.length==4,"Four cells");close(base.basic.voltage,13.243,"Pack voltage retained");close(base.basic.current,-1.25,"Signed current retained");check(Arrays.equals(base.basic.temperatureLabels,new String[]{"MOS","T1","T2"}),"Present sensor labels");
            byte[] missing=all.clone();missing[182+offset]=3;le(missing,mos,0x7fff,2);le(missing,130+offset,0x8000,2);
            JkProtocol.Sample sparse=JkProtocol.parse(missing,"Auto");check(sparse.basic.soc==90,"Auto layout still works when sensors are absent");check(sparse.basic.temperatures.length==1,"Absent sensors omitted");close(sparse.basic.temperatures[0],-1.5,"Present negative temperature retained");check(Arrays.equals(sparse.basic.temperatureLabels,new String[]{"T2"}),"T2 is not relabelled MOS");close(sparse.basic.capacityAh,280,"Capacity unaffected by absent sensors");
            missing[182+offset]=7;le(missing,132+offset,0x7fff,2);check(JkProtocol.parse(missing,protocol).basic.temperatures.length==0,"All missing sensors allowed");
            byte[] invalid=all.clone();le(invalid,130+offset,0x7fff,2);rejects(invalid,protocol,"T1 temperature");rejects(invalid,"Auto",protocol+":");
            invalid=missing.clone();le(invalid,118+offset,0,4);rejects(invalid,protocol,"Pack voltage");
            invalid=missing.clone();le(invalid,146+offset,0,4);rejects(invalid,protocol,"Capacity");
            invalid=missing.clone();le(invalid,126+offset,4000001,4);rejects(invalid,protocol,"Current");
            invalid=missing.clone();invalid[141+offset]=(byte)101;rejects(invalid,protocol,"State of charge");
            invalid=all.clone();le(invalid,8,0,2);rejects(invalid,protocol,"unused channel");
            invalid=all.clone();le(invalid,6,6501,2);rejects(invalid,protocol,"Cell 1 voltage");
        }
        rejects(new byte[300],"Auto","Not a JK cell-info frame");rejects(new byte[299],"Auto","Not a JK cell-info frame");rejects(null,"Auto","Not a JK cell-info frame");
        byte[] unknown=frame(false);le(unknown,6,0xffff,2);try{JkProtocol.parse(unknown,"Auto");throw new AssertionError("Unknown format accepted");}catch(IllegalArgumentException e){check(e.getMessage().contains("JK02_24S:")&&e.getMessage().contains("JK02_32S:"),"Auto diagnostics retain both layout reasons");}
        System.out.println("PASS: "+checks+" JK diagnostics and absent-sensor checks");
    }
}
