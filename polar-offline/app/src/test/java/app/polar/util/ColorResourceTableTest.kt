package app.polar.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Comprueba la estructura de la tabla `resources.arsc` que carga [CustomColorResources]. */
class ColorResourceTableTest {

    private val colorType = 0x06
    private val entries = listOf(
        ColorResourceTable.Entry(0x7F060012, "custom_hex_accent_primary", 0xFF3A7BD5.toInt()),
        ColorResourceTable.Entry(0x7F060003, "custom_hex_bg_background", 0xFF1E1E2E.toInt())
    )
    private val table = ByteBuffer.wrap(ColorResourceTable.create("app.polar", entries)).order(ByteOrder.LITTLE_ENDIAN)

    private fun chunk(offset: Int) = Triple(
        table.getShort(offset).toInt() and 0xFFFF,
        table.getShort(offset + 2).toInt() and 0xFFFF,
        table.getInt(offset + 4)
    )

    @Test
    fun chunkSizesAddUp() {
        val (type, headerSize, size) = chunk(0)
        assertEquals(0x0002, type)
        assertEquals(12, headerSize)
        assertEquals(table.capacity(), size)
        assertEquals(1, table.getInt(8))

        val (poolType, _, poolSize) = chunk(12)
        assertEquals(0x0001, poolType)
        val packageOffset = 12 + poolSize
        val (packageType, packageHeader, packageSize) = chunk(packageOffset)
        assertEquals(0x0200, packageType)
        assertEquals(288, packageHeader)
        assertEquals(table.capacity(), packageOffset + packageSize)
        assertEquals(0x7F, table.getInt(packageOffset + 8))
        val name = String(CharArray(9) { table.getChar(packageOffset + 12 + it * 2) })
        assertEquals("app.polar", name)

        // Pools de tipos y de nombres, typeSpec y type, seguidos y en orden.
        var offset = packageOffset + packageHeader
        val types = mutableListOf<Int>()
        while (offset < packageOffset + packageSize) {
            val (chunkType, _, chunkSize) = chunk(offset)
            types += chunkType
            offset += chunkSize
        }
        assertEquals(listOf(0x0001, 0x0001, 0x0202, 0x0201), types)
        assertEquals(packageOffset + packageSize, offset)
    }

    @Test
    fun entriesAreSortedByIdWithTheirColors() {
        val typeOffset = findChunk(0x0201)
        assertEquals(colorType, table.get(typeOffset + 8).toInt())
        val entryCount = table.getInt(typeOffset + 12)
        assertEquals(0x12 + 1, entryCount)
        val entriesStart = typeOffset + table.getInt(typeOffset + 16)
        assertEquals(64, table.getInt(typeOffset + 20))

        val offsets = IntArray(entryCount) { table.getInt(typeOffset + 84 + it * 4) }
        assertEquals(0, offsets[0x03])
        assertEquals(16, offsets[0x12])
        assertEquals(-1, offsets[0x00])

        fun value(entryOffset: Int) = table.getInt(entriesStart + entryOffset + 12)
        fun dataType(entryOffset: Int) = table.get(entriesStart + entryOffset + 11).toInt()
        assertEquals(0xFF1E1E2E.toInt(), value(offsets[0x03]))
        assertEquals(0xFF3A7BD5.toInt(), value(offsets[0x12]))
        assertEquals(0x1C, dataType(offsets[0x03]))
    }

    @Test
    fun typeStringsNameTheColorType() {
        val packageOffset = 12 + chunk(12).third
        val poolOffset = packageOffset + 288
        val count = table.getInt(poolOffset + 8)
        assertEquals(colorType, count)
        val stringsStart = poolOffset + table.getInt(poolOffset + 20)
        val last = stringsStart + table.getInt(poolOffset + 28 + (count - 1) * 4)
        val length = table.getShort(last).toInt()
        val chars = CharArray(length) { table.getChar(last + 2 + it * 2) }
        assertArrayEquals("color".toCharArray(), chars)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsResourcesOutsideTheApp() {
        ColorResourceTable.create("app.polar", listOf(ColorResourceTable.Entry(0x01060000, "black", 0)))
    }

    private fun findChunk(type: Int): Int {
        val packageOffset = 12 + chunk(12).third
        var offset = packageOffset + 288
        while (chunk(offset).first != type) offset += chunk(offset).third
        return offset
    }
}
