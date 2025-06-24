package com.example.chatapp

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlin.math.log

class MainActivity : AppCompatActivity() {

    private lateinit var inputContainer: View
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

        inputContainer = findViewById(R.id.inputContainer)
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
            .baseUrl(Config.BASEURL)
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

        // 监听键盘弹出和隐藏事件
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            if (imeHeight > 0) { // 键盘弹出
                // 调整输入框的位置
                inputContainer.translationY = -imeHeight.toFloat()
            } else { // 键盘隐藏
                // 恢复输入框的位置
                inputContainer.translationY = 0f
            }

            // 返回处理后的 WindowInsets
            insets
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
            model = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).getString("MODEL", "deepseek-chat").toString(),
            messages = listOf(Message(role = "user", content = userMessage)),
            stream = true,
            stream_options = StreamOptions()
        )
        Log.e("MainActivity", request.toString())
        val call = deepSeekApiService.getChatResponse("Bearer $apiKey", request)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (!response.isSuccessful) {
                    // 错误处理
                    chatMessages.add(ChatMessage("Error: ${response.message()}", false))
                    adapter.notifyItemInserted(chatMessages.size - 1)
                    return
                }

                val body = response.body()
                if (body == null) {
                    chatMessages.add(ChatMessage("Empty response", false))
                    adapter.notifyItemInserted(chatMessages.size - 1)
                    return
                }

                // 移除“正在思考”消息
                chatMessages.removeAt(chatMessages.size - 1)
                adapter.notifyItemRemoved(chatMessages.size)

                // 创建一个临时的 AI 消息对象用于逐步更新
                val aiMessage = StringBuilder()
                val tempMessage = ChatMessage("", isUser = false)
                chatMessages.add(tempMessage)
                val position = chatMessages.size - 1
                adapter.notifyItemInserted(position)

                // 使用缓冲字符流读取响应
                val source = body.source()
                source.request(Long.MAX_VALUE) // 请求全部数据
                val buffer = source.buffer()

                // 按行读取
                while (!source.exhausted()) {
                    val line = buffer.readUtf8LineStrict()
                    if (line.startsWith("data: ")) {
                        val json = line.substring(6).trim()
                        if (json == "[DONE]") break

                        try {
                            val gson = Gson()
                            val itemType = object : TypeToken<Choice>() {}.type
                            val choice = gson.fromJson<Choice>(json, itemType)
                            val content = choice.delta?.content ?: continue

                            aiMessage.append(content)
                            aiMessage.append(content)
                            tempMessage.message = aiMessage.toString()
                            adapter.notifyItemChanged(position)
                            recyclerView.scrollToPosition(position)

                        } catch (e: Exception) {
                            Log.e("StreamParseError", "Failed to parse: $json", e)
                        }
                    }
                }

                // 最终保存对话历史
                chatMessages[position] = ChatMessage(aiMessage.toString(), isUser = false)
                adapter.notifyItemChanged(position)
                saveChatHistory()
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                // 失败处理逻辑
                chatMessages.removeAt(chatMessages.size - 1)
                adapter.notifyItemRemoved(chatMessages.size)
                chatMessages.add(ChatMessage("Failure: ${t.message}", false))
                adapter.notifyItemInserted(chatMessages.size - 1)
            }
        } )

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
    var message: String,
    val isUser: Boolean,
    val isThinking: Boolean = false // 是否为“正在思考”消息
): Serializable

// DeepSeek API 请求和响应数据类
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean,
    val stream_options: StreamOptions
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val delta: Message
)

class StreamOptions(
    val streamOptions: Boolean = true
)