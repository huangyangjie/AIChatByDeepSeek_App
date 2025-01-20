package com.example.chatapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

// Adapter
class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val context: Context
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val markwon: Markwon = Markwon.builder(context).build()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            0 -> R.layout.item_message_user // 用户消息
            1 -> R.layout.item_message_ai   // AI 消息
            2 -> R.layout.item_message_thinking // 正在思考消息
            else -> throw IllegalArgumentException("Invalid view type")
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        if (message.isUser || message.isThinking) {
            holder.messageText.text = message.message // 用户消息或“正在思考”消息直接显示
        } else {
            // 使用 Markwon 渲染 AI 消息
            val markdownText = message.message
            val spannedText: Spanned = markwon.toMarkdown(markdownText)
            holder.messageText.text = spannedText
        }

        // 设置长按事件监听器
        holder.itemView.setOnLongClickListener {
            // 复制消息内容到剪贴板
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Chat Message", message.message)
            clipboardManager.setPrimaryClip(clipData)

            // 显示提示
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return when {
            messages[position].isUser -> 0 // 用户消息
            messages[position].isThinking -> 2 // 正在思考消息
            else -> 1 // AI 消息
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
    }
}