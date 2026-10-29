package com.coach.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

data class Exercise(val name: String, val sets: Int, val reps: Int, val weight: Double)
data class Workout(val id: String = UUID.randomUUID().toString(), val title: String, val exercises: List<Exercise>)
data class SetLog(val exercise: String, val reps: Int, val weight: Double)
data class Log(val workoutTitle: String, val date: String, val sets: List<SetLog>)
data class Client(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val workouts: List<Workout> = emptyList(), val logs: List<Log> = emptyList()
)

class VM(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("coach", 0)
    private val gson = Gson()
    var clients by mutableStateOf(
        try { gson.fromJson(prefs.getString("d", "[]"), Array<Client>::class.java).toList() } catch (e: Exception) { emptyList() }
    )
    fun update(f: (List<Client>) -> List<Client>) {
        clients = f(clients); prefs.edit().putString("d", gson.toJson(clients)).apply()
    }
    fun edit(id: String, f: (Client) -> Client) = update { l -> l.map { if (it.id == id) f(it) else it } }
}

sealed class Screen {
    object Home : Screen()
    data class Detail(val cid: String) : Screen()
    data class Builder(val cid: String) : Screen()
    data class Run(val cid: String, val wid: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: VM = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(screen != Screen.Home) {
        screen = when (val s = screen) {
            is Screen.Builder -> Screen.Detail(s.cid)
            is Screen.Run -> Screen.Detail(s.cid)
            else -> Screen.Home
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Coach") }) }) { pad ->
        Box(Modifier.padding(pad).padding(16.dp)) {
            when (val s = screen) {
                Screen.Home -> Home(vm) { screen = Screen.Detail(it) }
                is Screen.Detail -> vm.clients.find { it.id == s.cid }?.let {
                    Detail(it, vm, { screen = Screen.Builder(s.cid) }, { w -> screen = Screen.Run(s.cid, w) }, { screen = Screen.Home })
                }
                is Screen.Builder -> Builder { w ->
                    vm.edit(s.cid) { it.copy(workouts = it.workouts + w) }; screen = Screen.Detail(s.cid)
                }
                is Screen.Run -> {
                    val c = vm.clients.find { it.id == s.cid }
                    val w = c?.workouts?.find { it.id == s.wid }
                    if (c != null && w != null) Runner(w) { log ->
                        vm.edit(c.id) { it.copy(logs = listOf(log) + it.logs) }; screen = Screen.Detail(c.id)
                    }
                }
            }
        }
    }
}

@Composable
fun Home(vm: VM, open: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, label = { Text("New client name") }, modifier = Modifier.weight(1f))
            Button(onClick = { if (name.isNotBlank()) { vm.update { it + Client(name = name.trim()) }; name = "" } }) { Text("Add") }
        }
        Text("Clients", style = MaterialTheme.typography.titleMedium)
        if (vm.clients.isEmpty()) Text("No clients yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.clients) { c ->
                Card(onClick = { open(c.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text("${c.workouts.size} workouts · ${c.logs.size} sessions logged")
                    }
                }
            }
        }
    }
}

@Composable
fun Detail(c: Client, vm: VM, build: () -> Unit, run: (String) -> Unit, back: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.update { l -> l.filter { it.id != c.id } }; back() }) { Text("Delete") }
            }
            Button(onClick = build, modifier = Modifier.fillMaxWidth()) { Text("+ New workout") }
            Text("Assigned workouts", style = MaterialTheme.typography.titleMedium)
        }
        items(c.workouts) { w ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(w.title, style = MaterialTheme.typography.titleMedium)
                    w.exercises.forEach { Text("${it.name}: ${it.sets}×${it.reps} @ ${it.weight}kg") }
                    Row {
                        Button(onClick = { run(w.id) }) { Text("Start / log") }
                        TextButton(onClick = { vm.edit(c.id) { x -> x.copy(workouts = x.workouts - w) } }) { Text("Remove") }
                    }
                }
            }
        }
        item { Text("History", style = MaterialTheme.typography.titleMedium) }
        if (c.logs.isEmpty()) item { Text("No sessions logged yet.") }
        items(c.logs) { l ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${l.workoutTitle} · ${l.date}", style = MaterialTheme.typography.titleSmall)
                    Text("Volume: ${"%.0f".format(l.sets.sumOf { it.reps * it.weight })} kg")
                    l.sets.groupBy { it.exercise }.forEach { (n, s) ->
                        Text("$n: " + s.joinToString(", ") { "${it.reps}×${it.weight}" })
                    }
                }
            }
        }
    }
}

@Composable
fun Builder(save: (Workout) -> Unit) {
    var title by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var wt by remember { mutableStateOf("0") }
    val list = remember { mutableStateListOf<Exercise>() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(title, { title = it }, label = { Text("Workout title (e.g. Push Day)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(n, { n = it }, label = { Text("Exercise") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sets, { sets = it }, label = { Text("Sets") }, modifier = Modifier.weight(1f))
                OutlinedTextField(reps, { reps = it }, label = { Text("Reps") }, modifier = Modifier.weight(1f))
                OutlinedTextField(wt, { wt = it }, label = { Text("kg") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                if (n.isNotBlank()) { list.add(Exercise(n.trim(), sets.toIntOrNull() ?: 3, reps.toIntOrNull() ?: 10, wt.toDoubleOrNull() ?: 0.0)); n = "" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
        }
        items(list.toList()) { Text("• ${it.name}: ${it.sets}×${it.reps} @ ${it.weight}kg") }
        item {
            Button(onClick = { if (title.isNotBlank() && list.isNotEmpty()) save(Workout(title = title.trim(), exercises = list.toList())) },
                modifier = Modifier.fillMaxWidth()) { Text("Save & assign") }
        }
    }
}

data class Row(val ex: String, val reps: String, val kg: String, val done: Boolean = false)

@Composable
fun Runner(w: Workout, finish: (Log) -> Unit) {
    val rows = remember {
        mutableStateListOf<Row>().apply {
            w.exercises.forEach { e -> repeat(e.sets) { add(Row(e.name, e.reps.toString(), e.weight.toString())) } }
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text(w.title, style = MaterialTheme.typography.headlineSmall) }
        items(rows.size) { i ->
            val r = rows[i]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.ex, modifier = Modifier.weight(1.2f))
                OutlinedTextField(r.reps, { rows[i] = r.copy(reps = it) }, label = { Text("reps") }, modifier = Modifier.weight(0.8f), singleLine = true)
                OutlinedTextField(r.kg, { rows[i] = r.copy(kg = it) }, label = { Text("kg") }, modifier = Modifier.weight(0.8f), singleLine = true)
                Checkbox(r.done, { rows[i] = r.copy(done = it) })
            }
        }
        item {
            Button(onClick = {
                val done = rows.filter { it.done }.map { SetLog(it.ex, it.reps.toIntOrNull() ?: 0, it.kg.toDoubleOrNull() ?: 0.0) }
                finish(Log(w.title, SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()), done))
            }, modifier = Modifier.fillMaxWidth()) { Text("Finish & save") }
        }
    }
}
