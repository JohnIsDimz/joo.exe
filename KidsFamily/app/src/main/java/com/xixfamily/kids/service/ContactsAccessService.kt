package com.xixfamily.kids.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import com.xixfamily.kids.network.SocketManager
import org.json.JSONArray
import org.json.JSONObject

class ContactsAccessService : Service() {

    companion object { private const val TAG = "ContactsAccess" }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "LIST") {
            Thread { readContacts() }.start()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun readContacts() {
        try {
            val contacts = JSONArray()
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            val seenContacts = mutableSetOf<Long>()
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val contactId = c.getLong(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                    val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "(No Name)"
                    val number = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    val type = c.getInt(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                    val photoUri = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI))

                    if (!seenContacts.contains(contactId)) {
                        seenContacts.add(contactId)
                        val typeLabel = when (type) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
                            else -> "Other"
                        }

                        contacts.put(JSONObject().apply {
                            put("id", contactId)
                            put("name", name)
                            put("number", number)
                            put("type", typeLabel)
                            put("hasPhoto", photoUri != null)
                        })
                    }
                }
            }

            if (SocketManager.getInstance().isSocketConnected()) {
                SocketManager.getInstance().emit("contacts:result", JSONObject().apply {
                    put("contacts", contacts)
                })
            }
            Log.d(TAG, "Sent ${contacts.length()} contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading contacts: ${e.message}")
            SocketManager.getInstance().emit("contacts:result", JSONObject().apply {
                put("contacts", JSONArray())
            })
        }
        stopSelf()
    }
}
