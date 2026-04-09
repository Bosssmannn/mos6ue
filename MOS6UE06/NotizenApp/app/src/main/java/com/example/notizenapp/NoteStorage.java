package com.example.notizenapp;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NoteStorage {

    private static final String ENCRYPTED_PREFS = "encrypted_notes";

    // --- Internal Storage ---

    public static void saveInternal(Context context, String title, String content) throws Exception {
        String fileName = "note_" + title + ".txt";
        FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        fos.write(content.getBytes());
        fos.close();
    }

    public static String readInternal(Context context, String title) throws Exception {
        String fileName = "note_" + title + ".txt";
        FileInputStream fis = context.openFileInput(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString().trim();
    }

    public static boolean existsInternal(Context context, String title) {
        String fileName = "note_" + title + ".txt";
        File file = new File(context.getFilesDir(), fileName);
        return file.exists();
    }

    public static List<String> listInternalTitles(Context context) {
        List<String> titles = new ArrayList<>();
        File dir = context.getFilesDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("note_") && name.endsWith(".txt")) {
                    titles.add(name.substring(5, name.length() - 4));
                }
            }
        }
        return titles;
    }

    // --- External Storage ---

    public static void saveExternal(Context context, String title, String content) throws Exception {
        File dir = context.getExternalFilesDir(null);
        File file = new File(dir, "note_" + title + ".txt");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes());
        fos.close();
    }

    public static String readExternal(Context context, String title) throws Exception {
        File dir = context.getExternalFilesDir(null);
        File file = new File(dir, "note_" + title + ".txt");
        FileInputStream fis = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString().trim();
    }

    public static boolean existsExternal(Context context, String title) {
        File dir = context.getExternalFilesDir(null);
        File file = new File(dir, "note_" + title + ".txt");
        return file.exists();
    }

    public static List<String> listExternalTitles(Context context) {
        List<String> titles = new ArrayList<>();
        File dir = context.getExternalFilesDir(null);
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.startsWith("note_") && name.endsWith(".txt")) {
                        titles.add(name.substring(5, name.length() - 4));
                    }
                }
            }
        }
        return titles;
    }

    // --- Encrypted SharedPreferences ---

    private static SharedPreferences getEncryptedPrefs(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public static void saveEncrypted(Context context, String title, String content) throws Exception {
        SharedPreferences prefs = getEncryptedPrefs(context);
        prefs.edit().putString(title, content).apply();
    }

    public static String readEncrypted(Context context, String title) throws Exception {
        SharedPreferences prefs = getEncryptedPrefs(context);
        return prefs.getString(title, "");
    }

    public static boolean existsEncrypted(Context context, String title) {
        try {
            SharedPreferences prefs = getEncryptedPrefs(context);
            return prefs.contains(title);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> listEncryptedTitles(Context context) {
        List<String> titles = new ArrayList<>();
        try {
            SharedPreferences prefs = getEncryptedPrefs(context);
            Map<String, ?> all = prefs.getAll();
            titles.addAll(all.keySet());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return titles;
    }

    // --- Unified helpers ---

    public static List<Note> loadAllNotes(Context context) {
        List<Note> notes = new ArrayList<>();

        for (String title : listInternalTitles(context)) {
            try {
                String content = readInternal(context, title);
                notes.add(new Note(title, content, Note.StorageType.INTERNAL));
            } catch (Exception ignored) {}
        }

        for (String title : listExternalTitles(context)) {
            try {
                String content = readExternal(context, title);
                notes.add(new Note(title, content, Note.StorageType.EXTERNAL));
            } catch (Exception ignored) {}
        }

        for (String title : listEncryptedTitles(context)) {
            try {
                String content = readEncrypted(context, title);
                notes.add(new Note(title, content, Note.StorageType.ENCRYPTED));
            } catch (Exception ignored) {}
        }

        return notes;
    }

    public static boolean titleExists(Context context, String title) {
        return existsInternal(context, title)
                || existsExternal(context, title)
                || existsEncrypted(context, title);
    }
}
