package com.evan.sae32;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientActivity extends AppCompatActivity {

    private EditText etIp, etPort, etLogin, etPassword;
    private Button btnRegister, btnLogin, btnSelectFile, btnSendFile;
    private TextView tvLog; // TextView pour afficher les logs
    private DatabaseHelper dbHelper;
    private boolean isUserLoggedIn = false;
    private static final int PICK_FILE_REQUEST = 1; // Code pour la sélection de fichier
    private Uri selectedFileUri = null; // URI du fichier sélectionné
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1; // Code pour demander la permission de lire le stockage externe

    // Modification du port ici
    private static final int DEFAULT_SERVER_PORT = 24689;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        // Initialisation des vues
        etIp = findViewById(R.id.etIp);
        etPort = findViewById(R.id.etPort);
        etLogin = findViewById(R.id.etLogin);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogin = findViewById(R.id.btnLogin);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnSendFile = findViewById(R.id.btnSendFile);
        tvLog = findViewById(R.id.tvLog);

        // Initialisation de la base de données
        dbHelper = new DatabaseHelper(this);

        // Gestion des boutons
        btnRegister.setOnClickListener(v -> registerUser());
        btnLogin.setOnClickListener(v -> loginUser());
        btnSelectFile.setOnClickListener(v -> requestReadStoragePermission());
        btnSendFile.setOnClickListener(v -> sendFile());

        // Masquer les boutons de fichier au départ
        btnSelectFile.setVisibility(View.GONE);
        btnSendFile.setVisibility(View.GONE);
    }

    private void registerUser() {
        String login = etLogin.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (login.isEmpty() || password.isEmpty()) {
            showToast("Veuillez remplir tous les champs.");
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("login", login);
        values.put("password", password);

        long result = db.insert("users", null, values);
        db.close();

        if (result == -1) {
            showToast("Erreur lors de la création du compte.");
        } else {
            showToast("Compte créé avec succès.");
            clearLoginFields();
        }
    }

    private void loginUser() {
        String login = etLogin.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (login.isEmpty() || password.isEmpty()) {
            showToast("Veuillez remplir tous les champs.");
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE login = ? AND password = ?", new String[]{login, password});

        if (cursor.moveToFirst()) {
            showToast("Connexion réussie.");
            isUserLoggedIn = true;
            btnSelectFile.setVisibility(View.VISIBLE);
            btnSendFile.setVisibility(View.VISIBLE);
        } else {
            showToast("Login ou mot de passe incorrect.");
        }

        cursor.close();
        db.close();
    }

    private void requestReadStoragePermission() {
        String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            selectFile();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectFile();
            } else {
                showToast("La permission de lire les fichiers est nécessaire pour sélectionner un fichier.");
            }
        }
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            if (selectedFileUri != null) {
                logMessage("Fichier sélectionné : " + selectedFileUri.getLastPathSegment());
                btnSendFile.setEnabled(true);
            }
        }
    }

    private void sendFile() {
        if (selectedFileUri == null) {
            logMessage("Aucun fichier sélectionné.");
            return;
        }

        String ip = etIp.getText().toString().trim();
        String portString = etPort.getText().toString().trim();
        int port = DEFAULT_SERVER_PORT;

        if (ip.isEmpty()) {
            logMessage("Veuillez entrer l'adresse IP du serveur.");
            return;
        }

        try {
            if (!portString.isEmpty()) {
                port = Integer.parseInt(portString);
            }
        } catch (NumberFormatException e) {
            logMessage("Le port doit être un nombre valide.");
            return;
        }

        int finalPort = port;
        new Thread(() -> {
            try (Socket socket = new Socket(ip, finalPort);
                 FileInputStream fis = (FileInputStream) getContentResolver().openInputStream(selectedFileUri);
                 OutputStream os = socket.getOutputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }

                runOnUiThread(() -> logMessage("Fichier envoyé avec succès."));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> logMessage("Erreur lors de l'envoi : " + e.getMessage()));
            }
        }).start();
    }

    private void logMessage(String message) {
        tvLog.append("\n" + message);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void clearLoginFields() {
        etLogin.setText("");
        etPassword.setText("");
    }
}