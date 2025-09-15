/* File: QuickSnapCam/src/com/tony/quicksnap/DatabaseHelper.java */
package com.tony.quicksnap;

import java.io.IOException;
import android.util.Log;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;

import java.io.File;

public class DatabaseHelper extends SQLiteOpenHelper {
	private static final String DB_NAME = "quicksnap.db";
	private static final int DB_VERSION = 2;

	public DatabaseHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(
				"CREATE TABLE images (id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2) {
			db.execSQL("ALTER TABLE images ADD COLUMN timestamp DATETIME DEFAULT CURRENT_TIMESTAMP");
		}
	}

	public void insertImage(String path) {
		SQLiteDatabase db = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("path", path);
		db.insert("images", null, values);
	}

	public boolean deleteImage(Context context, String path) {
    if (path == null) return false;
    try {
        File file = new File(path).getCanonicalFile();
        File appFiles = context.getFilesDir().getCanonicalFile();
        File appExternalFiles = context.getExternalFilesDir(null);
        boolean insideAppDir = false;

        if (file.toPath().startsWith(appFiles.toPath())) insideAppDir = true;
        if (appExternalFiles != null && file.toPath().startsWith(appExternalFiles.getCanonicalFile().toPath())) insideAppDir = true;

        if (!insideAppDir) {
            Log.w(TAG, "Attempt to delete file outside of app directories: " + path);
            return false; // disallow deletion outside app storage
        }
        boolean deleted = file.delete();
        if (deleted) {
            // also delete DB record
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete("images", "path = ?", new String[] { path });
        } else {
            Log.w(TAG, "Failed to delete file: " + path);
        }
        return deleted;
    } catch (IOException e) {
        Log.e(TAG, "Error validating file path for deletion", e);
        return false;
    }
}
