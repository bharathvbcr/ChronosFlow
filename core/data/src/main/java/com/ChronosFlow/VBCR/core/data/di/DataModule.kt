package com.ChronosFlow.VBCR.core.data.di

import android.content.Context
import androidx.room.Room
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.ChronosMockDataSeederInstaller
import com.ChronosFlow.VBCR.core.data.ChronosPlaintextDatabaseMigrator
import com.ChronosFlow.VBCR.core.data.ChronosSecureDatabaseProvider
import com.ChronosFlow.VBCR.core.data.dao.AlarmDao
import com.ChronosFlow.VBCR.core.data.dao.AppUsageDao
import com.ChronosFlow.VBCR.core.data.dao.AppUsageOverrideDao
import com.ChronosFlow.VBCR.core.data.dao.CalendarEventDao
import com.ChronosFlow.VBCR.core.data.dao.DayPlanDao
import com.ChronosFlow.VBCR.core.data.dao.FocusSessionDao
import com.ChronosFlow.VBCR.core.data.dao.GoalDao
import com.ChronosFlow.VBCR.core.data.dao.HabitEventDao
import com.ChronosFlow.VBCR.core.data.dao.HabitDao
import com.ChronosFlow.VBCR.core.data.dao.HabitScheduleDao
import com.ChronosFlow.VBCR.core.data.dao.JournalAttachmentDao
import com.ChronosFlow.VBCR.core.data.dao.JournalEntryDao
import com.ChronosFlow.VBCR.core.data.dao.MedicationDoseEventDao
import com.ChronosFlow.VBCR.core.data.dao.MedicationDao
import com.ChronosFlow.VBCR.core.data.dao.MedicationSafetyProfileDao
import com.ChronosFlow.VBCR.core.data.dao.MedicationScheduleDao
import com.ChronosFlow.VBCR.core.data.dao.MoodEnergyCheckInDao
import com.ChronosFlow.VBCR.core.data.dao.RecurrenceRuleDao
import com.ChronosFlow.VBCR.core.data.dao.ReviewDao
import com.ChronosFlow.VBCR.core.data.dao.RoutineDao
import com.ChronosFlow.VBCR.core.data.dao.SleepTrackDao
import com.ChronosFlow.VBCR.core.data.dao.TaskDao
import com.ChronosFlow.VBCR.core.data.dao.TaskScheduleDao
import com.ChronosFlow.VBCR.core.data.dao.TimeBlockDao
import com.ChronosFlow.VBCR.core.data.repository.AlarmRequestRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.CalendarEventRepositoryImpl
import com.ChronosFlow.VBCR.core.data.sync.CalendarSyncStatusStore
import com.ChronosFlow.VBCR.core.data.repository.DayPlanRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.DeviceCalendarPlatform
import com.ChronosFlow.VBCR.core.data.repository.FocusSessionRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.GoalRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.AppUsageRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.AppUsageOverrideRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.HabitRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.JournalRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.MedicationRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.MoodEnergyRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.PlannerPreferencesRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.RecurrenceRuleRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.ReviewRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.RoutineRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.SleepScheduleRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.SleepTrackRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.TaskRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.TaskScheduleRepositoryImpl
import com.ChronosFlow.VBCR.core.data.repository.TimeBlockRepositoryImpl
import com.ChronosFlow.VBCR.core.data.sync.SyncMutationNotifier
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.CalendarEventRepository
import com.ChronosFlow.VBCR.core.domain.repository.DayPlanRepository
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageRepository
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageOverrideRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import com.ChronosFlow.VBCR.core.domain.repository.PlannerPreferencesRepository
import com.ChronosFlow.VBCR.core.domain.repository.RecurrenceRuleRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
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
                ChronosDatabase.MIGRATION_15_16,
                ChronosDatabase.MIGRATION_16_17,
                ChronosDatabase.MIGRATION_17_18,
                ChronosDatabase.MIGRATION_18_19,
                ChronosDatabase.MIGRATION_19_20,
                ChronosDatabase.MIGRATION_20_21,
                ChronosDatabase.MIGRATION_21_22,
                ChronosDatabase.MIGRATION_22_23,
                ChronosDatabase.MIGRATION_23_24,
                ChronosDatabase.MIGRATION_24_25,
                ChronosDatabase.MIGRATION_25_26
            )
        )
            // Guard against opening a database written by a newer (uncommitted) schema:
            // Room cannot downgrade, so wipe and rebuild rather than crash on launch.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)

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
    fun provideGoalDao(db: ChronosDatabase) = db.goalDao()

    @Provides
    fun provideJournalEntryDao(db: ChronosDatabase) = db.journalEntryDao()

    @Provides
    fun provideJournalAttachmentDao(db: ChronosDatabase) = db.journalAttachmentDao()

    @Provides
    fun provideSleepTrackDao(db: ChronosDatabase) = db.sleepTrackDao()

    @Provides
    fun provideRoutineDao(db: ChronosDatabase) = db.routineDao()

    @Provides
    fun provideAppUsageDao(db: ChronosDatabase) = db.appUsageDao()

    @Provides
    @Singleton
    fun provideAppUsageRepository(dao: AppUsageDao): AppUsageRepository {
        return AppUsageRepositoryImpl(dao)
    }

    @Provides
    fun provideAppUsageOverrideDao(db: ChronosDatabase) = db.appUsageOverrideDao()

    @Provides
    @Singleton
    fun provideAppUsageOverrideRepository(dao: AppUsageOverrideDao): AppUsageOverrideRepository {
        return AppUsageOverrideRepositoryImpl(dao)
    }

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
        database: ChronosDatabase,
        taskDao: TaskDao,
        syncMutationNotifier: SyncMutationNotifier
    ): TaskRepository {
        return TaskRepositoryImpl(database, taskDao, syncMutationNotifier)
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
        db: ChronosDatabase,
        habitDao: HabitDao,
        habitScheduleDao: HabitScheduleDao,
        habitEventDao: HabitEventDao
    ): HabitRepository {
        return HabitRepositoryImpl(db, habitDao, habitScheduleDao, habitEventDao)
    }

    @Provides
    @Singleton
    fun provideMedicationRepository(
        db: ChronosDatabase,
        medicationDao: MedicationDao,
        medicationScheduleDao: MedicationScheduleDao,
        medicationSafetyProfileDao: MedicationSafetyProfileDao,
        medicationDoseEventDao: MedicationDoseEventDao
    ): MedicationRepository {
        return MedicationRepositoryImpl(
            db,
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
        deviceCalendarPlatform: DeviceCalendarPlatform,
        calendarSyncStatusStore: CalendarSyncStatusStore
    ): CalendarEventRepository {
        return CalendarEventRepositoryImpl(
            context,
            calendarEventDao,
            timeBlockDao,
            deviceCalendarPlatform,
            calendarSyncStatusStore
        )
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

    @Provides
    @Singleton
    fun provideGoalRepository(goalDao: GoalDao): GoalRepository {
        return GoalRepositoryImpl(goalDao)
    }

    @Provides
    @Singleton
    fun provideJournalRepository(journalEntryDao: JournalEntryDao, journalAttachmentDao: JournalAttachmentDao): JournalRepository {
        return JournalRepositoryImpl(journalEntryDao, journalAttachmentDao)
    }

    @Provides
    @Singleton
    fun provideSleepTrackRepository(sleepTrackDao: SleepTrackDao): SleepTrackRepository {
        return SleepTrackRepositoryImpl(sleepTrackDao)
    }

    @Provides
    @Singleton
    fun provideRoutineRepository(routineDao: RoutineDao): RoutineRepository {
        return RoutineRepositoryImpl(routineDao)
    }
}
