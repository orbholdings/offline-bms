package android.content;
import java.nio.file.*;
import java.util.*;
public class Context{
 public static final int MODE_PRIVATE=0;public final Path directory;private final Map<String,SharedPreferences> stores=new HashMap<>();
 public Context(Path dir){directory=dir;}
 public Path getDatabasePath(String name){return directory.resolve(name);}
 public SharedPreferences getSharedPreferences(String name,int mode){return stores.computeIfAbsent(name,n->new MemoryPrefs());}
 static final class MemoryPrefs implements SharedPreferences{final Map<String,String> map=new HashMap<>();public Map<String,?> getAll(){return new HashMap<>(map);}public String getString(String k,String fallback){return map.getOrDefault(k,fallback);}public Editor edit(){return new Editor(){final Map<String,String> next=new HashMap<>(map);public Editor putString(String k,String v){next.put(k,v);return this;}public Editor remove(String k){next.remove(k);return this;}public Editor clear(){next.clear();return this;}public void apply(){map.clear();map.putAll(next);}};}}
}
