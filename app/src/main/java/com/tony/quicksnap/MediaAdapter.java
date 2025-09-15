/* File: QuickSnapCam/src/com/tony/quicksnap/MediaAdapter.java */
package com.tony.quicksnap;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.nio.file.Files;
import androidx.annotation.NonNull;
import android.util.Log;

import android.widget.Adapter;
import android.view.ViewGroup;
import android.view.View;
import android.view.LayoutInflater;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {
	private final ExecutorService decodeExecutor = Executors.newFixedThreadPool(2);
	private final Context context;

	public MediaAdapter(Context context, List<String> paths) {
		this.context = context;
		this.paths = paths;
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(context).inflate(R.layout.item_media, parent, false);
		return new ViewHolder(v);
	}

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    	
    	String filename = mediaList.get(position);
    	holder.imageView.setImageDrawable(null);
    	holder.progressBar.setVisibility(View.VISIBLE);
        decodeExecutor.submit(() -> {
            try {
                // Read encrypted bytes from file
                File f = new File(context.getFilesDir(), filename); // or where you store them
                byte[] encrypted = Files.readAllBytes(f.toPath());

                // decrypt (example using CryptoUtil)
                byte[] jpegBytes = CryptoUtil.decrypt(encrypted);
                final Bitmap bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

                holder.imageView.post(() -> {
                    holder.progressBar.setVisibility(View.GONE);
                    holder.imageView.setImageBitmap(bmp);
                });
            } catch (Exception e) {
                Log.e("MediaAdapter", "Failed to load image " + filename, e);
                holder.imageView.post(() -> {
                    holder.progressBar.setVisibility(View.GONE);
                    holder.imageView.setImageResource(R.drawable.ic_broken_image);
                });
            }
        });
    }

    // remember to shut down the executor when adapter no longer needed
    public void shutdown() {
        decodeExecutor.shutdownNow();
    }
}

	@Override
	public int getItemCount() {
		return paths.size();
	}

	static class ViewHolder extends RecyclerView.ViewHolder {
		ImageView imageView;
		TextView timestampView;

		ViewHolder(View itemView) {
			super(itemView);
			imageView = itemView.findViewById(R.id.imageView);
			timestampView = itemView.findViewById(R.id.timestampView);
		}
	}
}
