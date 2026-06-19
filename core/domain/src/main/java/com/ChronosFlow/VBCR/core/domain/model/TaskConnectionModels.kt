package com.ChronosFlow.VBCR.core.domain.model

enum class ContactMethodKind {
    PHONE,
    EMAIL
}

data class TaskContactMethod(
    val id: String,
    val kind: ContactMethodKind,
    val label: String?,
    val value: String,
    val normalizedValue: String? = null,
    val isPrimary: Boolean = false
)

data class TaskContactSnapshot(
    val displayName: String,
    val lookupKey: String? = null,
    val methods: List<TaskContactMethod> = emptyList()
)

enum class TaskActionType {
    WEBSITE,
    DOCUMENT,
    PHONE,
    EMAIL,
    MAP,
    APP,
    CUSTOM_DEEP_LINK
}

data class TaskAction(
    val id: String,
    val type: TaskActionType,
    val label: String,
    val value: String,
    val isPrimary: Boolean = false
)

enum class TaskAttachmentKind {
    IMAGE,
    FILE
}

enum class TaskAttachmentStorageMode {
    LINKED,
    IMPORTED
}

data class TaskAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val kind: TaskAttachmentKind,
    val storageMode: TaskAttachmentStorageMode,
    val reference: String,
    val persistedUriPermission: Boolean = false,
    val isFeaturedImage: Boolean = false
)
