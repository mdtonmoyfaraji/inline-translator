package com.inlinetranslator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferencesManager(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var apiUrl by remember { mutableStateOf(prefs.apiUrl) }
                    var apiKey by remember { mutableStateOf(prefs.apiKey) }
                    var modelName by remember { mutableStateOf(prefs.modelName) }
                    var targetLang by remember { mutableStateOf(prefs.targetLang) }

                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Inline Translator", style = MaterialTheme.typography.headlineMedium)
                        Text("Configure your AI model & languages", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = { apiUrl = it },
                            label = { Text("AI API URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key (leave blank for local Ollama)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("Model Name (e.g. gpt-4o-mini, llama3)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = targetLang,
                            onValueChange = { targetLang = it },
                            label = { Text("Target Language (e.g. Spanish, German)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                prefs.apiUrl = apiUrl
                                prefs.apiKey = apiKey
                                prefs.modelName = modelName
                                prefs.targetLang = targetLang
                                Toast.makeText(this@MainActivity, "Settings Saved!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Configuration")
                        }
                    }
                }
            }
        }
    }
}
