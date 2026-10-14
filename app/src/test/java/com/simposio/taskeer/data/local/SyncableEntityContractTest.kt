package com.simposio.taskeer.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncableEntityContractTest {

    private val localRoot: File = resolveLocalRoot()

    @Test
    fun todaEntidadDelDatabase_implementaSyncableEntity() {
        val entities = declaredEntities()

        assertTrue("el @Database debe declarar al menos una entidad", entities.isNotEmpty())
        entities.forEach { entityClass ->
            assertTrue(
                "${entityClass.simpleName} debe implementar SyncableEntity",
                SyncableEntity::class.java.isAssignableFrom(entityClass),
            )
        }
    }

    @Test
    fun entidades_exponenLosCuatroAccesoresConElTipoEsperado() {
        declaredEntities().forEach { entityClass ->
            assertEquals(String::class.java, entityClass.getMethod("getId").returnType)
            assertEquals(Long::class.java, entityClass.getMethod("getUpdatedAt").returnType)
            assertEquals(Boolean::class.java, entityClass.getMethod("getPending").returnType)
            assertEquals(java.lang.Long::class.java, entityClass.getMethod("getDeletedAt").returnType)
        }
    }

    @Test
    fun idDeLasEntidades_noUsaAutoGenerate() {
        val entitySources = localRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("@Entity") }
            .toList()

        assertTrue("debe existir al menos una fuente con @Entity", entitySources.isNotEmpty())
        entitySources.forEach { file ->
            assertTrue(
                "${file.name} no debe generar el id en la base",
                !Regex("autoGenerate\\s*=\\s*true").containsMatchIn(file.readText()),
            )
        }
    }

    private fun declaredEntities(): List<Class<*>> {
        val databaseSource = File(localRoot, "AppDatabase.kt").readText()
        val declaration = Regex("entities\\s*=\\s*\\[([^\\]]*)\\]").find(databaseSource)
            ?: error("no encontré la lista entities en AppDatabase.kt")

        return Regex("(\\w+)::class")
            .findAll(declaration.groupValues[1])
            .map { Class.forName("$LOCAL_PACKAGE.${it.groupValues[1]}") }
            .toList()
    }

    private fun resolveLocalRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer/data/local"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }

    private companion object {
        const val LOCAL_PACKAGE = "com.simposio.taskeer.data.local"
    }
}
