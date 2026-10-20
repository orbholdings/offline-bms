package android.database.sqlite;
import android.content.Context;
import android.database.Cursor;
public abstract class SQLiteOpenHelper{
 private final Context context;private final String name;private final int version;private SQLiteDatabase db;
 public SQLiteOpenHelper(Context c,String name,Object factory,int v){context=c;this.name=name;version=v;}
 public SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
 public SQLiteDatabase getWritableDatabase(){if(db!=null)return db;db=new SQLiteDatabase(context.getDatabasePath(name));int old;try(Cursor c=db.rawQuery("PRAGMA user_version",null)){c.moveToFirst();old=c.getInt(0);}if(old<version){db.beginTransaction();try{if(old==0)onCreate(db);else onUpgrade(db,old,version);db.execSQL("PRAGMA user_version="+version);db.setTransactionSuccessful();}finally{db.endTransaction();}}return db;}
 public abstract void onCreate(SQLiteDatabase db);public abstract void onUpgrade(SQLiteDatabase db,int old,int next);public void close(){if(db!=null)db.close();db=null;}
}
