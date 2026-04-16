package com.example.securesms.ui

/** Simple enum-style navigator used by [com.example.securesms.MainActivity]. */
sealed interface Screen {
    data object Roles : Screen
    data object Host : Screen
    data object Client : Screen
}
