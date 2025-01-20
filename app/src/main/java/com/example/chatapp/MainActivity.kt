package com.example.chatapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable


class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var settingsButton: ImageButton // 新增设置按钮
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var deepSeekApiService: DeepSeekApiService
    private lateinit var apiKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        inputText = findViewById(R.id.inputText)
        sendButton = findViewById(R.id.sendButton)
        settingsButton = findViewById(R.id.settingsButton) // 绑定设置按钮

        // 设置 RecyclerView
        adapter = ChatAdapter(chatMessages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 初始化 Retrofit 和 DeepSeek API 服务
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/") // DeepSeek API 的基础 URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        deepSeekApiService = retrofit.create(DeepSeekApiService::class.java)

        // 从 SharedPreferences 中加载 API Key
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        apiKey = sharedPreferences.getString("API_KEY", "").orEmpty()
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

                // 调用 DeepSeek API 获取 AI 回复
                getAIResponse(userMessage)

                // 保存对话历史
                saveChatHistory()
            }
        }

        // 设置按钮点击事件
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun getAIResponse(userMessage: String) {
        if (apiKey.isEmpty()) {
            // 如果 API Key 为空，提示用户去设置
            chatMessages.add(ChatMessage("Please set your API Key in Settings.", false))
            adapter.notifyItemInserted(chatMessages.size - 1)
            return
        }

        val request = ChatRequest(
            model = "deepseek-chat", // 模型名称
            messages = listOf(Message(role = "user", content = userMessage))
        )

        val call = deepSeekApiService.getChatResponse("Bearer $apiKey", request)
        call.enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
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
                    chatMessages.add(ChatMessage("Error: ${response.message()}", false))
                    adapter.notifyItemInserted(chatMessages.size - 1)
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                // 处理失败
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
        if (json != null) {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val history: List<ChatMessage> = gson.fromJson(json, type)
            chatMessages.clear()
            chatMessages.addAll(history)
            adapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(chatMessages.size - 1)
        }
    }
}

// 数据类
data class ChatMessage(val message: String, val isUser: Boolean): Serializable

// Adapter
class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == 0) R.layout.item_message_user else R.layout.item_message_ai
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.message
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) 0 else 1
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
    }
}

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

// DeepSeek API 服务接口
interface DeepSeekApiService {
    @POST("v1/chat/completions")
    fun getChatResponse(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): Call<ChatResponse>
}