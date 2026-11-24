package ru.maxchistogradov.moirashody

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = FinanceDatabase.get(this).dao()
        setContent { FinanceApp(dao) }
    }
}

private enum class Page(val label: String) {
    HOME("Главная"), RECEIPTS("Чеки"), ADD("Добавить"), STATS("Статистика"), SETTINGS("Настройки")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceApp(dao: FinanceDao) {
    var page by remember { mutableStateOf(Page.HOME) }
    val transactions by dao.observeTransactions().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val monthKey = remember {
        val c = Calendar.getInstance()
        "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }
    val monthTransactions = transactions.filter {
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date(it.dateMillis)) == monthKey
    }
    val income = monthTransactions.filter { it.isIncome }.sumOf { it.amountKopecks }
    val expenses = monthTransactions.filter { !it.isIncome }.sumOf { it.amountKopecks }
    val money: (Long) -> String = { "%,.2f ₽".format(Locale("ru", "RU"), it / 100.0).replace(',', ' ') }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF67D6B0),
        secondary = androidx.compose.ui.graphics.Color(0xFF9BA7FF),
        background = androidx.compose.ui.graphics.Color(0xFF101318),
        surface = androidx.compose.ui.graphics.Color(0xFF191E25)
    )) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Мои расходы", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    Page.values().forEach { p ->
                        val icon = when (p) {
                            Page.HOME -> Icons.Default.Home
                            Page.RECEIPTS -> Icons.Default.QrCodeScanner
                            Page.ADD -> Icons.Default.AddCircle
                            Page.STATS -> Icons.Default.BarChart
                            Page.SETTINGS -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = page == p,
                            onClick = { page = p },
                            icon = { Icon(icon, contentDescription = p.label) },
                            label = { Text(p.label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (page) {
                Page.HOME -> HomeScreen(
                    Modifier.padding(padding), money(income - expenses), money(income),
                    money(expenses), transactions.take(6), money
                )
                Page.RECEIPTS -> ReceiptsScreen(Modifier.padding(padding))
                Page.ADD -> AddScreen(Modifier.padding(padding)) { title, amount, isIncome, category ->
                    scope.launch {
                        dao.insertTransaction(
                            TransactionEntity(
                                title = title, amountKopecks = (amount * 100).toLong(),
                                isIncome = isIncome, category = category
                            )
                        )
                        page = Page.HOME
                    }
                }
                Page.STATS -> StatsScreen(Modifier.padding(padding), monthTransactions, money)
                Page.SETTINGS -> BudgetScreen(Modifier.padding(padding), dao, monthKey, money)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, balance: String, income: String, expenses: String,
    recent: List<TransactionEntity>, money: (Long) -> String
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Остаток за месяц", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(balance, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column { Text("Доходы", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(income, color = MaterialTheme.colorScheme.primary) }
                    Column { Text("Расходы", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(expenses, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Text("Последние операции", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (recent.isEmpty()) EmptyHint("Пока нет операций. Нажми «Добавить», чтобы внести первую.")
        recent.forEach { TransactionRow(it, money) }
    }
}

@Composable
private fun TransactionRow(item: TransactionEntity, money: (Long) -> String) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Medium)
                Text("${item.category} • ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(item.dateMillis))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text((if (item.isIncome) "+" else "−") + money(item.amountKopecks),
                color = if (item.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddScreen(modifier: Modifier, onSave: (String, Double, Boolean, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(DEFAULT_CATEGORIES.first()) }
    var expanded by remember { mutableStateOf(false) }
    val validAmount = amount.replace(',', '.').toDoubleOrNull()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Новая операция", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("Расход") })
            FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("Доход") })
        }
        OutlinedTextField(title, { title = it }, label = { Text("Название операции") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, ₽") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(category, {}, readOnly = true, label = { Text("Категория") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DEFAULT_CATEGORIES.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { category = c; expanded = false })
                }
            }
        }
        Button(
            onClick = { if (title.isNotBlank() && validAmount != null && validAmount > 0) onSave(title.trim(), validAmount, isIncome, category) },
            enabled = title.isNotBlank() && validAmount != null && validAmount > 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить операцию") }
    }
}

@Composable
private fun ReceiptsScreen(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Чеки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Сканирование QR — следующий этап")
                Text("В этой сборке раздел подготовлен. Для загрузки состава российского кассового чека потребуется подключить источник электронных чеков/ОФД. Сам QR обычно содержит фискальные реквизиты, а не список товаров.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null)
                Text("Фотография бумажного чека — следующий этап")
                Text("Добавим распознавание позиций и сумм, а затем проверку категорий перед сохранением.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatsScreen(modifier: Modifier, items: List<TransactionEntity>, money: (Long) -> String) {
    val groups = items.filter { !it.isIncome }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountKopecks } }.toList().sortedByDescending { it.second }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Статистика за месяц", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (groups.isEmpty()) EmptyHint("Пока нет расходов за этот месяц.")
        groups.forEach { (category, total) ->
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category)
                    Text(money(total), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BudgetScreen(modifier: Modifier, dao: FinanceDao, month: String, money: (Long) -> String) {
    val budgets by dao.observeBudgets(month).collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(DEFAULT_CATEGORIES.first()) }
    var amount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Месячные лимиты", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(category, {}, readOnly = true, label = { Text("Категория") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DEFAULT_CATEGORIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; expanded = false }) }
            }
        }
        OutlinedTextField(amount, { amount = it }, label = { Text("Лимит, ₽") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val value = amount.replace(',', '.').toDoubleOrNull()
            if (value != null && value > 0) scope.launch {
                dao.saveBudget(BudgetEntity(category, month, (value * 100).toLong()))
                amount = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить лимит") }
        Text("Установленные лимиты", style = MaterialTheme.typography.titleLarge)
        budgets.forEach { b ->
            Card { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(b.category); Text(money(b.limitKopecks), fontWeight = FontWeight.SemiBold)
            } }
        }
        if (budgets.isEmpty()) EmptyHint("Лимиты пока не заданы.")
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
