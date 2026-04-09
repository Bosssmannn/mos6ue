package com.example.notizenapp;

public class Note {

    public enum StorageType {
        INTERNAL("Intern"),
        EXTERNAL("Extern"),
        ENCRYPTED("Verschlüsselt");

        private final String displayName;

        StorageType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String title;
    private final String content;
    private final StorageType storageType;

    public Note(String title, String content, StorageType storageType) {
        this.title = title;
        this.content = content;
        this.storageType = storageType;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public StorageType getStorageType() { return storageType; }
}
