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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
            val scheme = lightColorScheme(
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
            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgPureWhite) {
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

    val workoutDaysState = remember { mutableStateOf(loadWorkouts(prefs, gson)) }
    val selectedDayIndexState = remember { mutableStateOf(0) }

    val restRemainingState = remember { mutableStateOf(0) }
    val restTotalState = remember { mutableStateOf(90) }
    val isTimerRunningState = remember { mutableStateOf(false) }

    val editingExerciseState = remember { mutableStateOf<ExerciseItem?>(null) }
    val isCreatingNewState = remember { mutableStateOf(false) }

    val workoutDays = workoutDaysState.value
    val selectedDayIndex = selectedDayIndexState.value
    val restRemaining = restRemainingState.value
    val restTotal = restTotalState.value
    val isTimerRunning = isTimerRunningState.value

    fun saveWorkouts(updated: List<WorkoutDay>) {
        workoutDaysState.value = updated
        prefs.edit().putString("workouts_json", gson.toJson(updated)).apply()
    }

    LaunchedEffect(isTimerRunning, restRemaining) {
        if (isTimerRunning && restRemaining > 0) {
            delay(1000L)
            val next = restRemaining - 1
            restRemainingState.value = next
            if (next == 0) {
                isTimerRunningState.value = false
                triggerFeedback(context)
            }
        }
    }

    val currentDay = workoutDays.getOrElse(selectedDayIndex) { workoutDays.first() }
    val totalSets = currentDay.exercises.sumOf { ex -> ex.sets }
    val doneSets = currentDay.exercises.sumOf { ex -> ex.completedSets }
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

                    OutlinedButton(
                        onClick = {
                            val resetList = workoutDays.mapIndexed { idx, day ->
                                if (idx == selectedDayIndex) {
                                    day.copy(exercises = day.exercises.map { ex -> ex.copy(completedSets = 0) })
                                } else {
                                    day
                                }
                            }
                            saveWorkouts(resetList)
                            isTimerRunningState.value = false
                            restRemainingState.value = 0
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

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                .clickable { selectedDayIndexState.value = index }
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
                    isCreatingNewState.value = true
                    editingExerciseState.value = ExerciseItem(
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
                                            isTimerRunningState.value = false
                                        } else {
                                            if (restRemaining == 0) {
                                                restRemainingState.value = restTotal
                                            }
                                            isTimerRunningState.value = true
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
                                        .background(if (i < activeDots) NothingRed else BorderSubtle)
                                )
                            }
                        }
                    }
                }
            }
                        items(currentDay.exercises.size) { index ->
                val exercise = currentDay.exercises[index]
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
                                    exercises = day.exercises.map { ex ->
                                        if (ex.id == exercise.id) ex.copy(completedSets = newCompleted) else ex
                                    }
                                )
                            } else {
                                day
                            }
                        }
                        saveWorkouts(updated)
                        if (newCompleted > exercise.completedSets) {
                            restTotalState.value = exercise.restSeconds
                            restRemainingState.value = exercise.restSeconds
                            isTimerRunningState.value = true
                        }
                    },
                    onEditClick = {
                        isCreatingNewState.value = false
                        editingExerciseState.value = exercise
                    }
                )
            }
        }
    }

    val targetExercise = editingExerciseState.value
    if (targetExercise != null) {
        ExerciseEditorSheet(
            exercise = targetExercise,
            isNew = isCreatingNewState.value,
            onDismiss = { editingExerciseState.value = null },
            onSave = { savedItem ->
                val updated = workoutDays.mapIndexed { idx, day ->
                    if (idx == selectedDayIndex) {
                        val newList = if (isCreatingNewState.value) {
                            day.exercises + savedItem
                        } else {
                            day.exercises.map { ex -> if (ex.id == savedItem.id) savedItem else ex }
                        }
                        day.copy(exercises = newList)
                    } else {
                        day
                    }
                }
                saveWorkouts(updated)
                editingExerciseState.value = null
            },
            onDelete = if (!isCreatingNewState.value) {
                {
                    val updated = workoutDays.mapIndexed { idx, day ->
                        if (idx == selectedDayIndex) {
                            day.copy(exercises = day.exercises.filterNot { ex -> ex.id == targetExercise.id })
                        } else {
                            day
                        }
                    }
                    saveWorkouts(updated)
                    editingExerciseState.value = null
                }
            } else {
                null
            }
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
                        "${exercise.weight.toInt()} кг"
                    } else {
                        "${exercise.weight} кг"
                    }
                    Text(
                        text = "${exercise.sets}x${exercise.reps}  •  $weightLabel  •  ОТДЫХ ${exercise.restSeconds}С",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Настроить",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until exercise.sets) {
                    val done = i < exercise.completedSets
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (done) NothingRed else BgPureWhite)
                            .border(
                                width = 1.dp,
                                color = if (done) NothingRed else BorderSubtle,
                                shape = CircleShape
                            )
                            .clickable { onToggleSet(i) }
                    ) {
                        if (done) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "${i + 1}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseEditorSheet(
    exercise: ExerciseItem,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExerciseItem) -> Unit,
    onDelete: (() -> Unit)?
) {
    val nameState = remember { mutableStateOf(exercise.name) }
    val setsState = remember { mutableStateOf(exercise.sets) }
    val repsState = remember { mutableStateOf(exercise.reps) }
    val weightTextState = remember {
        mutableStateOf(
            if (exercise.weight % 1f == 0f) exercise.weight.toInt().toString()
            else exercise.weight.toString()
        )
    }
    val restSecondsState = remember { mutableStateOf(exercise.restSeconds) }

    val name = nameState.value
    val sets = setsState.value
    val reps = repsState.value
    val weightText = weightTextState.value
    val restSeconds = restSecondsState.value

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgPureWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNew) "НОВОЕ УПРАЖНЕНИЕ" else "НАСТРОЙКА УПРАЖНЕНИЯ",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = NothingRed
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { newVal -> nameState.value = newVal },
                label = { Text("Название упражнения") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CounterBox(
                    title = "ПОДХОДЫ",
                    value = sets,
                    onMinus = { if (sets > 1) setsState.value = sets - 1 },
                    onPlus = { if (sets < 12) setsState.value = sets + 1 },
                    modifier = Modifier.weight(1f)
                )
                CounterBox(
                    title = "ПОВТОРЫ",
                    value = reps,
                    onMinus = { if (reps > 1) repsState.value = reps - 1 },
                    onPlus = { if (reps < 100) repsState.value = reps + 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ВЕС (КГ)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallStepButton(label = "-2.5") {
                            val current = weightText.replace(',', '.').toFloatOrNull() ?: 0f
                            val next = (current - 2.5f).coerceAtLeast(0f)
                            weightTextState.value = if (next % 1f == 0f) next.toInt().toString() else next.toString()
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { newVal -> weightTextState.value = newVal },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(84.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SmallStepButton(label = "+2.5") {
                            val current = weightText.replace(',', '.').toFloatOrNull() ?: 0f
                            val next = current + 2.5f
                            weightTextState.value = if (next % 1f == 0f) next.toInt().toString() else next.
                            
