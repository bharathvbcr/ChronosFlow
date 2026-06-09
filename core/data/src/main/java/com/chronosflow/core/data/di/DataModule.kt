package com.chronosflow.core.data.di

import android.content.Context
import androidx.room.Room
import com.chronosflow.core.data.ChronosDatabase
import com.chronosflow.core.data.ChronosMockDataSeederInstaller
import com.chronosflow.core.data.ChronosPlaintextDatabaseMigrator
import com.chronosflow.core.data.ChronosSecureDatabaseProvider
import com.chronosflow.core.data.dao.AlarmDao
import com.chronosflow.core.data.dao.CalendarEventDao
import com.chronosflow.core.data.dao.DayPlanDao
import com.chronosflow.core.data.dao.FocusSessionDao
import com.chronosflow.core.data.dao.HabitEventDao
import com.chronosflow.core.data.dao.HabitDao
import com.chronosflow.core.data.dao.HabitScheduleDao
import com.chronosflow.core.data.dao.MedicationDoseEventDao
import com.chronosflow.core.data.dao.MedicationDao
import com.chronosflow.core.data.dao.MedicationSafetyProfileDao
import com.chronosflow.core.data.dao.MedicationScheduleDao
import com.chronosflow.core.data.dao.MoodEnergyCheckInDao
import com.chronosflow.core.data.dao.RecurrenceRuleDao
import com.chronosflow.core.data.dao.ReviewDao
import com.chronosflow.core.data.dao.TaskDao
import com.chronosflow.core.data.dao.TaskScheduleDao
import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.repository.AlarmRequestRepositoryImpl
import com.chronosflow.core.data.repository.CalendarEventRepositoryImpl
import com.chronosflow.core.data.repository.DayPlanRepositoryImpl
import com.chronosflow.core.data.repository.DeviceCalendarPlatform
import com.chronosflow.core.data.repository.FocusSessionRepositoryImpl
import com.chronosflow.core.data.repository.HabitRepositoryImpl
import com.chronosflow.core.data.repository.MedicationRepositoryImpl
import com.chronosflow.core.data.repository.MoodEnergyRepositoryImpl
import com.chronosflow.core.data.repository.PlannerPreferencesRepositoryImpl
import com.chronosflow.core.data.repository.RecurrenceRuleRepositoryImpl
import com.chronosflow.core.data.repository.ReviewRepositoryImpl
import com.chronosflow.core.data.repository.SleepScheduleRepositoryImpl
import com.chronosflow.core.data.repository.TaskRepositoryImpl
import com.chronosflow.core.data.repository.TaskScheduleRepositoryImpl
import com.chronosflow.core.data.repository.TimeBlockRepositoryImpl
import com.chronosflow.core.data.sync.SyncMutationNotifier
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.CalendarEventRepository
import com.chronosflow.core.domain.repository.DayPlanRepository
import com.chronosflow.core.domain.repository.FocusSessionRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.MoodEnergyRepository
import com.chronosflow.core.domain.repository.PlannerPreferencesRepository
import com.chronosflow.core.domain.repository.RecurrenceRuleRepository
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        secureProvider: ChronosSecureDatabaseProvider,
        migrator: ChronosPlaintextDatabaseMigrator
    ): ChronosDatabase {
        migrator.migrateIfNeeded()
        val builder = secureProvider.databaseBuilder(
            migrations = arrayOf(
                ChronosDatabase.MIGRATION_2_3,
                ChronosDatabase.MIGRATION_3_4,
                ChronosDatabase.MIGRATION_4_5,
                ChronosDatabase.MIGRATION_5_6,
                ChronosDatabase.MIGRATION_6_7,
                ChronosDatabase.MIGRATION_7_8,
                ChronosDatabase.MIGRATION_8_9,
                ChronosDatabase.MIGRATION_9_10,
                ChronosDatabase.MIGRATION_10_11,
                ChronosDatabase.MIGRATION_11_12,
                ChronosDatabase.MIGRATION_12_13,
                ChronosDatabase.MIGRATION_13_14,
                ChronosDatabase.MIGRATION_14_15,
                ChronosDatabase.MIGRATION_15_16
            )
        )

        ChronosMockDataSeederInstaller.installIfEnabled(builder)

        return secureProvider.create(builder)
    }

    @Provides
    fun provideTaskDao(db: ChronosDatabase) = db.taskDao()

    @Provides
    fun provideTaskScheduleDao(db: ChronosDatabase) = db.taskScheduleDao()

    @Provides
    fun provideTimeBlockDao(db: ChronosDatabase) = db.timeBlockDao()

    @Provides
    fun provideDayPlanDao(db: ChronosDatabase) = db.dayPlanDao()

    @Provides
    fun provideRecurrenceRuleDao(db: ChronosDatabase) = db.recurrenceRuleDao()

    @Provides
    fun provideHabitDao(db: ChronosDatabase) = db.habitDao()

    @Provides
    fun provideHabitScheduleDao(db: ChronosDatabase) = db.habitScheduleDao()

    @Provides
    fun provideHabitEventDao(db: ChronosDatabase) = db.habitEventDao()

    @Provides
    fun provideMedicationDao(db: ChronosDatabase) = db.medicationDao()

    @Provides
    fun provideMedicationScheduleDao(db: ChronosDatabase) = db.medicationScheduleDao()

    @Provides
    fun provideMedicationDoseEventDao(db: ChronosDatabase) = db.medicationDoseEventDao()

    @Provides
    fun provideMedicationSafetyProfileDao(db: ChronosDatabase) = db.medicationSafetyProfileDao()

    @Provides
    fun provideReviewDao(db: ChronosDatabase) = db.reviewDao()

    @Provides
    fun provideAlarmDao(db: ChronosDatabase) = db.alarmDao()

    @Provides
    fun provideFocusSessionDao(db: ChronosDatabase) = db.focusSessionDao()

    @Provides
    fun provideCalendarEventDao(db: ChronosDatabase) = db.calendarEventDao()

    @Provides
    fun provideMoodEnergyCheckInDao(db: ChronosDatabase) = db.moodEnergyCheckInDao()

    @Provides
    @Singleton
    fun provideDeviceCalendarPlatform(): DeviceCalendarPlatform {
        return DeviceCalendarPlatform()
    }

    @Provides
    @Singleton
    fun provideMoodEnergyRepository(dao: MoodEnergyCheckInDao): MoodEnergyRepository {
        return MoodEnergyRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun providePlannerPreferencesRepository(
        repository: PlannerPreferencesRepositoryImpl
    ): PlannerPreferencesRepository {
        return repository
    }

    @Provides
    @Singleton
    fun provideRecurrenceRuleRepository(recurrenceRuleDao: RecurrenceRuleDao): RecurrenceRuleRepository {
        return RecurrenceRuleRepositoryImpl(recurrenceRuleDao)
    }

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        syncMutationNotifier: SyncMutationNotifier
    ): TaskRepository {
        return TaskRepositoryImpl(taskDao, syncMutationNotifier)
    }

    @Provides
    @Singleton
    fun provideTaskScheduleRepository(taskScheduleDao: TaskScheduleDao): TaskScheduleRepository {
        return TaskScheduleRepositoryImpl(taskScheduleDao)
    }

    @Provides
    @Singleton
    fun provideTimeBlockRepository(
        timeBlockDao: TimeBlockDao,
        syncMutationNotifier: SyncMutationNotifier
    ): TimeBlockRepository {
        return TimeBlockRepositoryImpl(timeBlockDao, syncMutationNotifier)
    }

    @Provides
    @Singleton
    fun provideDayPlanRepository(dayPlanDao: DayPlanDao): DayPlanRepository {
        return DayPlanRepositoryImpl(dayPlanDao)
    }

    @Provides
    @Singleton
    fun provideHabitRepository(
        habitDao: HabitDao,
        habitScheduleDao: HabitScheduleDao,
        habitEventDao: HabitEventDao
    ): HabitRepository {
        return HabitRepositoryImpl(habitDao, habitScheduleDao, habitEventDao)
    }

    @Provides
    @Singleton
    fun provideMedicationRepository(
        medicationDao: MedicationDao,
        medicationScheduleDao: MedicationScheduleDao,
        medicationSafetyProfileDao: MedicationSafetyProfileDao,
        medicationDoseEventDao: MedicationDoseEventDao
    ): MedicationRepository {
        return MedicationRepositoryImpl(
            medicationDao,
            medicationScheduleDao,
            medicationSafetyProfileDao,
            medicationDoseEventDao
        )
    }

    @Provides
    @Singleton
    fun provideCalendarEventRepository(
        @ApplicationContext context: Context,
        calendarEventDao: CalendarEventDao,
        timeBlockDao: TimeBlockDao,
        deviceCalendarPlatform: DeviceCalendarPlatform
    ): CalendarEventRepository {
        return CalendarEventRepositoryImpl(context, calendarEventDao, timeBlockDao, deviceCalendarPlatform)
    }

    @Provides
    @Singleton
    fun provideReviewRepository(reviewDao: ReviewDao): ReviewRepository {
        return ReviewRepositoryImpl(reviewDao)
    }

    @Provides
    @Singleton
    fun provideSleepScheduleRepository(
        repository: SleepScheduleRepositoryImpl
    ): SleepScheduleRepository {
        return repository
    }

    @Provides
    @Singleton
    fun provideAlarmRequestRepository(alarmDao: AlarmDao): AlarmRequestRepository {
        return AlarmRequestRepositoryImpl(alarmDao)
    }

    @Provides
    @Singleton
    fun provideFocusSessionRepository(focusSessionDao: FocusSessionDao): FocusSessionRepository {
        return FocusSessionRepositoryImpl(focusSessionDao)
    }
}
