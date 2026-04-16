package com.example.securesms.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire protocol types, represented as JSON. The payload field is opaque so the
 * transport layer (SecureChannel) can wrap it with encryption without caring
 * about the inner type.
 */

const val PROTOCOL_VERSION = 1

object MsgType {
    // Handshake (sent plaintext; contents are signed/hashed into session key)
    const val HELLO = "hello"
    const val HELLO_ACK = "hello_ack"
    // Post-handshake, encrypted
    const val LIST_CONVERSATIONS = "list_conversations"
    const val CONVERSATIONS = "conversations"
    const val LIST_MESSAGES = "list_messages"
    const val MESSAGES = "messages"
    const val NEW_MESSAGE = "new_message"
    const val PING = "ping"
    const val PONG = "pong"
    const val ERROR = "error"
}

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String?,
    val snippet: String,
    val lastTimestamp: Long,
    val unreadCount: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("threadId", threadId)
        put("address", address)
        put("displayName", displayName ?: JSONObject.NULL)
        put("snippet", snippet)
        put("lastTimestamp", lastTimestamp)
        put("unreadCount", unreadCount)
    }

    companion object {
        fun fromJson(o: JSONObject) = Conversation(
            threadId = o.getLong("threadId"),
            address = o.getString("address"),
            displayName = o.optString("displayName", "").ifEmpty { null }
                ?.takeIf { !o.isNull("displayName") },
            snippet = o.getString("snippet"),
            lastTimestamp = o.getLong("lastTimestamp"),
            unreadCount = o.optInt("unreadCount", 0)
        )
    }
}

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val inbound: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("threadId", threadId)
        put("address", address)
        put("body", body)
        put("timestamp", timestamp)
        put("inbound", inbound)
    }

    companion object {
        fun fromJson(o: JSONObject) = SmsMessage(
            id = o.getLong("id"),
            threadId = o.getLong("threadId"),
            address = o.getString("address"),
            body = o.getString("body"),
            timestamp = o.getLong("timestamp"),
            inbound = o.getBoolean("inbound")
        )
    }
}

/** Build a framed request or response. */
object Envelope {
    fun request(type: String, payload: JSONObject = JSONObject()): JSONObject =
        JSONObject().apply {
            put("v", PROTOCOL_VERSION)
            put("type", type)
            put("payload", payload)
        }

    fun conversationsResponse(list: List<Conversation>): JSONObject {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return request(MsgType.CONVERSATIONS, JSONObject().put("items", arr))
    }

    fun messagesResponse(threadId: Long, list: List<SmsMessage>): JSONObject {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return request(MsgType.MESSAGES, JSONObject()
            .put("threadId", threadId)
            .put("items", arr))
    }

    fun newMessage(msg: SmsMessage): JSONObject =
        request(MsgType.NEW_MESSAGE, msg.toJson())

    fun error(reason: String): JSONObject =
        request(MsgType.ERROR, JSONObject().put("reason", reason))
}

fun JSONObject.parseConversations(): List<Conversation> {
    val items = getJSONObject("payload").getJSONArray("items")
    return (0 until items.length()).map { Conversation.fromJson(items.getJSONObject(it)) }
}

fun JSONObject.parseMessages(): Pair<Long, List<SmsMessage>> {
    val payload = getJSONObject("payload")
    val items = payload.getJSONArray("items")
    val list = (0 until items.length()).map { SmsMessage.fromJson(items.getJSONObject(it)) }
    return payload.getLong("threadId") to list
}

fun JSONObject.parseNewMessage(): SmsMessage =
    SmsMessage.fromJson(getJSONObject("payload"))
