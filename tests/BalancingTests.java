package com.simon.offlinebms;

import java.util.*;

/** Wire vectors and settings validation for the single supported JK control. */
public final class BalancingTests {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Balancing check "+checks);}
    private static void rejected(Runnable action){boolean failed=false;try{action.run();}catch(IllegalArgumentException e){failed=true;}check(failed);}
    private static void le(byte[] f,int at,long n){for(int i=0;i<4;i++)f[at+i]=(byte)(n>>(i*8));}
    private static void checksum(byte[] f){int sum=0;for(int i=0;i<299;i++)sum+=f[i]&255;f[299]=(byte)sum;}
    private static byte[] settings(boolean enabled){byte[] f=new byte[300];f[0]=0x55;f[1]=(byte)0xaa;f[2]=(byte)0xeb;f[3]=(byte)0x90;f[4]=1;le(f,26,5);le(f,114,4);le(f,118,1);le(f,122,1);le(f,126,enabled?1:0);le(f,130,280000);le(f,138,3300);checksum(f);return f;}
    public static void main(String[] args){
        check(JbdProtocol.hex(JkProtocol.balancingCommand(true)).replace(" ","").equalsIgnoreCase("AA5590EB1F04010000000000000000000000009E"));
        check(JbdProtocol.hex(JkProtocol.balancingCommand(false)).replace(" ","").equalsIgnoreCase("AA5590EB1F04000000000000000000000000009D"));
        JbdProtocol.Basic live=new JbdProtocol.Basic();live.cellCount=4;live.capacityAh=280;
        for(String variant:new String[]{"JK02_24S","JK02_32S"}){
            JkProtocol.BalanceSettings off=JkProtocol.balanceSettings(settings(false),variant,live),on=JkProtocol.balanceSettings(settings(true),variant,live);
            check(!off.enabled);check(on.enabled);check(off.startVoltage==3.3);check(off.differenceMv==5);
        }
        rejected(()->JkProtocol.balanceSettings(settings(true),"Auto",live));rejected(()->JkProtocol.balanceSettings(settings(true),"JK04",live));
        rejected(()->JkProtocol.balanceSettings(null,"JK02_32S",live));rejected(()->JkProtocol.balanceSettings(new byte[299],"JK02_32S",live));
        byte[] damaged=settings(true);damaged[299]++;rejected(()->JkProtocol.balanceSettings(damaged,"JK02_32S",live));
        for(int[] mutation:new int[][]{{0,0},{4,2},{114,8},{118,2},{122,2},{126,2},{126,256},{130,0},{130,1000},{138,0},{138,6501},{26,1001}}){
            byte[] f=settings(true);if(mutation[0]<6)f[mutation[0]]=(byte)mutation[1];else le(f,mutation[0],mutation[1]);checksum(f);rejected(()->JkProtocol.balanceSettings(f,"JK02_32S",live));
        }
        byte[] valid=settings(true);rejected(()->JkProtocol.balanceSettings(valid,"JK02_32S",null));
        JbdSettings.Field field=JbdSettings.field("balance_enabled");
        check(JbdSettings.u16(field.encode("1",JbdSettings.word(0xab08)))==0xab0c);
        check(JbdSettings.u16(field.encode("0",JbdSettings.word(0xab0c)))==0xab08);
        System.out.println("PASS: "+checks+" balancing wire-vector, layout and bit-preservation checks");
    }
}
