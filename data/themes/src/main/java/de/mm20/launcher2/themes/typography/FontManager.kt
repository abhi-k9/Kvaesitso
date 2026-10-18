package de.mm20.launcher2.themes.typography

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class FontList(
    val builtIn: List<FontFamily>,
    val generic: List<FontFamily>,
    val deviceDefault: List<FontFamily>,
    val system: List<FontFamily>,
    /**
     * Fonts that the user has imported
     */
    val custom: List<FontFamily> = emptyList(),
)

data class VariableFontAxis(
    val name: String,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val defaultValue: Float,
)

class FontManager(
    private val context: Context
) {
    fun getInstalledFonts(): FontList {
        val deviceHeadlineResId = context.resources
            .getIdentifier("config_headlineFontFamily", "string", "android")
        val deviceBodyResId = context.resources
            .getIdentifier("config_bodyFontFamily", "string", "android")

        val deviceHeadlineExists = deviceHeadlineResId != 0 &&
                context.resources.getString(deviceHeadlineResId).isNotBlank()

        val deviceBodyExists = deviceBodyResId != 0 &&
                context.resources.getString(deviceBodyResId).isNotBlank()
        return FontList(
            builtIn = listOf(FontFamily.LauncherDefault()),
            generic = listOf(
                FontFamily.SansSerif,
                FontFamily.Serif,
                FontFamily.Monospace,
            ),
            deviceDefault = listOfNotNull(
                if (deviceHeadlineExists) FontFamily.DeviceHeadline else null,
                if (deviceBodyExists) FontFamily.DeviceBody else null,
            ),
            system = emptyList(),
            custom = getCustomFonts(),
        )
    }

    fun getFontSettings(font: FontFamily.VariableFontFamily): List<VariableFontAxis> {
        if (font is FontFamily.LauncherDefault) {
            return listOf(
                VariableFontAxis("wdth", "Width", 25f..151f, 1f, 100f),
                VariableFontAxis("ROND", "Roundness", 0f..100f, 1f, 0f),
                VariableFontAxis("GRAD", "Grade", 0f..100f, 1f, 0f),
            )
        }

        return emptyList()
    }

    private val customFontsDir: File
        get() = File(context.filesDir, CustomFontsDir)

    /**
     * The fonts that the user has imported, by name
     */
    fun getCustomFonts(): List<FontFamily.Custom> {
        return customFontsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in FontExtensions }
            ?.sortedBy { it.name.lowercase() }
            ?.map { FontFamily.Custom(it.name) }
            ?: emptyList()
    }

    /**
     * The file of an imported font, or null if it doesn't exist (anymore)
     */
    fun getCustomFontFile(font: FontFamily.Custom): File? {
        // Only file names: the name comes from the typography settings, which can be restored
        // from a backup
        if (font.file != File(font.file).name) return null
        return File(customFontsDir, font.file).takeIf { it.isFile }
    }

    /**
     * Copies the font (.ttf or .otf) at [uri] to the launcher's fonts, so that it can be used in
     * the typography settings.
     *
     * @return the imported font, or null if the file couldn't be read or isn't a font
     */
    suspend fun importFont(uri: Uri): FontFamily.Custom? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: RuntimeException) {
            null
        }
        val extension = displayName?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { it in FontExtensions } ?: "ttf"
        val name = (displayName?.substringBeforeLast('.') ?: "")
            .replace(Regex("[^\\p{L}\\p{N} ._-]"), "")
            .trim(' ', '.')
            .take(64)
            .ifEmpty { "Font" }

        val tempFile = File.createTempFile("font", ".$extension", context.cacheDir)
        try {
            val input = resolver.openInputStream(uri) ?: return@withContext null
            input.use {
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MaxFontSize) return@withContext null
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (Typeface.Builder(tempFile).build() == null) return@withContext null

            customFontsDir.mkdirs()
            var target = File(customFontsDir, "$name.$extension")
            var number = 2
            while (target.exists()) {
                target = File(customFontsDir, "$name $number.$extension")
                number++
            }
            tempFile.copyTo(target)
            FontFamily.Custom(target.name)
        } catch (e: IOException) {
            Log.e("FontManager", "Couldn't import font", e)
            null
        } catch (e: SecurityException) {
            Log.e("FontManager", "Couldn't import font", e)
            null
        } finally {
            tempFile.delete()
        }
    }

    companion object {
        /**
         * Where imported fonts are stored, in the app's files directory
         */
        const val CustomFontsDir = "fonts"
        val FontExtensions = setOf("ttf", "otf")
        private const val MaxFontSize = 32L * 1024 * 1024
    }
}
