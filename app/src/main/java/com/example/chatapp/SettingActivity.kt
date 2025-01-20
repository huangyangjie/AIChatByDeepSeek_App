package com.example.chatapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageButton
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var clearHistoryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)

        // 加载已保存的 API Key
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val savedApiKey = sharedPreferences.getString("API_KEY", "")
        apiKeyInput.setText(savedApiKey)

        // 保存按钮点击事件
        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString()
            if (apiKey.isNotEmpty()) {
                // 保存 API Key 到 SharedPreferences
                sharedPreferences.edit().putString("API_KEY", apiKey).apply()

                // 返回主界面
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
        backButton.setOnClickListener {
            finish()
        }
        clearHistoryButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            sharedPreferences.edit().remove("CHAT_HISTORY").apply()
            Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show()
        }
    }
}