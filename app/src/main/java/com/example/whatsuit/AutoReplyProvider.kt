package com.example.whatsuit

import com.example.whatsuit.util.AutoReplyManager

interface AutoReplyProvider {
    fun getAutoReplyManager(): AutoReplyManager?
}
