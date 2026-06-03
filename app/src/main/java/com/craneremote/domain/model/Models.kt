package com.craneremote.domain.model

import java.util.UUID

// ─── PLC / Conexão ────────────────────────────────────────────────────────────

enum class PlcType {
    SIEMENS_S7_1200,
    SIEMENS_S7_1500,
    SIEMENS_S7_300,
    ALLEN_BRADLEY_COMPACTLOGIX,
    ALLEN_BRADLEY_CONTROLLOGIX,
    ALLEN_BRADLEY_MICROLOGIX
}

enum class DeviceStatus { CONNECTED, DISCONNECTED, CONNECTING, ERROR }

data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ipAddress: String,
    val port: Int = 102,
    val plcType: PlcType = PlcType.SIEMENS_S7_1200,
    val rack: Int = 0,
    val slot: Int = 1,
    val path: String = "1,0",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Subsistema (Gancho / Bridge / Trolley) ───────────────────────────────────

/**
 * Tipo de eixo de movimento do subsistema:
 *  VERTICAL   → subir / descer (ganchos MH)
 *  HORIZONTAL → esquerda / direita (bridge, trolley)
 */
enum class SubsystemAxis { VERTICAL, HORIZONTAL }

/**
 * Cada subsistema representa um mecanismo da ponte:
 *  - MH1, MH2, MH3, MH4 (ganchos) → eixo VERTICAL
 *  - Bridge, Trolley1, Trolley2    → eixo HORIZONTAL
 *
 * Tags:
 *  tagPositive → UP (vertical) ou RIGHT (horizontal)
 *  tagNegative → DOWN (vertical) ou LEFT (horizontal)
 *  tagSpeed    → endereço de leitura de velocidade em % (MW102, ex.)
 *  tagSetpoint → endereço de escrita do setpoint de velocidade (se aplicável)
 *  speedTagType→ tipo da tag de velocidade (INT = word, REAL = float)
 */
data class Subsystem(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val name: String,                    // "MH1", "Bridge", "Trolley 2", etc.
    val axis: SubsystemAxis,
    val order: Int = 0,
    // Tags de comando (escrita BOOL)
    val tagPositive: String = "",        // UP / RIGHT
    val tagNegative: String = "",        // DOWN / LEFT
    // Tag de velocidade (leitura — % da nominal)
    val tagSpeedRead: String = "",       // ex: "MW102"
    // Tag de setpoint de velocidade (escrita INT 0-100)
    val tagSpeedWrite: String = "",      // ex: "MW200"
    val defaultSpeedPct: Int = 50        // velocidade padrão (%) ao mover o joystick
)

// ─── Botões Fixos ─────────────────────────────────────────────────────────────

/**
 * Comportamento do botão:
 *  PULSE  → envia ON ao pressionar, OFF ao soltar (ex: sirene)
 *  LATCH  → toggle: primeiro toque = ON, segundo = OFF (ex: working lights)
 *  MOMENTARY → igual a PULSE mas sem auto-release (usuário mantém pressionado)
 */
enum class ButtonBehavior { PULSE, LATCH, MOMENTARY }

/**
 * Botão permanente na tela de controle.
 * A tela suporta até 8 botões fixos configuráveis.
 */
data class FixedButton(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val slot: Int,                        // posição 0-7 na barra de botões
    val label: String,                    // texto exibido
    val icon: String = "DEFAULT",         // nome do ícone Material
    val tag: String = "",                 // endereço da tag
    val behavior: ButtonBehavior = ButtonBehavior.LATCH,
    val activeColor: String = "#FF9800",  // cor quando ativo/pressionado
    val isEnabled: Boolean = true
)

// Slots padrão pré-nomeados (podem ser sobrescritos pelo usuário)
object DefaultButtons {
    fun defaults(deviceId: String) = listOf(
        FixedButton(deviceId = deviceId, slot = 0, label = "Sirene",   icon = "HORN",    behavior = ButtonBehavior.PULSE,   activeColor = "#FF9800"),
        FixedButton(deviceId = deviceId, slot = 1, label = "Energiza", icon = "POWER",   behavior = ButtonBehavior.LATCH,   activeColor = "#4CAF50"),
        FixedButton(deviceId = deviceId, slot = 2, label = "Bypass",   icon = "BYPASS",  behavior = ButtonBehavior.LATCH,   activeColor = "#FF5722"),
        FixedButton(deviceId = deviceId, slot = 3, label = "Lights",   icon = "LIGHTS",  behavior = ButtonBehavior.LATCH,   activeColor = "#FFEB3B"),
        FixedButton(deviceId = deviceId, slot = 4, label = "Tools",    icon = "TOOLS",   behavior = ButtonBehavior.LATCH,   activeColor = "#2196F3"),
        FixedButton(deviceId = deviceId, slot = 5, label = "Aux 1",    icon = "AUX",     behavior = ButtonBehavior.LATCH,   activeColor = "#9C27B0"),
        FixedButton(deviceId = deviceId, slot = 6, label = "Aux 2",    icon = "AUX",     behavior = ButtonBehavior.LATCH,   activeColor = "#9C27B0"),
        FixedButton(deviceId = deviceId, slot = 7, label = "E-Stop",   icon = "ESTOP",   behavior = ButtonBehavior.MOMENTARY, activeColor = "#F44336")
    )
}

// ─── Log de Comandos ──────────────────────────────────────────────────────────

data class CommandLog(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val deviceName: String,
    val subsystemName: String,
    val direction: String,               // "UP", "DOWN", "LEFT", "RIGHT", "BUTTON:<label>"
    val tagAddress: String,
    val value: String,
    val success: Boolean,
    val response: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Estado de Velocidade (runtime, não persiste) ─────────────────────────────

data class SubsystemStatus(
    val subsystemId: String,
    val speedPct: Float = 0f,            // velocidade lida do PLC (%)
    val isMoving: Boolean = false,
    val setpointPct: Int = 50            // velocidade configurada pelo usuário
)
