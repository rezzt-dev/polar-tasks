package app.polar.di

import android.app.Application
import androidx.room.Room
import app.polar.data.AppDatabase
import app.polar.data.dao.SubtaskDao
import app.polar.data.dao.TaskDao
import app.polar.data.dao.TaskListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return AppDatabase.getDatabase(app)
    }

    @Provides
    @Singleton
    fun provideTaskDao(db: AppDatabase): TaskDao {
        return db.taskDao()
    }

    @Provides
    @Singleton
    fun provideTaskListDao(db: AppDatabase): TaskListDao {
        return db.taskListDao()
    }

    @Provides
    @Singleton
    fun provideSubtaskDao(db: AppDatabase): SubtaskDao {
        return db.subtaskDao()
    }

    @Provides
    @Singleton
    fun provideReminderDao(db: AppDatabase): app.polar.data.dao.ReminderDao {
        return db.reminderDao()
    }
}
