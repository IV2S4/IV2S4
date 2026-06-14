package com.aria.assistant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aria.assistant.R

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userContainer: View = view.findViewById(R.id.userContainer)
        val ariaContainer: View = view.findViewById(R.id.ariaContainer)
        val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
        val tvAriaMessage: TextView = view.findViewById(R.id.tvAriaMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        if (msg.isUser) {
            holder.userContainer.visibility = View.VISIBLE
            holder.ariaContainer.visibility = View.GONE
            holder.tvUserMessage.text = msg.text
        } else {
            holder.userContainer.visibility = View.GONE
            holder.ariaContainer.visibility = View.VISIBLE
            holder.tvAriaMessage.text = msg.text
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun getConversationHistory(): List<Pair<String, String>> {
        return messages.map { Pair(if (it.isUser) "user" else "assistant", it.text) }
    }

    fun isEmpty() = messages.isEmpty()
}
