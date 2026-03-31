package mcv.testfacepass.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * データベースアクセスを管理するヘルパークラス。
 *
 * 【重要設計方針】
 * このクラスは SQLiteOpenHelper のコネクションプールを適切に管理するため、
 * 各メソッド内で db.close() を呼び出さない。
 * Android の SQLiteOpenHelper はコネクションを内部でキャッシュしており、
 * db.close() を呼ぶと他のスレッド（RecognizeThread 等）が
 * 同じコネクションを利用中でも強制的に閉じてしまい、
 * "connection pool has been closed" の IllegalStateException を引き起こす。
 * コネクションのライフサイクルは SQLiteOpenHelper に委任する。
 */
public class DatabaseHelper {

    private static final String TAG = "DatabaseHelper";

    /** DBヘルパーのシングルインスタンス（コネクションプールを管理） */
    private final DBHelper dbHelper;

    /**
     * コンストラクタ。
     * @param context アプリケーションコンテキスト
     */
    public DatabaseHelper(Context context) {
        // アプリケーションコンテキストを使用してメモリリークを防止
        dbHelper = new DBHelper(context.getApplicationContext());
    }

    /**
     * 顔認証データを追加する。
     * @param faceId   SDKから取得したフェイストークン
     * @param name     登録者名
     * @param employeeId 社員番号
     * @return 追加成功の場合 true
     */
    public boolean add(String faceId, String name, String employeeId) {
        // 書き込み用DBを取得（db.close()は呼ばない → プール管理はSQLiteOpenHelperに委任）
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DBHelper.FIELD_FACE_ID, faceId);
        values.put(DBHelper.FIELD_NAME, name);
        values.put(DBHelper.FIELD_EMPLOYEE_ID, employeeId);
        long result = db.insert(DBHelper.TABLE_NAME, null, values);
        Log.d(TAG, "add() faceId=" + faceId + " result=" + result);
        return result != -1;
    }

    /**
     * 指定した faceId のレコードを削除する。
     * @param faceId 削除対象のフェイストークン
     * @return 削除成功の場合 true
     */
    public boolean delete(String faceId) {
        // 書き込み用DBを取得（db.close()は呼ばない → プール管理はSQLiteOpenHelperに委任）
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int result = db.delete(
                DBHelper.TABLE_NAME,
                DBHelper.FIELD_FACE_ID + "=?",
                new String[]{faceId}
        );
        Log.d(TAG, "delete() faceId=" + faceId + " deletedRows=" + result);
        return result > 0;
    }

    /**
     * faceId をキーに登録者名を検索する。
     * Cursor は finally ブロックで確実にクローズし、リソースリークを防ぐ。
     * ただし DB 接続自体は閉じず、コネクションプールを保持する。
     *
     * @param faceId 検索対象のフェイストークン
     * @return 登録者名。見つからない場合は null
     */
    public String findName(String faceId) {
        // 読み取り用DBを取得（db.close()は呼ばない → プール管理はSQLiteOpenHelperに委任）
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DBHelper.TABLE_NAME,
                    new String[]{DBHelper.FIELD_NAME},
                    DBHelper.FIELD_FACE_ID + "=?",
                    new String[]{faceId},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        } finally {
            // Cursor のみクローズ。DB接続はクローズしない（マルチスレッド安全性のため）
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * faceId をキーに社員番号を検索する。
     * Cursor は finally ブロックで確実にクローズし、リソースリークを防ぐ。
     * ただし DB 接続自体は閉じず、コネクションプールを保持する。
     *
     * @param faceId 検索対象のフェイストークン
     * @return 社員番号。見つからない場合は null
     */
    public String findEmployeeId(String faceId) {
        // 読み取り用DBを取得（db.close()は呼ばない → プール管理はSQLiteOpenHelperに委任）
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DBHelper.TABLE_NAME,
                    new String[]{DBHelper.FIELD_EMPLOYEE_ID},
                    DBHelper.FIELD_FACE_ID + "=?",
                    new String[]{faceId},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        } finally {
            // Cursor のみクローズ。DB接続はクローズしない（マルチスレッド安全性のため）
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * アプリ終了時にDBコネクションを明示的に解放する。
     * FacePassActivity.onDestroy() から呼び出すこと。
     * 通常の認証フロー中には呼ばないこと。
     */
    public void close() {
        Log.d(TAG, "close() DBコネクションを解放します。");
        dbHelper.close();
    }
}