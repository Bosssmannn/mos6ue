package com.example.notizenapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ViewNoteActivity extends AppCompatActivity {

    private String title;
    private String content;
    private String storageTypeName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_note);

        TextView textTitle = findViewById(R.id.textTitle);
        TextView textContent = findViewById(R.id.textContent);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnEdit = findViewById(R.id.btnEdit);

        title = getIntent().getStringExtra("title");
        storageTypeName = getIntent().getStringExtra("storageType");

        if (title == null || storageTypeName == null) {
            finish();
            return;
        }

        Note.StorageType storageType = Note.StorageType.valueOf(storageTypeName);

        try {
            switch (storageType) {
                case INTERNAL:
                    content = NoteStorage.readInternal(this, title);
                    break;
                case EXTERNAL:
                    content = NoteStorage.readExternal(this, title);
                    break;
                case ENCRYPTED:
                    content = NoteStorage.readEncrypted(this, title);
                    break;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Fehler beim Lesen: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        textTitle.setText(title);
        textContent.setText(content);

        btnBack.setOnClickListener(v -> finish());

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(ViewNoteActivity.this, AddNoteActivity.class);
            intent.putExtra("editTitle", title);
            intent.putExtra("editContent", content);
            intent.putExtra("editStorageType", storageTypeName);
            startActivity(intent);
            finish();
        });
    }
}
