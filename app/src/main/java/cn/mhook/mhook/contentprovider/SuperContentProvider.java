package cn.mhook.mhook.contentprovider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.Nullable;

public class SuperContentProvider extends ContentProvider {
    private SQLiteDatabase db;
    private static final String MAUTHORITIESNAME = "mHookData";
    private static UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int print = 1;
    private static final int jsonCfg = 2;
    private static final int appCfg = 3;
    private static final String TABLE_NAME = "printLog";
    private static final String TABLE_NAME2 = "jsonCfg";
    private static final String TABLE_NAME3 = "appCfg";
    
    static {
        
        matcher.addURI(MAUTHORITIESNAME, "print", print);
        matcher.addURI(MAUTHORITIESNAME, "jsonCfg", jsonCfg);
        matcher.addURI(MAUTHORITIESNAME, "appCfg", appCfg);
    }

    @Override
    public boolean onCreate() {
        DBHelper helper = new DBHelper(getContext());
        
        db = helper.getWritableDatabase();
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        
        int match = matcher.match(uri);
        switch (match) {
            case print:
                return db.query(TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
            case jsonCfg:
                return db.query(TABLE_NAME2, projection, selection, selectionArgs,
                        null, null, sortOrder);
            case appCfg:
                return db.query(TABLE_NAME3, projection, selection, selectionArgs,
                        null, null, sortOrder);
            default:
                break;
        }
        return null;
    }


    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        
        int match = matcher.match(uri);
        switch (match) {
            case print:
                
                getContext().getContentResolver().notifyChange(uri, null);
                long id = db.insert(TABLE_NAME, null, values);
                
                return ContentUris.withAppendedId(uri, id);
            case jsonCfg:
                getContext().getContentResolver().notifyChange(uri, null);
                long id2 = db.insert(TABLE_NAME2, null, values);
                
                return ContentUris.withAppendedId(uri, id2);
            case appCfg:
                getContext().getContentResolver().notifyChange(uri, null);
                long id3 = db.insert(TABLE_NAME3, null, values);
                
                return ContentUris.withAppendedId(uri, id3);
            default:
                break;
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        
        int match = matcher.match(uri);
        switch (match) {
            case print:
                

                int id = db.delete(TABLE_NAME,selection,selectionArgs);

                
                return id;
            case jsonCfg:
                int id2 = db.delete(TABLE_NAME2,selection,selectionArgs);

                
                return id2;
            case appCfg:
                int id3 = db.delete(TABLE_NAME3,selection,selectionArgs);

                
                return id3;
            default:
                break;
        }
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        
        int match = matcher.match(uri);
        switch (match) {
            case print:
                
                int id = db.update(TABLE_NAME,values,selection,selectionArgs);
                
                return id;
            case jsonCfg:
                
                int id2 = db.update(TABLE_NAME2,values,selection,selectionArgs);
                
                return id2;
            case appCfg:
                
                int id3 = db.update(TABLE_NAME3,values,selection,selectionArgs);
                
                return id3;
            default:
                break;
        }
        return 0;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        return null;
    }

}

