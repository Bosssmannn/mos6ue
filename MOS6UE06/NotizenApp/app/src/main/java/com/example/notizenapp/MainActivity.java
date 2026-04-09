package com.example.notizenapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ListView listViewNotes;
    private TextView textEmpty;
    private List<Note> notes;
    private List<String> displayTitles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listViewNotes = findViewById(R.id.listViewNotes);
        textEmpty = findViewById(R.id.textEmpty);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddNoteActivity.class);
            startActivity(intent);
        });

        listViewNotes.setOnItemClickListener((parent, view, position, id) -> {
            Note note = notes.get(position);
            Intent intent = new Intent(MainActivity.this, ViewNoteActivity.class);
            intent.putExtra("title", note.getTitle());
            intent.putExtra("storageType", note.getStorageType().name());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotes();
    }

    private void loadNotes() {
        notes = NoteStorage.loadAllNotes(this);
        displayTitles = new ArrayList<>();

        for (Note note : notes) {
            displayTitles.add(note.getTitle() + " (" + note.getStorageType().getDisplayName() + ")");
        }

        if (notes.isEmpty()) {
            listViewNotes.setVisibility(View.GONE);
            textEmpty.setVisibility(View.VISIBLE);
        } else {
            listViewNotes.setVisibility(View.VISIBLE);
            textEmpty.setVisibility(View.GONE);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, displayTitles);
        listViewNotes.setAdapter(adapter);
    }
}
