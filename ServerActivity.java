package com.evan.sae32;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class ServerActivity extends AppCompatActivity {

    private TextView tvStatus, tvIp, tvPort;
    private Button btnStartServer, btnStopServer;

    private boolean isServerRunning = false;
    private static final int SERVER_PORT = 24689; // Port du serveur
    private String publicIP = "Récupération...";
    private ServerSocket serverSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        // Initialisation des vues
        tvStatus = findViewById(R.id.tvStatus);
        tvIp = findViewById(R.id.tvIp);
        tvPort = findViewById(R.id.tvPort);
        btnStartServer = findViewById(R.id.btnStartServer);
        btnStopServer = findViewById(R.id.btnStopServer);

        // Affichage initial de l'adresse IP
        tvIp.setText("Adresse IP : " + publicIP);
        tvPort.setText("Port : " + SERVER_PORT);

        // Récupération de l'IP publique
        new GetPublicIP().execute();

        // Gestion des boutons
        btnStartServer.setOnClickListener(v -> startServer());
        btnStopServer.setOnClickListener(v -> stopServer());
    }

    private void startServer() {
        if (publicIP != null && !publicIP.equals("Récupération...")) {
            if (!isServerRunning) {
                isServerRunning = true;
                tvStatus.setText("Statut du Serveur : Démarré");
                btnStartServer.setEnabled(false);
                btnStopServer.setEnabled(true);

                new Thread(() -> {
                    try {
                        serverSocket = new ServerSocket(SERVER_PORT);
                        Log.i("Server", "Le serveur est démarré sur " + publicIP + ":" + SERVER_PORT);

                        while (isServerRunning) {
                            Socket clientSocket = serverSocket.accept();
                            new Thread(() -> handleClient(clientSocket)).start();
                        }
                    } catch (IOException e) {
                        Log.e("Server", "Error starting server: " + e.getMessage());
                    }
                }).start();
            }
        } else {
            Toast.makeText(this, "L'adresse IP publique n'est pas encore disponible.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream();
             FileOutputStream fileOutputStream = new FileOutputStream("/sdcard/ReceivedFile.txt")) { // Ensure you have write permission or use an app-specific directory
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            Log.i("Server", "File received from client");
        } catch (IOException e) {
            Log.e("Server", "Error handling client connection: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e("Server", "Error closing socket: " + e.getMessage());
            }
        }
    }

    private void stopServer() {
        if (isServerRunning) {
            isServerRunning = false;
            tvStatus.setText("Statut du Serveur : Arrêté");
            btnStartServer.setEnabled(true);
            btnStopServer.setEnabled(false);
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e("Server", "Error stopping server: " + e.getMessage());
            }
        }
    }

    private class GetPublicIP extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL("https://api.ipify.org?format=json");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    JSONObject jsonObject = new JSONObject(stringBuilder.toString());
                    return jsonObject.getString("ip");
                } finally {
                    urlConnection.disconnect();
                }
            } catch (Exception e) {
                Log.e("Public IP", "Failed to fetch public IP", e);
                return "Erreur lors de la récupération de l'IP publique";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            publicIP = result;
            tvIp.setText("Adresse IP : " + publicIP);
        }
    }
}