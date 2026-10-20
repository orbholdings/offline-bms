package android.database.sqlite;
import android.content.ContentValues;
import android.database.Cursor;
import java.sql.*;
import java.util.*;
public final class SQLiteDatabase implements AutoCloseable{
 public static final int CONFLICT_REPLACE=5;private final Connection c;private boolean success;
 public SQLiteDatabase(java.nio.file.Path path){try{c=DriverManager.getConnection("jdbc:sqlite:"+path);}catch(SQLException e){throw new RuntimeException(e);}}
 public void execSQL(String sql){try(Statement s=c.createStatement()){s.execute(sql);}catch(SQLException e){throw new RuntimeException(e);}}
 private PreparedStatement statement(String sql,String[] args)throws SQLException{PreparedStatement p=c.prepareStatement(sql);if(args!=null)for(int i=0;i<args.length;i++)p.setString(i+1,args[i]);return p;}
 public Cursor rawQuery(String sql,String[] args){try(PreparedStatement p=statement(sql,args);ResultSet r=p.executeQuery()){List<Object[]> out=new ArrayList<>();while(r.next()){Object[] row=new Object[r.getMetaData().getColumnCount()];for(int i=0;i<row.length;i++)row[i]=r.getObject(i+1);out.add(row);}return new Cursor(out);}catch(SQLException e){throw new RuntimeException(e);}}
 public long insertOrThrow(String table,String empty,ContentValues values){return insertWithOnConflict(table,empty,values,0);}
 public long insert(String table,String empty,ContentValues values){return insertOrThrow(table,empty,values);}
 public long insertWithOnConflict(String table,String empty,ContentValues values,int conflict){String marks=String.join(",",Collections.nCopies(values.size(),"?")),columns=String.join(",",values.keySet());try(PreparedStatement p=c.prepareStatement((conflict==5?"INSERT OR REPLACE":"INSERT")+" INTO "+table+" ("+columns+") VALUES ("+marks+")")){int n=1;for(Object v:values.values())p.setObject(n++,v);p.executeUpdate();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT last_insert_rowid()")){r.next();return r.getLong(1);}}catch(SQLException e){throw new RuntimeException(e);}}
 public int update(String table,ContentValues values,String where,String[] args){List<String> set=new ArrayList<>();for(String key:values.keySet())set.add(key+"=?");try(PreparedStatement p=c.prepareStatement("UPDATE "+table+" SET "+String.join(",",set)+(where==null?"":" WHERE "+where))){int n=1;for(Object v:values.values())p.setObject(n++,v);if(args!=null)for(String a:args)p.setString(n++,a);return p.executeUpdate();}catch(SQLException e){throw new RuntimeException(e);}}
 public int delete(String table,String where,String[] args){try(PreparedStatement p=statement("DELETE FROM "+table+(where==null?"":" WHERE "+where),args)){return p.executeUpdate();}catch(SQLException e){throw new RuntimeException(e);}}
 public void beginTransaction(){try{c.setAutoCommit(false);success=false;}catch(SQLException e){throw new RuntimeException(e);}}
 public void setTransactionSuccessful(){success=true;}
 public void endTransaction(){try{if(success)c.commit();else c.rollback();c.setAutoCommit(true);}catch(SQLException e){throw new RuntimeException(e);}}
 public void close(){try{c.close();}catch(SQLException e){throw new RuntimeException(e);}}
}
