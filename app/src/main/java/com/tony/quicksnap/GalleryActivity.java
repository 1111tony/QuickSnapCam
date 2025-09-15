/* File: QuickSnapCam/src/com/tony/quicksnap/GalleryActivity.java */
package com.tony.quicksnap;

import java.io.File;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class GalleryActivity extends Activity {
	private RecyclerView recyclerView;
	private MediaAdapter adapter;
	private DatabaseHelper dbHelper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_gallery);

		recyclerView = findViewById(R.id.recyclerView);
		recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
		dbHelper = new DatabaseHelper(this);

		List<String> imagePaths = getAllImages();
		adapter = new MediaAdapter(this, imagePaths);
		recyclerView.setAdapter(adapter);
	}

	private List<String> getAllImages() {
		List<String> list = new ArrayList<>();
		SQLiteDatabase db = dbHelper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT path FROM images ORDER BY timestamp DESC", null);
		if (cursor.moveToFirst()) {
			do {
				list.add(cursor.getString(0));
			} while (cursor.moveToNext());
		}
		cursor.close();
		return list;
	}
}