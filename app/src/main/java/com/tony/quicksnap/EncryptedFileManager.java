package com.yourpackage;

import java.util.Optional;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public class EncryptedFileManager {

    private static final String TAG = "EncryptedFileManager";
    private final Context context;
    private final MasterKey masterKey;

    public EncryptedFileManager(Context context) throws GeneralSecurityException, IOException {
        this.context = context;

        // Create or retrieve MasterKey
        masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
    }

    // ---------------------------
    // String-based methods
    // ---------------------------
    public void write(String filename, String data) {
        writeBytes(filename, data.getBytes(StandardCharsets.UTF_8));
    }

    public String read(String filename) {
        byte[] bytes = readBytes(filename);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    // ---------------------------
    // Byte array methods (for images)
    // ---------------------------
    public void writeBytes(String filename, byte[] data) {
        File file = new File(context.getFilesDir(), filename);

        try {
            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                    file,
                    context,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            try (FileOutputStream fos = encryptedFile.openFileOutput()) {
                fos.write(data);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error writing encrypted file: " + filename, e);
        }
    }

    public byte[] readBytes(String filename) {
        File file = new File(context.getFilesDir(), filename);

        if (!file.exists()) {
            Log.w(TAG, "Encrypted file does not exist: " + filename);
            return null;
        }

        try {
            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                    file,
                    context,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            try (FileInputStream fis = encryptedFile.openFileInput()) {
                return fis.readAllBytes();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading encrypted file: " + filename, e);
        }

        return null;
    }

    // ---------------------------
    // Optional: delete file
    // ---------------------------
    public boolean delete(String filename) {
        File file = new File(context.getFilesDir(), filename);
        return file.exists() && file.delete();
    }
}