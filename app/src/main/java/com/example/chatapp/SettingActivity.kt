package com.example.chatapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var clearHistoryButton: Button
    private lateinit var modelSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        modelSpinner = findViewById(R.id.modelSpinner)

        //初始化模型选择器
        val models = arrayOf("deepseek-chat") // 可用的模型列表
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter

        // 加载已保存的 API Key
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val savedApiKey = sharedPreferences.getString("API_KEY", "")
        apiKeyInput.setText(savedApiKey)

        // 加载已保存的模型
        val savedModel = sharedPreferences.getString("MODEL", "deepseek-chat") // 默认模型
        val selectedIndex = models.indexOf(savedModel)
        if (selectedIndex >= 0) {
            modelSpinner.setSelection(selectedIndex)
        }

        // 保存按钮点击事件
        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString()
            if (apiKey.isNotEmpty()) {
                // 保存 API Key 和 选择的模型 到 SharedPreferences
                val selectedModel = modelSpinner.selectedItem as String
                val editor = sharedPreferences.edit()
                editor.putString("API_KEY", apiKey)
                editor.putString("MODEL", selectedModel) // 保存选择的模型
                editor.apply()

                Toast.makeText(this, "Settings were saved", Toast.LENGTH_SHORT).show()

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