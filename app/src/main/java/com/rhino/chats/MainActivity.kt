package com.rhino.chats

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var messageBoxTextView: TextView
    private lateinit var client: MyClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        messageBoxTextView = findViewById(R.id.messageBoxTextView)

        client = MyClient(statusTextView, messageBoxTextView)  { data ->
            runOnUiThread {
                appendMessage(data)
            }
        }
    }

    private fun appendMessage(message: String) {
        messageBoxTextView.append("$message\n")
    }
}