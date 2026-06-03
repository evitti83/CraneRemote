package com.craneremote.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.craneremote.domain.model.CommandLog
import com.craneremote.domain.usecase.ClearLogsUseCase
import com.craneremote.domain.usecase.GetLogsUseCase
import com.craneremote.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val getLogs: GetLogsUseCase,
    private val clearLogs: ClearLogsUseCase
) : ViewModel() {

    private val _logs = MutableStateFlow<List<CommandLog>>(emptyList())
    val logs: StateFlow<List<CommandLog>> = _logs.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(deviceId: String) {
        viewModelScope.launch {
            getLogs(deviceId).collect { list ->
                _logs.value = list
                _loading.value = false
            }
        }
    }

    fun clear(deviceId: String) = viewModelScope.launch { clearLogs(deviceId) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    navController: NavController,
    deviceId: String,
    viewModel: LogsViewModel = hiltViewModel()
) {
    val logs    by viewModel.logs.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var showClear by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) { viewModel.load(deviceId) }

    if (showClear) AlertDialog(
        onDismissRequest = { showClear = false },
        title   = { Text("Limpar logs") },
        text    = { Text("Remover todos os logs deste dispositivo?") },
        confirmButton = { TextButton(onClick = { showClear = false; viewModel.clear(deviceId) }) { Text("Limpar", color = Disconnected) } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancelar") } }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs de Comandos") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showClear = true }) {
                        Icon(Icons.Default.DeleteSweep, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary, titleContentColor = Color.White)
            )
        }
    ) { pad ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            logs.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Receipt, null, Modifier.size(48.dp), tint = Secondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhum log", color = Secondary)
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs, key = { it.id }) { LogRow(it) }
            }
        }
    }
}

@Composable
private fun LogRow(log: CommandLog) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status dot
        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (log.success) Connected else Disconnected))

        // Timestamp
        Text(
            fmt.format(Date(log.timestamp)),
            style      = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color      = Secondary,
            modifier   = Modifier.width(80.dp)
        )

        // Subsistema + direção
        Text(
            "${log.subsystemName} ${log.direction}",
            style    = MaterialTheme.typography.bodySmall,
            color    = Color.White,
            modifier = Modifier.width(120.dp)
        )

        // Tag = valor
        Text(
            "${log.tagAddress} = ${log.value}",
            style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color    = if (log.success) Connected else Disconnected,
            modifier = Modifier.weight(1f)
        )

        // Resposta de erro
        if (!log.success && log.response.isNotBlank()) {
            Text(
                log.response,
                style  = MaterialTheme.typography.labelSmall,
                color  = Disconnected.copy(0.7f),
                modifier = Modifier.width(100.dp)
            )
        }
    }
}
