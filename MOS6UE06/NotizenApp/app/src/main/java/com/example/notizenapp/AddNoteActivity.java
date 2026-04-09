package com.example.notizenapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddNoteActivity extends AppCompatActivity {

    private EditText editTitle;
    private EditText editContent;
    private String editingTitle;
    private String editingStorageType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_note);

        editTitle = findViewById(R.id.editTitle);
        editContent = findViewById(R.id.editContent);
        Button btnSaveInternal = findViewById(R.id.btnSaveInternal);
        Button btnSaveExternal = findViewById(R.id.btnSaveExternal);
        Button btnSaveEncrypted = findViewById(R.id.btnSaveEncrypted);

        // Check if we are editing an existing note
        editingTitle = getIntent().getStringExtra("editTitle");
        editingStorageType = getIntent().getStringExtra("editStorageType");
        String editingContent = getIntent().getStringExtra("editContent");

        if (editingTitle != null) {
            editTitle.setText(editingTitle);
            editContent.setText(editingContent);
            setTitle("Notiz bearbeiten");
        }

        btnSaveInternal.setOnClickListener(v -> saveNote(Note.StorageType.INTERNAL));
        btnSaveExternal.setOnClickListener(v -> saveNote(Note.StorageType.EXTERNAL));
        btnSaveEncrypted.setOnClickListener(v -> saveNote(Note.StorageType.ENCRYPTED));
    }

    private void saveNote(Note.StorageType storageType) {
        String title = editTitle.getText().toString().trim();
        String content = editContent.getText().toString().trim();

        if (title.isEmpty()) {
            editTitle.setError("Titel darf nicht leer sein");
            return;
        }
        if (content.isEmpty()) {
            editContent.setError("Inhalt darf nicht leer sein");
            return;
        }

        // When editing, allow saving with the same title + same storage type
        boolean isOverwrite = editingTitle != null
                && editingTitle.equals(title)
                && editingStorageType != null
                && editingStorageType.equals(storageType.name());

        if (!isOverwrite && NoteStorage.titleExists(this, title)) {
            editTitle.setError("Eine Notiz mit diesem Titel existiert bereits");
            return;
        }

        try {
            switch (storageType) {
                case INTERNAL:
                    NoteStorage.saveInternal(this, title, content);
                    break;
                case EXTERNAL:
                    NoteStorage.saveExternal(this, title, content);
                    break;
                case ENCRYPTED:
                    NoteStorage.saveEncrypted(this, title, content);
                    break;
            }
            Toast.makeText(this, "Notiz gespeichert (" + storageType.getDisplayName() + ")",
                    Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Fehler beim Speichern: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
