package com.myapplication

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import java.util.UUID

// Цветовая палитра Nothing OS Light
private val BgPureWhite = Color(0xFFFFFFFF)
private val SurfaceCard = Color(0xFFF5F5F7)
private val SurfaceElevated = Color(0xFFEBEBEF)
private val BorderSubtle = Color(0xFFE2E2E5)
private val TextPrimary = Color(0xFF0A0A0C)
private val TextSecondary = Color(0xFF6E6E77)
private val NothingRed = Color(0xFFD71921)

data class ExerciseItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    val restSeconds: Int,
    val completedSets: Int = 0
)

data class WorkoutDay(
    val id: String,
    val title: String,
    val subtitle: String,
    val exercises: List<ExerciseItem>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val lightColorScheme = lightColorScheme(
                primary = NothingRed,
                onPrimary = Color.White,
                background = BgPureWhite,
                onBackground = TextPrimary,
                surface = SurfaceCard,
                onSurface = TextPrimary,
                surfaceVariant = SurfaceElevated,
                onSurfaceVariant = TextSecondary,
                outline = BorderSubtle
            )

            MaterialTheme(colorScheme = lightColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgPureWhite
                ) {
                    NothingWorkoutApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NothingWorkoutApp() {
    val context = LocalContext.current
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("nothing_workout_v2", Context.MODE_PRIVATE) }

    var workoutDays by remember {
        mutableStateOf(loadWorkouts(prefs, gson))
    }
    var selectedDayIndex by remember { mutableIntStateOf(0) }

    // Таймер отдыха
    var restRemaining by remember { mutableIntStateOf(0) }
    var restTotal by remember { mutableIntStateOf(90) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // Модальное окно редактирования / создания упражнения
    var editingExercise by remember { mutableStateOf<ExerciseItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    fun saveWorkouts(updated: List<WorkoutDay>) {
        workoutDays = updated
        prefs.edit().putString("workouts_json", gson.toJson(updated)).apply()
    }

    // Обратный отсчёт таймера
    LaunchedEffect(isTimerRunning, restRemaining) {
        if (isTimerRunning && restRemaining > 0) {
            delay(1000L)
            restRemaining -= 1
            if (restRemaining == 0) {
                isTimerRunning = false
                triggerFeedback(context)
            }
        }
    }

    val currentDay = workoutDays.getOrElse(selectedDayIndex) { workoutDays.first() }
    val totalSets = currentDay.exercises.sumOf { it.sets }
    val doneSets = currentDay.exercises.sumOf { it.completedSets }
    val progressRatio = if (totalSets > 0) doneSets.toFloat() / totalSets else 0f

    Scaffold(
        containerColor = BgPureWhite,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WORKOUT // OS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp,
                            color = TextPrimary
                        )
                    }

                    // Сброс прогресса дня
                    OutlinedButton(
                        onClick = {
                            val resetList = workoutDays.mapIndexed { idx, day ->
                                if (idx == selectedDayIndex) {
                                    day.copy(exercises = day.exercises.map { it.copy(completedSets = 0) })
                                } else day
                            }
                            saveWorkouts(resetList)
                            isTimerRunning = false
                            restRemaining = 0
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, BorderSubtle),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Сброс",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "СБРОС",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Выбор тренировочного дня
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(workoutDays.size) { index ->
                        val day = workoutDays[index]
                        val selected = index == selectedDayIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) TextPrimary else SurfaceCard)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) TextPrimary else BorderSubtle,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { selectedDayIndex = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = day.title.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isCreatingNew = true
                    editingExercise = ExerciseItem(
                        name = "",
                        sets = 3,
                        reps = 10,
                        weight = 20f,
                        restSeconds = 90
                    )
                },
                containerColor = NothingRed,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить упражнение")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Виджет прогресса и таймера отдыха
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = currentDay.subtitle.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$doneSets / $totalSets ПОДХОДОВ",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = TextPrimary
                                )
                            }

                            // Блок таймера
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isTimerRunning) NothingRed else BgPureWhite)
                                    .border(
                                        1.dp,
                                        if (isTimerRunning) NothingRed else BorderSubtle,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        if (isTimerRunning) {
                                            isTimerRunning = false
                                        } else {
                                            if (restRemaining == 0) restRemaining = restTotal
                                            isTimerRunning = true
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTimerRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = "Таймер",
                                    tint = if (isTimerRunning) Color.White else TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val displaySec = if (restRemaining > 0) restRemaining else restTotal
                                val mins = displaySec / 60
                                val secs = displaySec % 60
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isTimerRunning) Color.White else TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Точечный индикатор прогресса в стиле Nothing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val dotsCount = 24
                            val activeDots = (progressRatio * dotsCount).toInt()
                            for (i in 0 until dotsCount) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i < activeDots) NothingRed else BorderSubtle
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Список упражнений
            items(currentDay.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    onToggleSet = { setIndex ->
                        val newCompleted = if (exercise.completedSets == setIndex + 1) {
                            setIndex
                        } else {
                            setIndex + 1
                        }
                        val updated = workoutDays.mapIndexed { idx, day ->
                            if (idx == selectedDayIndex) {
                                day.copy(
                                    exercises = day.exercises.map {
                                        if (it.id == exercise.id) it.copy(completedSets = newCompleted) else it
                                    }
                                )
                            } else day
                        }
                        saveWorkouts(updated)
                        if (newCompleted > exercise.completedSets) {
                            restTotal = exercise.restSeconds
                            restRemaining = exercise.restSeconds
                            isTimerRunning = true
                        }
                    },
                    onEditClick = {
                        isCreatingNew = false
                        editingExercise = exercise
                    }
                )
            }
        }
    }

    // Диалог настройки упражнения
    editingExercise?.let { target ->
        ExerciseEditorSheet(
            exercise = target,
            isNew = isCreatingNew,
            onDismiss = { editingExercise = null },
            onSave = { savedItem ->
                val updated = workoutDays.mapIndexed { idx, day ->
                    if (idx == selectedDayIndex) {
                        val newList = if (isCreatingNew) {
                            day.exercises + savedItem
                        } else {
                            day.exercises.map { if (it.id == savedItem.id) savedItem else it }
                        }
                        day.copy(exercises = newList)
                    } else day
                }
                saveWorkouts(updated)
                editingExercise = null
            },
            onDelete = if (!isCreatingNew) {
                {
                    val updated = workoutDays.mapIndexed { idx, day ->
                        if (idx == selectedDayIndex) {
                            day.copy(exercises = day.exercises.filterNot { it.id == target.id })
                        } else day
                    }
                    saveWorkouts(updated)
                    editingExercise = null
                }
            } else null
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseItem,
    onToggleSet: (Int) -> Unit,
    onEditClick: () -> Unit
) {
    val isAllDone = exercise.sets > 0 && exercise.completedSets >= exercise.sets

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isAllDone) BgPureWhite else SurfaceCard,
        border = BorderStroke(1.dp, if (isAllDone) NothingRed.copy(alpha = 0.4f) else BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val weightLabel = if (exercise.weight % 1f == 0f) {
       
