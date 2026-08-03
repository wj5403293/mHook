package cn.mhook.mhook.contentprovider;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "mhook.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "printLog";
    private static final String TABLE_NAME2 = "jsonCfg";
    private static final String TABLE_NAME3 = "appCfg";
    private static final String ID = "_id";
    private static final String msg = "msg";
    private static final String time = "time";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_NAME + "(" + ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" + "," + msg
                + " CHAR(10)"+"," + time
                + " CHAR(10))";
        db.execSQL(sql);
        String sql2 = "CREATE TABLE " + TABLE_NAME2 + "(" + ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,pkg CHAR(10),config CHAR(10),enable CHAR(10),canUse CHAR(10),keyStr CHAR(10),useSdcard CHAR(10))";
        db.execSQL(sql2);
        String sql3 = "CREATE TABLE " + TABLE_NAME3 + "(" + ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,pkg CHAR(10),config CHAR(10))";
        db.execSQL(sql3);
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}


