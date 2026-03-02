package mcv.testfacepass.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "face.db";
    // private static final int DB_VERSION = １;
    // バージョンアップ
    private static final int DB_VERSION = 2;

    public static final String TABLE_NAME = "face";
    public static final String FIELD_FACE_ID = "face_id";
    public static final String FIELD_NAME = "name";
    // 社員番号追加
    public static final String FIELD_EMPLOYEE_ID = "employee_id";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_NAME + "(" +
                FIELD_FACE_ID + " TEXT PRIMARY KEY, " +
                FIELD_NAME + " TEXT," +
                FIELD_EMPLOYEE_ID + " TEXT" +
                ")";
        db.execSQL(sql);
    }

    /*@Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }*/
   @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
       if (oldVersion < 2) {
           // 既存のテーブルに社員番号カラムを追加
           db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + FIELD_EMPLOYEE_ID + " TEXT");
       }
   }
}