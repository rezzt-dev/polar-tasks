package app.polar.data.sync

import app.polar.data.entity.Reminder
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityMappersTest {

    private val userId = "7a1e6b0d-2222-4b7b-9a6c-111122223333"
    private val listUuid = "b3c9e4a0-1111-4c1a-8a2b-444455556666"
    private val taskUuid = "0f2b6c2e-df6a-4e2a-9a3a-2f0a6a3e9b41"

    @Test
    fun `Task toDto converts CSV tags to a wire array`() {
        val task = Task(id = 1, listId = 10, title = "Pagar la luz", tags = "casa,urgente")

        val dto = task.toDto(userId, listUuid)

        assertEquals(listOf("casa", "urgente"), dto.tags)
        assertEquals(listUuid, dto.listId)
        assertEquals(userId, dto.userId)
    }

    @Test
    fun `Task toDto handles a blank tags CSV as an empty array, not one blank tag`() {
        val task = Task(id = 1, listId = 10, title = "Sin tags", tags = "")

        val dto = task.toDto(userId, listUuid)

        assertTrue(dto.tags.isEmpty())
    }

    @Test
    fun `TaskDto toEntity converts the wire tags array back to CSV and resolves the local listId`() {
        val dto = app.polar.data.sync.dto.TaskDto(
            id = taskUuid,
            userId = userId,
            listId = listUuid,
            title = "Comprar leche",
            tags = listOf("casa", "urgente"),
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val entity = dto.toEntity(localListId = 42L, localId = 7L)

        assertEquals("casa,urgente", entity.tags)
        assertEquals(42L, entity.listId)
        assertEquals(7L, entity.id)
        assertEquals(taskUuid, entity.uuid)
        assertFalse(entity.dirty)
    }

    @Test
    fun `TaskDto toEntity preserves the local-only imageUri cache instead of wiping it`() {
        val dto = app.polar.data.sync.dto.TaskDto(
            id = taskUuid,
            userId = userId,
            listId = listUuid,
            title = "Con imagen",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val local = Task(id = 5, listId = 1, title = "Con imagen", imageUri = "content://local/cache.jpg")
        val entity = dto.toEntity(localListId = 1L, localId = 5L, local = local)

        assertEquals("content://local/cache.jpg", entity.imageUri)
    }

    @Test
    fun `TaskDto toEntity preserves the device-only alert settings`() {
        val dto = app.polar.data.sync.dto.TaskDto(
            id = taskUuid, userId = userId, listId = listUuid, title = "Pagar la luz",
            dueDate = 9000L, createdAt = 1000L, updatedAt = 2000L
        )
        val local = Task(id = 5, listId = 1, title = "Pagar la luz", dueDate = 9000L, reminderOffset = 1440, snoozedUntil = 8000L)

        val entity = dto.toEntity(localListId = 1L, localId = 5L, local = local)

        assertEquals(1440, entity.reminderOffset)
        assertEquals(8000L, entity.snoozedUntil)
    }

    @Test
    fun `TaskDto toEntity drops a snooze made for a due date the server has changed`() {
        val dto = app.polar.data.sync.dto.TaskDto(
            id = taskUuid, userId = userId, listId = listUuid, title = "Pagar la luz",
            dueDate = 20_000L, createdAt = 1000L, updatedAt = 2000L
        )
        val local = Task(id = 5, listId = 1, title = "Pagar la luz", dueDate = 9000L, reminderOffset = 60, snoozedUntil = 8000L)

        val entity = dto.toEntity(localListId = 1L, localId = 5L, local = local)

        assertEquals(60, entity.reminderOffset)
        assertNull(entity.snoozedUntil)
    }

    @Test
    fun `TaskList toDto derives is_deleted from deletedAt since there is no local isDeleted column`() {
        val active = TaskList(id = 1, title = "Casa", deletedAt = null)
        val trashed = TaskList(id = 2, title = "Trabajo", deletedAt = 5000L)

        assertFalse(active.toDto(userId).isDeleted)
        assertTrue(trashed.toDto(userId).isDeleted)
        assertEquals(5000L, trashed.toDto(userId).deletedAt)
    }

    @Test
    fun `Subtask round-trips through dto preserving order and resolving the local taskId`() {
        val subtask = Subtask(id = 3, taskId = 99, title = "Sub 1", orderIndex = 2, createdAt = 1000L)

        val dto = subtask.toDto(userId, taskUuid)
        val backToEntity = dto.toEntity(localTaskId = 55L, localId = 3L)

        assertEquals(taskUuid, dto.taskId)
        assertEquals(2, dto.orderIndex)
        assertEquals(55L, backToEntity.taskId)
        assertEquals(2, backToEntity.orderIndex)
    }

    @Test
    fun `Reminder toDto and back preserves location fields untouched`() {
        val reminder = Reminder(
            id = 1,
            title = "Recuerdame",
            dateTime = 1000L,
            latitude = 40.4,
            longitude = -3.7,
            radius = 50f,
            locationName = "Casa"
        )

        val dto = reminder.toDto(userId)
        val backToEntity = dto.toEntity(localId = 1L)

        assertEquals(40.4, backToEntity.latitude)
        assertEquals(-3.7, backToEntity.longitude)
        assertEquals(50f, backToEntity.radius)
        assertEquals("Casa", backToEntity.locationName)
        assertNull(backToEntity.deletedAt)
    }

    @Test
    fun `ReminderDto toEntity preserves the device-only arrival alert`() {
        val local = Reminder(id = 1, title = "Comprar pan", dateTime = 1000L, latitude = 40.4, longitude = -3.7, notifyOnArrival = true)

        val backToEntity = local.toDto(userId).toEntity(localId = 1L, local = local)

        assertTrue(backToEntity.notifyOnArrival)
        assertFalse(local.toDto(userId).toEntity().notifyOnArrival)
    }
}
