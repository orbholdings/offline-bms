package android.util;
import java.io.*;
public final class JsonReader implements AutoCloseable{
 private final com.google.gson.stream.JsonReader r;
 public JsonReader(Reader input){r=new com.google.gson.stream.JsonReader(input);r.setStrictness(com.google.gson.Strictness.STRICT);}
 public void beginObject()throws IOException{r.beginObject();}public void endObject()throws IOException{r.endObject();}public void beginArray()throws IOException{r.beginArray();}public void endArray()throws IOException{r.endArray();}public boolean hasNext()throws IOException{return r.hasNext();}public String nextName()throws IOException{return r.nextName();}public int nextInt()throws IOException{return r.nextInt();}public String nextString()throws IOException{return r.nextString();}public boolean nextBoolean()throws IOException{return r.nextBoolean();}public void nextNull()throws IOException{r.nextNull();}public void skipValue()throws IOException{r.skipValue();}public JsonToken peek()throws IOException{return JsonToken.valueOf(r.peek().name());}public void close()throws IOException{r.close();}
}
