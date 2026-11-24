package ru.maxchistogradov.moirashody

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountKopecks: Long,
    val isIncome: Boolean,
    val category: String,
    val dateMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets", primaryKeys = ["category", "monthKey"])
data class BudgetEntity(val category: String, val monthKey: String, val limitKopecks: Long)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(item: TransactionEntity)

    @Query("SELECT * FROM budgets WHERE monthKey = :month")
    fun observeBudgets(month: String): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(item: BudgetEntity)
}

@Database(entities = [TransactionEntity::class, BudgetEntity::class], version = 1, exportSchema = false)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    companion object {
        @Volatile private var INSTANCE: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, FinanceDatabase::class.java, "moi_rashody.db"
            ).build().also { INSTANCE = it }
        }
    }
}

val DEFAULT_CATEGORIES = listOf(
    "Продукты", "Автомобиль", "Дом", "Бытовая химия",
    "Кафе и рестораны", "Одежда", "Здоровье", "Транспорт", "Развлечения", "Прочее"
)
