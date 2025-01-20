package com.example.chatapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.EditText
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var settingsButton: ImageButton // 设置按钮
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var deepSeekApiService: DeepSeekApiService
    private lateinit var apiKey: String
    private lateinit var model: String // 选择的模型

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        inputText = findViewById(R.id.inputText)
        sendButton = findViewById(R.id.sendButton)
        settingsButton = findViewById(R.id.settingsButton) // 绑定设置按钮

        // 设置 RecyclerView
        adapter = ChatAdapter(chatMessages, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 初始化 Retrofit 和 DeepSeek API 服务
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 连接超时时间
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时时间
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时时间
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY // 记录请求和响应的详细信息
            })
            .build()

        // 初始化 Retrofit 和 DeepSeek API 服务
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        deepSeekApiService = retrofit.create(DeepSeekApiService::class.java)

        // 从 SharedPreferences 中加载 API Key
        loadChatHistory()

        // 发送消息
        sendButton.setOnClickListener {
            val userMessage = inputText.text.toString()
            if (userMessage.isNotEmpty()) {
                // 添加用户消息
                chatMessages.add(ChatMessage(userMessage, true))
                adapter.notifyItemInserted(chatMessages.size - 1)
                recyclerView.scrollToPosition(chatMessages.size - 1)
                inputText.text.clear()

                // 添加“正在思考”消息
                val thinkingMessage = ChatMessage("正在思考...", isUser = false, isThinking = true)
                chatMessages.add(thinkingMessage)
                adapter.notifyItemInserted(chatMessages.size - 1)
                recyclerView.scrollToPosition(chatMessages.size - 1)

                // 调用 DeepSeek API 获取 AI 回复
                getAIResponse(userMessage)
            }
        }

        // 设置按钮点击事件
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回 MainActivity 时重新加载聊天记录和设置
        loadSettings()
        loadChatHistory()
    }

    private fun getAIResponse(userMessage: String) {
        if (apiKey.isEmpty()) {
            // 移除“正在思考”消息
            chatMessages.removeAt(chatMessages.size - 1)
            adapter.notifyItemRemoved(chatMessages.size)

            // 如果 API Key 为空，提示用户去设置
            chatMessages.add(ChatMessage("Please set your API Key in Settings.", false))

            adapter.notifyItemInserted(chatMessages.size - 1)
            recyclerView.scrollToPosition(chatMessages.size - 1)
            return
        }

        val request = ChatRequest(
            model = "deepseek-chat", // 模型名称
            messages = listOf(Message(role = "user", content = userMessage))
        )

        val call = deepSeekApiService.getChatResponse("Bearer $apiKey", request)
        call.enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                // 移除“正在思考”消息
                chatMessages.removeAt(chatMessages.size - 1)
                adapter.notifyItemRemoved(chatMessages.size)

                if (response.isSuccessful) {
                    val aiMessage = response.body()?.choices?.firstOrNull()?.message?.content
                    if (aiMessage != null) {
                        // 添加 AI 回复
                        chatMessages.add(ChatMessage(aiMessage, false))
                        adapter.notifyItemInserted(chatMessages.size - 1)
                        recyclerView.scrollToPosition(chatMessages.size - 1)

                        // 保存对话历史
                        saveChatHistory()
                    }
                } else {
                    // 处理错误
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    Log.e("API_ERROR", errorMessage)
                    chatMessages.add(ChatMessage("Error: ${response.message()}", false))
                    adapter.notifyItemInserted(chatMessages.size - 1)
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                // 移除“正在思考”消息
                chatMessages.removeAt(chatMessages.size - 1)
                adapter.notifyItemRemoved(chatMessages.size)

                // 处理失败
                val errorMessage = if (t is java.net.SocketTimeoutException) {
                    "Failure: Timeout. Please check your network connection and try again."
                } else {
                    "Failure: ${t.message}"
                }
                Log.e("API_FAILURE", errorMessage, t)
                chatMessages.add(ChatMessage("Failure: ${t.message}", false))
                adapter.notifyItemInserted(chatMessages.size - 1)
            }
        })
    }
    private fun saveChatHistory() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(chatMessages) // 将对话历史转换为 JSON 字符串
        editor.putString("CHAT_HISTORY", json)
        editor.apply()
    }

    private fun loadChatHistory() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("CHAT_HISTORY", null)
        chatMessages.clear() // 清空当前聊天记录
        if (json != null) {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val history: List<ChatMessage> = gson.fromJson(json, type)
            chatMessages.addAll(history)
        }
        adapter.notifyDataSetChanged() // 刷新 RecyclerView
        recyclerView.scrollToPosition(chatMessages.size - 1) // 滚动到最后一条消息
    }

    private fun loadSettings() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        apiKey = sharedPreferences.getString("API_KEY", "").orEmpty()
        model = sharedPreferences.getString("MODEL", "deepseek-chat").orEmpty() // 加载选择的模型
    }
}

// 数据类
data class ChatMessage(
    val message: String,
    val isUser: Boolean,
    val isThinking: Boolean = false // 是否为“正在思考”消息
): Serializable

// DeepSeek API 请求和响应数据类
data class ChatRequest(
    val model: String,
    val messages: List<Message>
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

