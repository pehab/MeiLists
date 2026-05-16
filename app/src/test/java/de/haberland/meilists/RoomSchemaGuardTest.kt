package de.haberland.meilists

import de.haberland.meilists.model.APP_DATABASE_VERSION
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RoomSchemaGuardTest {
    @Test
    fun exportedRoomSchemaMatchesGuardedCurrentVersion() {
        assertEquals(
            "If the Room schema changed intentionally, bump APP_DATABASE_VERSION and add a migration.",
            EXPECTED_DATABASE_VERSION,
            APP_DATABASE_VERSION
        )

        val schemaFile = currentSchemaFile()
        assertEquals(
            "Room schema changed for version $APP_DATABASE_VERSION. If intentional, bump the database version, add a migration, regenerate app/schemas, and update this guard.",
            EXPECTED_SCHEMA_SHA256,
            schemaFile.sha256()
        )
    }

    private fun currentSchemaFile(): File {
        val schemaPath = "schemas/de.haberland.meilists.model.AppDatabase/$APP_DATABASE_VERSION.json"
        val userDir = requireNotNull(System.getProperty("user.dir")) { "Missing user.dir system property" }
        val candidates = generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .flatMap { dir ->
                sequenceOf(
                    File(dir, schemaPath),
                    File(dir, "app/$schemaPath")
                )
            }
            .toList()

        val schemaFile = candidates.firstOrNull { it.isFile }
        if (schemaFile != null) {
            return schemaFile
        }

        fail("Missing exported Room schema for version $APP_DATABASE_VERSION. Run .\\gradlew.bat kspDebugKotlin and commit app/schemas.")
        throw AssertionError("Unreachable")
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val EXPECTED_DATABASE_VERSION = 13
        const val EXPECTED_SCHEMA_SHA256 = "9e153104e999b2d69df6eabc03d157730a6d40d21ca5757b4aecc5f6b1f4e819"
    }
}
