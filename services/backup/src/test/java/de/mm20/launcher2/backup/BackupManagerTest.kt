package de.mm20.launcher2.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
// Robolectric's Android 16 environment needs internal JDK classes that Java 21 doesn't export
@Config(sdk = [35])
class BackupManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()

    private class FakeComponent(private val name: String) : Backupable {
        var restored: String? = null

        override suspend fun backup(toDir: File) {
            File(toDir, name).writeText("data of $name")
        }

        override suspend fun restore(fromDir: File) {
            restored = File(fromDir, name).readText()
        }
    }

    /**
     * A document that a files app would provide, stored in [file]
     */
    private fun document(file: File): Uri {
        val uri = Uri.parse("content://test/${file.name}")
        shadowOf(context.contentResolver).apply {
            registerOutputStreamSupplier(uri) { file.outputStream() }
            registerInputStreamSupplier(uri) { file.inputStream() }
        }
        return uri
    }

    private suspend fun createBackup(file: File): Boolean =
        BackupManager(context, listOf(FakeComponent("a"), FakeComponent("b"))).backup(document(file))

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun metaEntry(): Pair<String, ByteArray> {
        val meta = """{"device":"test","timestamp":0,"format":"1.9","versionName":"1.0","components":[]}"""
        return "meta" to meta.toByteArray()
    }

    @Test
    fun restoresABackup() = runBlocking {
        val file = tmp.newFile("backup.kvaesitso")
        assertTrue(createBackup(file))

        val a = FakeComponent("a")
        val b = FakeComponent("b")
        val manager = BackupManager(context, listOf(a, b))
        val meta = manager.readBackupMeta(document(file))
        assertNotNull(meta)
        assertEquals(BackupCompatibility.Compatible, manager.checkCompatibility(meta!!))

        assertTrue(manager.restore(document(file)))
        assertEquals("data of a", a.restored)
        assertEquals("data of b", b.restored)
    }

    // A truncated backup (e.g. an incomplete download) crashed the restore. Its metadata can
    // still be read, so this is only noticed while extracting it.
    @Test
    fun doesNotRestoreATruncatedBackup() = runBlocking {
        val archive = zip(metaEntry(), "a" to Random(0).nextBytes(100_000))
        val file = tmp.newFile("truncated.kvaesitso")
        file.writeBytes(archive.copyOf(archive.size / 2))

        val a = FakeComponent("a")
        val manager = BackupManager(context, listOf(a))
        assertNotNull(manager.readBackupMeta(document(file)))
        assertFalse(manager.restore(document(file)))
        assertNull(a.restored)
    }

    // Entries like "../file" must not be written outside of the restore directory. (Android 14+
    // already rejects such entries when reading the archive, the host JVM doesn't.)
    @Test
    fun doesNotWriteOutsideOfTheRestoreDirectory() = runBlocking {
        val outside = File(context.cacheDir, "outside")
        outside.delete()
        val archive = zip(
            metaEntry(),
            "../outside" to "evil".toByteArray(),
            "a" to "data of a".toByteArray(),
        )
        val file = tmp.newFile("evil.kvaesitso")
        file.writeBytes(archive)

        BackupManager(context, listOf(FakeComponent("a"))).restore(document(file))
        assertFalse(outside.exists())
    }

    // Writing to a full storage (or a failing cloud storage app) crashed the app
    @Test
    fun reportsAFailedBackup() = runBlocking {
        val uri = Uri.parse("content://test/full")
        shadowOf(context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("No space left on device")
            }
        })
        assertFalse(BackupManager(context, listOf(FakeComponent("a"))).backup(uri))
    }
}
