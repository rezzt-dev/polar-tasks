package app.polar.util

import android.content.Context
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import java.io.ByteArrayOutputStream

/**
 * Colores a medida del tema personalizado en tiempo de ejecucion. Los temas y overlays
 * "portadores" (`Theme.Polar.Custom.Hex.*`, `ThemeOverlay.Polar.Custom.*.Hex`) apuntan a los
 * colores `custom_hex_*`; aqui se sustituye su valor en los recursos de cada activity con un
 * [ResourcesLoader] (android 11+). Asi todos los `?attr/` de layouts, drawables y selectores
 * resuelven el color elegido igual que si estuviera compilado.
 *
 * En android 10 o anterior, o si el sistema rechaza la tabla, [isSupported] es `false`: el editor
 * no ofrece colores a medida y `CustomThemePalette.sanitize()` los cambia por los de la paleta.
 */
object CustomColorResources {

    private const val TAG = "CustomColorResources"

    /** Fuerza el soporte en los tests de JVM, donde no hay version de android. */
    @VisibleForTesting
    @Volatile
    var supportedOverride: Boolean? = null

    @Volatile
    private var failed = false

    /** Ultima tabla creada: todas las activities comparten el mismo cargador mientras no cambien los colores. */
    private var cachedColors: Map<Int, Int>? = null
    private var cachedLoader: Any? = null

    /** El dispositivo admite colores a medida (android 11+ y sin fallos previos al cargarlos). */
    val isSupported: Boolean
        get() = supportedOverride ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !failed)

    /**
     * Sustituye en los recursos de [context] los colores de [colors] (id de recurso -> color). Se
     * llama antes de crear el tema de la activity. Devuelve `false` si no se pudo; desde entonces
     * [isSupported] es `false` y el tema se pinta con la paleta, sin dejar la pantalla a medias.
     */
    @Synchronized
    fun apply(context: Context, colors: Map<Int, Int>): Boolean {
        if (colors.isEmpty()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || failed) return false
        return try {
            val loader = cachedLoader.takeIf { cachedColors == colors }
                ?: Api30.createLoader(context, colors).also {
                    cachedColors = colors
                    cachedLoader = it
                }
            Api30.addLoader(context, loader)
            true
        } catch (e: Exception) {
            Log.e(TAG, "no se pudieron cargar los colores a medida", e)
            failed = true
            cachedColors = null
            cachedLoader = null
            false
        }
    }

    /** Todo lo que usa la api de android 11; el cargador viaja como `Any` fuera de aqui. */
    @RequiresApi(Build.VERSION_CODES.R)
    private object Api30 {
        fun addLoader(context: Context, loader: Any) {
            context.resources.addLoaders(loader as ResourcesLoader)
        }

        fun createLoader(context: Context, colors: Map<Int, Int>): ResourcesLoader {
            val resources = context.resources
            val entries = colors.map { (id, color) ->
                require(resources.getResourceTypeName(id) == "color") { "no es un color: $id" }
                ColorResourceTable.Entry(id, resources.getResourceEntryName(id), color)
            }
            val table = ColorResourceTable.create(context.packageName, entries)
            val fd = Os.memfd_create("polar_custom_colors", 0)
            try {
                var written = 0
                while (written < table.size) {
                    written += Os.write(fd, table, written, table.size - written)
                }
                return ParcelFileDescriptor.dup(fd).use { pfd ->
                    ResourcesLoader().apply { addProvider(ResourcesProvider.loadFromTable(pfd, null)) }
                }
            } finally {
                Os.close(fd)
            }
        }
    }
}

/**
 * Escribe una tabla de recursos (`resources.arsc`) que solo contiene colores del paquete de la
 * app, con su valor como `#AARRGGBB`. Es una version reducida de `ColorResourcesTableCreator` de
 * material components (apache 2.0, ver `THIRD_PARTY_NOTICES.md`), que la libreria no expone.
 * Formato: `frameworks/base/libs/androidfw/include/androidfw/ResourceTypes.h`.
 */
internal object ColorResourceTable {

    data class Entry(val id: Int, val name: String, val color: Int) {
        val packageId: Int get() = (id ushr 24) and 0xFF
        val typeId: Int get() = (id ushr 16) and 0xFF
        val entryId: Int get() = id and 0xFFFF
    }

    private const val APP_PACKAGE_ID = 0x7F

    private const val TYPE_STRING_POOL = 0x0001
    private const val TYPE_TABLE = 0x0002
    private const val TYPE_PACKAGE = 0x0200
    private const val TYPE_TYPE = 0x0201
    private const val TYPE_TYPE_SPEC = 0x0202

    private const val TABLE_HEADER_SIZE = 12
    private const val STRING_POOL_HEADER_SIZE = 28
    private const val PACKAGE_HEADER_SIZE = 288
    private const val TYPE_SPEC_HEADER_SIZE = 16
    private const val CONFIG_SIZE = 64
    private const val TYPE_HEADER_SIZE = 20 + CONFIG_SIZE
    private const val PACKAGE_NAME_LENGTH = 128

    private const val ENTRY_SIZE = 8
    private const val VALUE_SIZE = 8
    private const val FLAG_PUBLIC_ENTRY = 0x0002
    private const val SPEC_PUBLIC = 0x40000000
    private const val NO_ENTRY = -1
    private const val TYPE_INT_COLOR_ARGB8 = 0x1C
    private const val UTF8_FLAG = 0x100

    fun create(packageName: String, entries: List<Entry>): ByteArray {
        require(entries.isNotEmpty()) { "sin colores" }
        require(packageName.length < PACKAGE_NAME_LENGTH) { "nombre de paquete demasiado largo" }
        val sorted = entries.sortedBy { it.entryId }
        val typeId = sorted.first().typeId
        require(sorted.all { it.packageId == APP_PACKAGE_ID && it.typeId == typeId }) {
            "solo colores del paquete de la app"
        }
        require(sorted.map { it.entryId }.toSet().size == sorted.size) { "colores repetidos" }

        // Nombres de tipo: solo importa el de los colores; el resto son marcadores.
        val typeNames = List(typeId) { index -> if (index == typeId - 1) "color" else "?${index + 1}" }
        val typeStrings = stringPool(typeNames, utf8 = false)
        val keyStrings = stringPool(sorted.map { it.name }, utf8 = true)
        val types = typeChunks(typeId, sorted)

        val packageSize = PACKAGE_HEADER_SIZE + typeStrings.size + keyStrings.size + types.size
        val pkg = Chunk().apply {
            header(TYPE_PACKAGE, PACKAGE_HEADER_SIZE, packageSize)
            u32(APP_PACKAGE_ID)
            for (i in 0 until PACKAGE_NAME_LENGTH) u16(packageName.getOrNull(i)?.code ?: 0)
            u32(PACKAGE_HEADER_SIZE) // typeStrings
            u32(0) // lastPublicType
            u32(PACKAGE_HEADER_SIZE + typeStrings.size) // keyStrings
            u32(0) // lastPublicKey
            u32(0) // typeIdOffset
            bytes(typeStrings)
            bytes(keyStrings)
            bytes(types)
        }.toByteArray()

        val globalStrings = stringPool(emptyList(), utf8 = false)
        return Chunk().apply {
            header(TYPE_TABLE, TABLE_HEADER_SIZE, TABLE_HEADER_SIZE + globalStrings.size + pkg.size)
            u32(1) // packageCount
            bytes(globalStrings)
            bytes(pkg)
        }.toByteArray()
    }

    /** `ResTable_typeSpec` seguido de un `ResTable_type` con la configuracion por defecto. */
    private fun typeChunks(typeId: Int, sorted: List<Entry>): ByteArray {
        val entryCount = sorted.last().entryId + 1
        val present = sorted.associateBy { it.entryId }

        val spec = Chunk().apply {
            header(TYPE_TYPE_SPEC, TYPE_SPEC_HEADER_SIZE, TYPE_SPEC_HEADER_SIZE + entryCount * 4)
            u8(typeId); u8(0); u16(0)
            u32(entryCount)
            for (entryId in 0 until entryCount) u32(if (entryId in present) SPEC_PUBLIC else 0)
        }

        val entriesStart = TYPE_HEADER_SIZE + entryCount * 4
        val type = Chunk().apply {
            header(TYPE_TYPE, TYPE_HEADER_SIZE, entriesStart + sorted.size * (ENTRY_SIZE + VALUE_SIZE))
            u8(typeId); u8(0); u16(0)
            u32(entryCount)
            u32(entriesStart)
            u32(CONFIG_SIZE) // ResTable_config: solo su tamano, sin calificadores
            bytes(ByteArray(CONFIG_SIZE - 4))
            var offset = 0
            for (entryId in 0 until entryCount) {
                if (entryId in present) {
                    u32(offset)
                    offset += ENTRY_SIZE + VALUE_SIZE
                } else {
                    u32(NO_ENTRY)
                }
            }
            sorted.forEachIndexed { keyIndex, entry ->
                u16(ENTRY_SIZE); u16(FLAG_PUBLIC_ENTRY); u32(keyIndex)
                u16(VALUE_SIZE); u8(0); u8(TYPE_INT_COLOR_ARGB8); u32(entry.color)
            }
        }
        return spec.toByteArray() + type.toByteArray()
    }

    /** `ResStringPool` sin estilos, en UTF-16 o UTF-8. */
    private fun stringPool(strings: List<String>, utf8: Boolean): ByteArray {
        val encoded = strings.map { if (utf8) utf8String(it) else utf16String(it) }
        val dataSize = encoded.sumOf { it.size }
        val padding = (4 - dataSize % 4) % 4
        val stringsStart = STRING_POOL_HEADER_SIZE + strings.size * 4
        return Chunk().apply {
            header(TYPE_STRING_POOL, STRING_POOL_HEADER_SIZE, stringsStart + dataSize + padding)
            u32(strings.size)
            u32(0) // styleCount
            u32(if (utf8) UTF8_FLAG else 0)
            u32(stringsStart)
            u32(0) // stylesStart
            var offset = 0
            encoded.forEach { u32(offset); offset += it.size }
            encoded.forEach { bytes(it) }
            bytes(ByteArray(padding))
        }.toByteArray()
    }

    private fun utf16String(value: String): ByteArray = Chunk().apply {
        u16(value.length)
        value.forEach { u16(it.code) }
        u16(0)
    }.toByteArray()

    private fun utf8String(value: String): ByteArray {
        val data = value.toByteArray(Charsets.UTF_8)
        return utf8Length(value.length) + utf8Length(data.size) + data + byteArrayOf(0)
    }

    private fun utf8Length(length: Int): ByteArray =
        if (length > 0x7F) byteArrayOf((((length shr 8) and 0x7F) or 0x80).toByte(), (length and 0xFF).toByte())
        else byteArrayOf(length.toByte())

    /** Escritura little-endian de un bloque de la tabla. */
    private class Chunk {
        private val out = ByteArrayOutputStream()

        fun header(type: Int, headerSize: Int, size: Int) {
            u16(type); u16(headerSize); u32(size)
        }

        fun u8(value: Int) = out.write(value and 0xFF)

        fun u16(value: Int) {
            u8(value); u8(value shr 8)
        }

        fun u32(value: Int) {
            u16(value); u16(value shr 16)
        }

        fun bytes(value: ByteArray) = out.write(value)

        fun toByteArray(): ByteArray = out.toByteArray()
    }
}
