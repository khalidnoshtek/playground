package com.example.securesms.sms

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.Telephony
import com.example.securesms.protocol.Conversation
import com.example.securesms.protocol.SmsMessage

/**
 * Read-only accessor over the system SMS content provider.  The client never
 * receives anything we did not fetch here, and we never expose send or delete
 * operations through the network protocol.
 */
class SmsRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    @SuppressLint("Range")
    fun listConversations(limit: Int = 200): List<Conversation> {
        val uri = Telephony.Sms.Conversations.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.Conversations.THREAD_ID,
            Telephony.Sms.Conversations.SNIPPET,
            Telephony.Sms.Conversations.MESSAGE_COUNT
        )
        val out = mutableListOf<Conversation>()
        resolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
            while (cursor.moveToNext() && out.size < limit) {
                val threadId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(Telephony.Sms.Conversations.THREAD_ID))
                val snippet = cursor.getString(
                    cursor.getColumnIndexOrThrow(Telephony.Sms.Conversations.SNIPPET)) ?: ""
                val preview = previewMessageForThread(threadId) ?: continue
                val name = displayNameFor(preview.address)
                out += Conversation(
                    threadId = threadId,
                    address = preview.address,
                    displayName = name,
                    snippet = snippet.ifEmpty { preview.body.take(120) },
                    lastTimestamp = preview.timestamp,
                    unreadCount = countUnread(threadId)
                )
            }
        }
        return out
    }

    @SuppressLint("Range")
    fun listMessages(threadId: Long, limit: Int = 500): List<SmsMessage> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        val out = mutableListOf<SmsMessage>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < limit) {
                out += cursor.toSmsMessage()
            }
        }
        return out.asReversed()
    }

    @SuppressLint("Range")
    fun findById(messageId: Long): SmsMessage? {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms._ID} = ?",
            arrayOf(messageId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.toSmsMessage()
        }
        return null
    }

    @SuppressLint("Range")
    private fun previewMessageForThread(threadId: Long): SmsMessage? {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.toSmsMessage()
        }
        return null
    }

    private fun countUnread(threadId: Long): Int {
        val cursor = resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString()),
            null
        ) ?: return 0
        return cursor.use { it.count }
    }

    @SuppressLint("Range")
    private fun displayNameFor(address: String): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(address).build()
        return resolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst())
                c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            else null
        }
    }

    @SuppressLint("Range")
    private fun Cursor.toSmsMessage(): SmsMessage {
        val type = getInt(getColumnIndexOrThrow(Telephony.Sms.TYPE))
        val inbound = type == Telephony.Sms.MESSAGE_TYPE_INBOX
        return SmsMessage(
            id = getLong(getColumnIndexOrThrow(Telephony.Sms._ID)),
            threadId = getLong(getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
            address = getString(getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "",
            body = getString(getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
            timestamp = getLong(getColumnIndexOrThrow(Telephony.Sms.DATE)),
            inbound = inbound
        )
    }
}
