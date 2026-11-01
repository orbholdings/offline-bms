package com.orbholdings.offlinehub;

import java.util.Locale;

/**
 * Read-only AirHeaterBLE status parser, based on the AA55/AA66 layout in
 * Spettacolo83/diesel-heater-ble (MIT). Does not interpret command echoes
 * or short/unknown packets as measurements.
 */
public final class HeaterProtocol {
 private HeaterProtocol(){}
 public static final class State {
  public final String protocol;
  public final int runningState, runningStep, runningMode, error, level;
  public final double voltage, caseTemp, cabinTemp, altitude;
  State(String p,int run,int step,int mode,int err,int lev,double v,double ct,double cab,double alt){
   protocol=p;runningState=run;runningStep=step;runningMode=mode;error=err;level=lev;voltage=v;caseTemp=ct;cabinTemp=cab;altitude=alt;
  }
  public String summary(){return String.format(Locale.ROOT,
   "%s: state=%d step=%d mode=%d level=%d cabin=%.1f°C case=%.1f°C supply=%.1fV altitude=%.1fm error=%d",
   protocol,runningState,runningStep,runningMode,level,cabinTemp,caseTemp,voltage,altitude,error);}
 }
 private static int u(byte[] b,int i){return b[i]&255;}
 private static int signed16(int n){return n>=32768?n-65536:n;}
 private static boolean plausible(State s){return s.runningState<=4&&s.runningMode<=4&&s.voltage>=8&&s.voltage<=65&&s.cabinTemp>=-50&&s.cabinTemp<=90&&s.caseTemp>=-60&&s.caseTemp<=550&&s.level>=0&&s.level<=11;}
 public static State parse(byte[] b){
  if(b==null)return null;
  // Controller's FFE1 read may contain an echoed 8-byte AA55 request
  // followed by buffered bytes. Never treat that as a standalone reply.
  if(b.length>=8&&u(b,0)==0xAA&&u(b,1)==0x55&&u(b,2)==12&&u(b,3)==34&&u(b,4)==1)return null;
  if(b.length!=18&&b.length!=20&&b.length!=48)return null;
  byte[] d=b;
  String family;
  if(b.length==48){
   d=b.clone();byte[] key="12345678".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
   for(int i=0;i<48;i++)d[i]=(byte)(d[i]^key[i%8]);
   if(u(d,0)!=0xAA||(u(d,1)!=0x55&&u(d,1)!=0x66))return null;
   family=u(d,1)==0x55?"AA55 encrypted":"AA66 encrypted";
  }else{
   if(u(d,0)!=0xAA||(u(d,1)!=0x55&&u(d,1)!=0x66))return null;
   family=u(d,1)==0x55?"AA55":"AA66";
  }
  int run=u(d,3),step=u(d,5),mode=u(d,8),error=u(d,4);
  double voltage,ct,cab,alt;int level;
  if(b.length==48){
   level=u(d,10);
   alt=(u(d,6)*256+u(d,7))/10.0;
   voltage=(u(d,11)*256+u(d,12))/10.0;
   ct=signed16(u(d,13)*256+u(d,14));
   if(u(d,1)==0x55)cab=signed16(u(d,32)*256+u(d,33))/10.0;
   else cab=u(d,15);
  }else if(u(d,1)==0x55){
   level=mode==1?u(d,9):u(d,10)+1;
   alt=u(d,6)+u(d,7)*256;
   voltage=(u(d,11)+u(d,12)*256)/10.0;
   ct=signed16(u(d,13)+u(d,14)*256);
   cab=signed16(u(d,15)+u(d,16)*256);
  }else{
   level=u(d,9);
   alt=u(d,6);
   voltage=(u(d,11)+u(d,12)*256)/10.0;
   int raw=u(d,13)+u(d,14)*256;
   ct=raw>350?raw/10.0:raw;
   cab=u(d,15);
  }
  State state=new State(family,run,step,mode,error,level,voltage,ct,cab,alt);
  return plausible(state)?state:null;
 }
}
