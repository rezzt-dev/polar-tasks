package app.polar.data.backup

import app.polar.data.backup.CsvImporter.Row
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvImporterTest {

    @Test
    fun `parses title and description per row`() {
        val rows = CsvImporter.parseRows("comprar pan,en la panaderia\nllamar a ana,\n")

        assertEquals(listOf(Row("comprar pan", "en la panaderia"), Row("llamar a ana", "")), rows)
    }

    @Test
    fun `skips a header row`() {
        val rows = CsvImporter.parseRows("title,description\nrevisar correo,bandeja de entrada")

        assertEquals(listOf(Row("revisar correo", "bandeja de entrada")), rows)
    }

    @Test
    fun `keeps commas, quotes and line breaks inside quoted cells`() {
        val csv = "\"pagar luz, agua\",\"dice \"\"urgente\"\"\"\r\n\"nota\",\"linea 1\nlinea 2\""

        val rows = CsvImporter.parseRows(csv)

        assertEquals(
            listOf(Row("pagar luz, agua", "dice \"urgente\""), Row("nota", "linea 1\nlinea 2")),
            rows
        )
    }

    @Test
    fun `ignores blank lines, rows without title and a utf-8 bom`() {
        val rows = CsvImporter.parseRows("\uFEFFtarea 1,a\n\n ,sin titulo\ntarea 2")

        assertEquals(listOf(Row("tarea 1", "a"), Row("tarea 2", "")), rows)
    }

    @Test
    fun `an empty file has no rows`() {
        assertEquals(emptyList<Row>(), CsvImporter.parseRows(""))
    }
}
