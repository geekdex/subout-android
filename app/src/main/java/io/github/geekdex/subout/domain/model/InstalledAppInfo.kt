package io.github.geekdex.subout.domain.model

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean
)
