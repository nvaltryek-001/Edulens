package com.example.ui.screens

import java.util.Locale

/**
 * EduLens college email policy.
 *
 * This is client-side input validation only.
 * The backend must independently verify identity and authorization.
 */
object CollegeEmailPolicy {
    private const val ALLOWED_DOMAIN = "saividya.ac.in"

    fun normalize(email: String): String =
        email.trim().lowercase(Locale.ROOT)

    fun isAllowedEmail(email: String): Boolean {
        val normalized = normalize(email)
        if (normalized.isBlank() || normalized.length > 254) return false
        if (normalized.any { it.isWhitespace() }) return false

        val at = normalized.lastIndexOf('@')
        if (at <= 0 || at != normalized.indexOf('@')) return false

        val local = normalized.substring(0, at)
        val domain = normalized.substring(at + 1)

        if (local.length > 64 || local.startsWith(".") ||
            local.endsWith(".") || ".." in local) return false

        return domain == ALLOWED_DOMAIN
    }

    fun validationMessage(email: String): String? =
        if (isAllowedEmail(email)) null
        else "Use your registered college email ending in @saividya.ac.in"
}
