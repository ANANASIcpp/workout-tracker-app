package com.myapplication

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ==================== МОДЕЛИ ДАННЫХ ====================

data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    val restSeconds: Int
)

data class TrainingWeek(
    val id: String = UUID.randomUUID().toString(),
    val title: String, // Например: "Неделя 1: Тяжёлая"
    val days: MutableMap<String, MutableList<Exercise>> = mutableMapOf()
)

data class SetLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val weekTitle: String,
    val dayName: String,
    val exerciseName: String,
    val setNumber: Int,
    val totalSets: Int,
    val plannedReps: Int,
    val actualReps: Int,
    val plannedWeight: Float,
    val actualWeight: Float
)

data class AppStateData(
    val defaultRestSeconds: Int = 90,
    val geminiApiKey: String = "",
    val weeks: List<TrainingWeek> = defaultWeeks(),
    val logs: List<SetLogEntry> = emptyList()
)

val DAYS_OF_WEEK = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun defaultWeeks(): List<TrainingWeek> {
    val heavyDays = mutableMapOf<String, MutableList<Exercise>>()
    heavyDays["Пн"] = mutableListOf(
        Exercise(name = "Жим лёжа", sets = 4, reps = 6, weight = 80f, restSeconds = 120),
        Exercise(name = "Тяга штанги в наклоне", sets = 4, reps = 8, weight = 65f, restSeconds = 90),
        Exercise(name = "Жим гантелей сидя", sets = 3, reps = 8, weight = 22f, restSeconds = 90)
    )
    heavyDays["Ср"] = mutableListOf(
        Exercise(name = "Приседания со штангой", sets = 4, reps = 6, weight = 100f, restSeconds = 150),
        Exercise(name = "Румынская тяга", sets = 3, reps = 8, weight = 85f, restSeconds = 120)
    )
    heavyDays["Пт"] = mutableListOf(
        Exercise(name = "Подтягивания с весом", sets = 4, reps = 6, weight = 15f, restSeconds = 120),
        Exercise(name = "Брусья с весом", sets = 3, reps = 8, weight = 20f, restSeconds = 90)
    )

    val mediumDays = mutableMapOf<String, MutableList<Exercise>>()
    mediumDays["Пн"] = mutableListOf(
        Exercise(name = "Жим лёжа", sets = 4, reps = 10, weight = 67.5f, restSeconds = 90),
        Exercise(name = "Тяга верхнего блока", sets = 4, reps = 12, weight = 55f, restSeconds = 75)
    )

    val lightDays = mutableMapOf<String, MutableList<Exercise>>()
    lightDays["Пн"] = mutableListOf(
        Exercise(name = "Жим гантелей под углом", sets = 3, reps = 15, weight = 20f, restSeconds = 60),
        Exercise(name = "Разведения гантелей", sets = 3, reps = 15, weight = 10f, restSeconds = 60)
    )

    return listOf(
        TrainingWeek(title = "Неделя 1: Тяжёлая", days = heavyDays),
        TrainingWeek(title = "Неделя 2: Средняя", days = mediumDays),
        TrainingWeek(title = "Неделя 3: Лёгкая", days = lightDays)
    )
}

// ==================== ХРАНИЛИЩЕ (SHARED PREFERENCES + GSON) ====================

class StorageHelper(context: Context) {
    private val prefs = context.getSharedPreferences("workout_tracker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): AppStateData {
        val json = prefs.getString("app_state", null) ?: return AppStateData()
        return try {
            gson.fromJson(json, AppStateData::class.java) ?: AppStateData()
        } catch (e: Exception) {
            AppStateData()
        }
    }

    fun save(state: AppStateData) {
        prefs.edit().putString("app_state", gson.toJson(state)).apply()
    }
}

// ==================== ЗВУК И ВИБРАЦИЯ ====================

fun triggerTimerAlert(context: Context) {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 90)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
    } catch (_: Exception) {}

    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}

// ==================== GEMINI API АНАЛИЗАТОР ====================

suspend fun analyzeWorkoutWithGemini(apiKey: String, logs: List<SetLogEntry>): String {
    if (logs.isEmpty()) return "Журнал пуст. Выполните хотя бы один подход, чтобы запустить анализ."

    val recentLogs = logs.takeLast(60)
    val tableText = buildString {
        appendLine("Дата | Неделя | День | Упражнение | Подход | План (кг x повт) | Факт (кг x повт)")
        recentLogs.forEach {
            appendLine("${it.date} | ${it.weekTitle} | ${it.dayName} | ${it.exerciseName} | ${it.setNumber}/${it.totalSets} | ${it.plannedWeight}x${it.plannedReps} | ${it.actualWeight}x${it.actualReps}")
        }
    }

    if (apiKey.isBlank()) {
        val totalTonnage = recentLogs.sumOf { (it.actualWeight * it.actualReps).toDouble() }.toInt()
        val exercisesCount = recentLogs.map { it.exerciseName }.distinct().size
        val overPerformed = recentLogs.count { it.actualReps > it.plannedReps || it.actualWeight > it.plannedWeight }
        return """
            📊 ЛОКАЛЬНЫЙ ЭКСПРЕСС-АНАЛИЗ (без API-ключа)
            
            • Записано подходов: ${recentLogs.size} (упражнений: $exercisesCount)
            • Суммарный тоннаж: $totalTonnage кг
            • Подходов с перевыполнением плана: $overPerformed
            
            💡 Рекомендация:
            Если в тяжёлой неделе все подходы закрыты в целевом диапазоне повторений, на следующем цикле можно прибавить +2.5 кг на базовые движения. Для глубокого разбора от нейросети Gemini добавьте бесплатный API-ключ в Настройках (⚙️).
        """.trimIndent()
    }

    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey.trim()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 25000
            }

            val prompt = """
                Ты профессиональный спортивный тренер и аналитик силовых тренировок.
                Проанализируй таблицу выполненных подходов пользователя (учитывая периодизацию: Тяжёлая / Средняя / Лёгкая недели).
                Дай чёткий, структурированный разбор на русском языке без воды:
                1. Общая оценка нагрузки и тоннажа.
                2. Где есть прогресс, а где недовыполнение плана.
                3. Конкретные рекомендации по весам и восстановлению на следующую тренировку.
                
                Таблица тренировок:
                $tableText
            """.trimIndent()

            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }

            if (responseCode !in 200..299) {
                return@withContext "Ошибка Gemini API ($responseCode): проверьте правильность API-ключа в настройках."
            }

            val jsonResponse = JSONObject(responseText)
            val candidates = jsonResponse.optJSONArray("candidates")
            val text = candidates
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")

            text ?: "Не удалось получить текстовый ответ от модели."
        } catch (e: Exception) {
            "Ошибка сети при обращении к Gemini: ${e.localizedMessage}"
        }
    }
}

// ==================== ГЛАВНАЯ ACTIVITY ====================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = StorageHelper(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF22C55E),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    onPrimary = Color(0xFF0F172A),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                WorkoutApp(storage)
            }
        }
    }
}

// ==================== ОСНОВНОЙ ИНТЕРФЕЙС ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutApp(storage: StorageHelper) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appState by remember { mutableStateOf(storage.load()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedWeekIndex by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableStateOf("Пн") }

    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddWeekDialog by remember { mutableStateOf(false) }

    var isWorkoutActive by remember { mutableStateOf(false) }
    var activeExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var currentExerciseIdx by remember { mutableIntStateOf(0) }
    var currentSetNumber by remember { mutableIntStateOf(1) }
    var actualWeightInput by remember { mutableFloatStateOf(0f) }
    var actualRepsInput by remember { mutableIntStateOf(0) }

    var isResting by remember { mutableStateOf(false) }
    var restRemaining by remember { mutableIntStateOf(0) }
    var restTotal by remember { mutableIntStateOf(90) }

    var aiAnalysisResult by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }

    fun updateState(newState: AppStateData) {
        appState = newState
        storage.save(newState)
    }

    LaunchedEffect(isResting, restRemaining) {
        if (isResting && restRemaining > 0) {
            delay(1000L)
            restRemaining -= 1
            if (restRemaining == 0) {
                isResting = false
                triggerTimerAlert(context)
            }
        }
    }

    if (isWorkoutActive && activeExercises.isNotEmpty()) {
        val currentEx = activeExercises[currentExerciseIdx]
        val currentWeekTitle = appState.weeks.getOrNull(selectedWeekIndex)?.title ?: "Тренировка"

        ActiveWorkoutScreen(
            weekTitle = currentWeekTitle,
            dayName = selectedDay,
            exercise = currentEx,
            exerciseIndex = currentExerciseIdx,
            totalExercises = activeExercises.size,
            currentSet = currentSetNumber,
            actualWeight = actualWeightInput,
            actualReps = actualRepsInput,
            isResting = isResting,
            restRemaining = restRemaining,
            restTotal = restTotal,
            onWeightChange = { actualWeightInput = (actualWeightInput + it).coerceAtLeast(0f) },
            onRepsChange = { actualRepsInput = (actualRepsInput + it).coerceAtLeast(1) },
            onRestAdjust = { delta ->
                restRemaining = (restRemaining + delta).coerceAtLeast(5)
                restTotal = maxOf(restTotal, restRemaining)
            },
            onSkipRest = { isResting = false },
            onCompleteSet = {
                val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                val entry = SetLogEntry(
                    date = dateStr,
                    weekTitle = currentWeekTitle,
                    dayName = selectedDay,
                    exerciseName = currentEx.name,
                    setNumber = currentSetNumber,
                    totalSets = currentEx.sets,
                    plannedReps = currentEx.reps,
                    actualReps = actualRepsInput,
                    plannedWeight = currentEx.weight,
                    actualWeight = actualWeightInput
                )
                updateState(appState.copy(logs = appState.logs + entry))

                if (currentSetNumber < currentEx.sets) {
                    currentSetNumber += 1
                    restTotal = currentEx.restSeconds
                    restRemaining = currentEx.restSeconds
                    isResting = true
                } else if (currentExerciseIdx + 1 < activeExercises.size) {
                    currentExerciseIdx += 1
                    val nextEx = activeExercises[currentExerciseIdx]
                    currentSetNumber = 1
                    actualWeightInput = nextEx.weight
                    actualRepsInput = nextEx.reps
                    restTotal = currentEx.restSeconds
                    restRemaining = currentEx.restSeconds
                    isResting = true
                } else {
                    isWorkoutActive = false
                    isResting = false
                    selectedTab = 1
                }
            },
            onExitWorkout = {
                isWorkoutActive = false
                isResting = false
            }
        )
        return
    }

    val safeWeekIndex = selectedWeekIndex.coerceIn(0, (appState.weeks.size - 1).coerceAtLeast(0))
    val currentWeek = appState.weeks.getOrNull(safeWeekIndex)
    val dayExercises = currentWeek?.days?.get(selectedDay) ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Workout Tracker", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(
                            "Отдых по умолчанию: ${appState.defaultRestSeconds} сек",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E293B)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                    label = { Text("Программа") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                    label = { Text("Журнал и ИИ") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddExerciseDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить упражнение")
                }
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(appState.weeks) { index, week ->
                        FilterChip(
                            selected = index == safeWeekIndex,
                            onClick = { selectedWeekIndex = index },
                            label = { Text(week.title) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { showAddWeekDialog = true },
                            label = { Text("+ Неделя") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DAYS_OF_WEEK) { day ->
                        val hasExercises = (currentWeek?.days?.get(day)?.isNotEmpty() == true)
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text(if (hasExercises) "$day •" else day) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (dayExercises.isNotEmpty()) {
                    Button(
                        onClick = {
                            activeExercises = dayExercises.toList()
                            currentExerciseIdx = 0
                            currentSetNumber = 1
                            actualWeightInput = dayExercises[0].weight
                            actualRepsInput = dayExercises[0].reps
                            isResting = false
                            isWorkoutActive = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "СТАРТ ТРЕНИРОВКИ ($selectedDay)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (dayExercises.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "На $selectedDay упражнений пока нет.\nНажми «+» внизу, чтобы добавить.",
                            textAlign = TextAlign.Center,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        itemsIndexed(dayExercises) { idx, ex ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${idx + 1}. ${ex.name}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Подходы: ${ex.sets}  •  Повт: ${ex.reps}  •  Вес: ${ex.weight} кг  •  Отдых: ${ex.restSeconds}с",
                                            fontSize = 13.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val updatedWeeks = appState.weeks.mapIndexed { wIdx, w ->
                                            if (wIdx != safeWeekIndex) w
                                            else {
                                                val newDays = w.days.toMutableMap()
                                                val list = (newDays[selectedDay] ?: mutableListOf()).toMutableList()
                                                list.removeAt(idx)
                                                newDays[selectedDay] = list
                                                w.copy(days = newDays)
                                            }
                                        }
                                        updateState(appState.copy(weeks = updatedWeeks))
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "ИИ-Тренер Gemini",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Анализирует вашу таблицу подходов по неделям без промптов.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isAnalyzing = true
                                            aiAnalysisResult = analyzeWorkoutWithGemini(
                                                appState.geminiApiKey,
                                                appState.logs
                                            )
                                            isAnalyzing = false
                                        }
                                    },
                                    enabled = !isAnalyzing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isAnalyzing) "Анализирую..." else "✨ Анализ тренировки")
                                }

                                OutlinedButton(onClick = {
                                    val csv = buildString {
                                        appendLine("Дата;Неделя;День;Упражнение;Подход;Всего;План_кг;Факт_кг;План_повт;Факт_повт")
                                        appState.logs.forEach {
                                            appendLine("${it.date};${it.weekTitle};${it.dayName};${it.exerciseName};${it.setNumber};${it.totalSets};${it.plannedWeight};${it.actualWeight};${it.plannedReps};${it.actualReps}")
                                        }
                                    }
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "Таблица тренировок")
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Экспорт таблицы"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CSV")
                                }
                            }

                            if (aiAnalysisResult.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = aiAnalysisResult,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Таблица выполненных подходов (${appState.logs.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (appState.logs.isNotEmpty()) {
                            TextButton(onClick = {
                                updateState(appState.copy(logs = emptyList()))
                                aiAnalysisResult = ""
                            }) {
                                Text("Очистить", color = Color(0xFFEF4444))
                            }
                        }
                    }
                }

                if (appState.logs.isEmpty()) {
                    item {
                        Text(
                            "Записей пока нет. Завершите хотя бы один подход во время тренировки.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    items(appState.logs.reversed()) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.exerciseName, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Подход ${log.setNumber} из ${log.totalSets}",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${log.date} • ${log.weekTitle} (${log.dayName})",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    "Факт: ${log.actualWeight} кг × ${log.actualReps} повт. (План: ${log.plannedWeight} кг × ${log.plannedReps})",
                                    fontSize = 13.sp,
                                    color = Color(0xFF22C55E)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExerciseDialog) {
        var name by remember { mutableStateOf("") }
        var sets by remember { mutableStateOf("4") }
        var reps by remember { mutableStateOf("10") }
        var weight by remember { mutableStateOf("50") }
        var rest by remember { mutableStateOf(appState.defaultRestSeconds.toString()) }

        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            title = { Text("Новое упражнение ($selectedDay)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название (например, Жим лёжа)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sets,
                            onValueChange = { sets = it },
                            label = { Text("Подходы") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reps,
                            onValueChange = { reps = it },
                            label = { Text("Повторы") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("Вес (кг)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rest,
                            onValueChange = { rest = it },
                            label = { Text("Отдых (сек)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val newEx = Exercise(
                            name = name.trim(),
                            sets = sets.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                            reps = reps.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            weight = weight.toFloatOrNull() ?: 0f,
                            restSeconds = rest.toIntOrNull()?.coerceAtLeast(5) ?: appState.defaultRestSeconds
                        )
                        val updatedWeeks = appState.weeks.mapIndexed { idx, w ->
                            if (idx != safeWeekIndex) w
                            else {
                                val newDays = w.days.toMutableMap()
                                val list = (newDays[selectedDay] ?: mutableListOf()).toMutableList()
                                list.add(newEx)
                                newDays[selectedDay] = list
                                w.copy(days = newDays)
                            }
                        }
                        updateState(appState.copy(weeks = updatedWeeks))
                        showAddExerciseDialog = false
                    }
                }) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showSettingsDialog) {
        var restInput by remember { mutableStateOf(appState.defaultRestSeconds.toString()) }
        var keyInput by remember { mutableStateOf(appState.geminiApiKey) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Настройки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = restInput,
                        onValueChange = { restInput = it },
                        label = { Text("Время отдыха по умолчанию (сек)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60, 90, 120, 180).forEach { sec ->
                            AssistChip(
                                onClick = { restInput = sec.toString() },
                                label = { Text("${sec}с") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gemini API Key (необязательно)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newDefaultRest = restInput.toIntOrNull()?.coerceIn(10, 600) ?: 90
                    updateState(
                        appState.copy(
                            defaultRestSeconds = newDefaultRest,
                            geminiApiKey = keyInput.trim()
                        )
                    )
                    showSettingsDialog = false
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showAddWeekDialog) {
        var weekTitle by remember { mutableStateOf("Неделя ${appState.weeks.size + 1}: Тяжёлая") }

        AlertDialog(
            onDismissRequest = { showAddWeekDialog = false },
            title = { Text("Новая неделя цикла") },
            text = {
                OutlinedTextField(
                    value = weekTitle,
                    onValueChange = { weekTitle = it },
                    label = { Text("Название (Тяжёлая / Средняя / Лёгкая)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (weekTitle.isNotBlank()) {
                        val newWeeks = appState.weeks + TrainingWeek(title = weekTitle.trim())
                        updateState(appState.copy(weeks = newWeeks))
                        selectedWeekIndex = newWeeks.lastIndex
                        showAddWeekDialog = false
                    }
                }) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWeekDialog = false }) { Text("Отмена") }
            }
        )
    }
}

// ==================== ЭКРАН АКТИВНОЙ ТРЕНИРОВКИ И ТАЙМЕРА ====================

@Composable
fun ActiveWorkoutScreen(
    weekTitle: String,
    dayName: String,
    exercise: Exercise,
    exerciseIndex: Int,
    totalExercises: Int,
    currentSet: Int,
    actualWeight: Float,
    actualReps: Int,
    isResting: Boolean,
    restRemaining: Int,
    restTotal: Int,
    onWeightChange: (Float) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRestAdjust: (Int) -> Unit,
    onSkipRest: () -> Unit,
    onCompleteSet: () -> Unit,
    onExitWorkout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$weekTitle • $dayName", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    Text(
                        "Упражнение ${exerciseIndex + 1} из $totalExercises",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )
                }
                TextButton(onClick = onExitWorkout) {
                    Text("Завершить", color = Color(0xFFEF4444))
                }
            }

            if (isResting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ОТДЫХ МЕЖДУ ПОДХОДАМИ",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val minutes = restRemaining / 60
                    val seconds = restRemaining % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        fontSize = 68.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF38BDF8)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (restRemaining.toFloat() / restTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(8.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onRestAdjust(-15) }) { Text("-15 сек") }
                        OutlinedButton(onClick = { onRestAdjust(+15) }) { Text("+15 сек") }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Далее: ${exercise.name} — Подход $currentSet из ${exercise.sets}",
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onSkipRest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Пропустить отдых", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = exercise.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Подходы: $currentSet из ${exercise.sets}",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in 1..exercise.sets) {
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            s < currentSet -> Color(0xFF22C55E)
                                            s == currentSet -> Color(0xFF38BDF8)
                                            else -> Color(0xFF334155)
                                        }
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Вес (кг):", fontSize = 16.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(onClick = { onWeightChange(-2.5f) }) { Text("-2.5") }
                                    Text(
                                        "  $actualWeight кг  ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    FilledTonalButton(onClick = { onWeightChange(+2.5f) }) { Text("+2.5") }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Повторы:", fontSize = 16.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(onClick = { onRepsChange(-1) }) { Text("-1") }
                                    Text(
                                        "  $actualReps повт.  ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    FilledTonalButton(onClick = { onRepsChange(+1) }) { Text("+1") }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onCompleteSet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "ПОДХОД ВЫПОЛНЕН",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
