package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.runtime.compositionLocalOf

enum class ScaffoldPage {
    Home,
    Secondary,
}

val LocalScaffoldPage = compositionLocalOf<ScaffoldPage?> { null }

/**
 * False while the page is hidden behind another page. Hidden pages stay composed, so animations
 * that never end should pause while this is false.
 */
val LocalScaffoldPageVisible = compositionLocalOf { true }
