package com.ChronosFlow.VBCR.feature.tasks

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun resolveTaskContactSnapshot(
    context: Context,
    contactUri: Uri
): TaskContactSnapshot? = withContext(Dispatchers.IO) {
    val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    )

    context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@withContext null
        val contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
        val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
        val displayName = cursor.getString(
            cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        ) ?: return@withContext null

        TaskContactSnapshot(
            displayName = displayName,
            lookupKey = lookupKey,
            methods = loadTaskContactMethods(context, contactId)
        )
    }
}

internal suspend fun resolveTaskContactSnapshotFromPickerSession(
    context: Context,
    sessionUri: Uri
): TaskContactSnapshot? = withContext(Dispatchers.IO) {
    val projection = arrayOf(
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.DATA1,
        ContactsContract.Data.DATA2,
        ContactsContract.Data.DATA3
    )
    val methodsByLookup = linkedMapOf<String, MutableList<TaskContactMethod>>()
    val namesByLookup = linkedMapOf<String, String>()

    context.contentResolver.query(sessionUri, projection, null, null, null)?.use { cursor ->
        val lookupIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
        val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val mimeTypeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
        val valueIndex = cursor.getColumnIndex(ContactsContract.Data.DATA1)
        val typeIndex = cursor.getColumnIndex(ContactsContract.Data.DATA2)
        val labelIndex = cursor.getColumnIndex(ContactsContract.Data.DATA3)

        while (cursor.moveToNext()) {
            val lookupKey = cursor.getStringOrNull(lookupIndex) ?: UUID.randomUUID().toString()
            val displayName = cursor.getStringOrNull(nameIndex).orEmpty()
            if (displayName.isNotBlank()) {
                namesByLookup[lookupKey] = displayName
            }
            val mimeType = cursor.getStringOrNull(mimeTypeIndex)
            val value = cursor.getStringOrNull(valueIndex)?.takeIf { it.isNotBlank() } ?: continue
            val label = resolvePickerSessionLabel(
                context = context,
                mimeType = mimeType,
                type = cursor.getIntOrNull(typeIndex),
                label = cursor.getStringOrNull(labelIndex)
            )
            val method = when (mimeType) {
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> TaskContactMethod(
                    id = UUID.randomUUID().toString(),
                    kind = ContactMethodKind.PHONE,
                    label = label,
                    value = value,
                    normalizedValue = value.filterNot(Char::isWhitespace),
                    isPrimary = methodsByLookup[lookupKey].orEmpty().none { it.kind == ContactMethodKind.PHONE }
                )
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> TaskContactMethod(
                    id = UUID.randomUUID().toString(),
                    kind = ContactMethodKind.EMAIL,
                    label = label,
                    value = value,
                    normalizedValue = value.lowercase(),
                    isPrimary = methodsByLookup[lookupKey].orEmpty().none { it.kind == ContactMethodKind.EMAIL }
                )
                else -> null
            }
            if (method != null) {
                methodsByLookup.getOrPut(lookupKey) { mutableListOf() }.add(method)
            }
        }
    }

    val lookupKey = methodsByLookup.keys.firstOrNull() ?: namesByLookup.keys.firstOrNull() ?: return@withContext null
    TaskContactSnapshot(
        displayName = namesByLookup[lookupKey]?.takeIf { it.isNotBlank() } ?: "Selected contact",
        lookupKey = lookupKey,
        methods = methodsByLookup[lookupKey].orEmpty()
    )
}

private fun resolvePickerSessionLabel(
    context: Context,
    mimeType: String?,
    type: Int?,
    label: String?
): String? {
    return when (mimeType) {
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
            ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                context.resources,
                type ?: ContactsContract.CommonDataKinds.Phone.TYPE_OTHER,
                label
            )?.toString()
        }
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
            ContactsContract.CommonDataKinds.Email.getTypeLabel(
                context.resources,
                type ?: ContactsContract.CommonDataKinds.Email.TYPE_OTHER,
                label
            )?.toString()
        }
        else -> null
    }
}

private fun loadTaskContactMethods(context: Context, contactId: String): List<TaskContactMethod> {
    return buildList {
        addAll(loadPhoneMethods(context, contactId))
        addAll(loadEmailMethods(context, contactId))
    }
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? {
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun android.database.Cursor.getIntOrNull(index: Int): Int? {
    return if (index >= 0 && !isNull(index)) getInt(index) else null
}

private fun loadPhoneMethods(context: Context, contactId: String): List<TaskContactMethod> {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.LABEL,
        ContactsContract.CommonDataKinds.Phone.TYPE,
        ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
    )
    val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
    val args = arrayOf(contactId)

    return buildList {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                ) ?: continue
                val label = resolvePhoneLabel(context, cursor)
                val isPrimary = cursor.getInt(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
                ) == 1
                add(
                    TaskContactMethod(
                        id = UUID.randomUUID().toString(),
                        kind = ContactMethodKind.PHONE,
                        label = label,
                        value = number,
                        normalizedValue = number.filterNot(Char::isWhitespace),
                        isPrimary = isPrimary
                    )
                )
            }
        }
    }
}

private fun loadEmailMethods(context: Context, contactId: String): List<TaskContactMethod> {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Email.ADDRESS,
        ContactsContract.CommonDataKinds.Email.LABEL,
        ContactsContract.CommonDataKinds.Email.TYPE,
        ContactsContract.CommonDataKinds.Email.IS_PRIMARY
    )
    val selection = "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?"
    val args = arrayOf(contactId)

    return buildList {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                ) ?: continue
                val label = resolveEmailLabel(context, cursor)
                val isPrimary = cursor.getInt(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.IS_PRIMARY)
                ) == 1
                add(
                    TaskContactMethod(
                        id = UUID.randomUUID().toString(),
                        kind = ContactMethodKind.EMAIL,
                        label = label,
                        value = address,
                        normalizedValue = address.lowercase(),
                        isPrimary = isPrimary
                    )
                )
            }
        }
    }
}

private fun resolvePhoneLabel(
    context: Context,
    cursor: android.database.Cursor
): String? {
    val label = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL))
    val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
    return ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, label)?.toString()
}

private fun resolveEmailLabel(
    context: Context,
    cursor: android.database.Cursor
): String? {
    val label = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.LABEL))
    val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE))
    return ContactsContract.CommonDataKinds.Email.getTypeLabel(context.resources, type, label)?.toString()
}
