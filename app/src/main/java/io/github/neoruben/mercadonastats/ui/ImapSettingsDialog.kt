package io.github.neoruben.mercadonastats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.neoruben.mercadonastats.mail.ImapProvider
import io.github.neoruben.mercadonastats.mail.ImapSettings

/** Lo que el usuario ha escrito en el formulario. Contraseña vacía = mantener la guardada. */
data class ImapSettingsInput(
    val host: String,
    val email: String,
    val password: String,
    val folder: String
)

/**
 * Formulario para conectar un correo por IMAP con una contraseña de aplicación.
 *
 * @param current cuenta ya guardada (para editarla), o `null` si es nueva.
 * @param busy `true` mientras se prueba la conexión.
 * @param error mensaje de error de la última prueba.
 */
@Composable
fun ImapSettingsDialog(
    current: ImapSettings?,
    busy: Boolean,
    error: String?,
    onSave: (ImapSettingsInput) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember { mutableStateOf(current?.host ?: "") }
    var email by remember { mutableStateOf(current?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(current?.folder ?: ImapSettings.DEFAULT_FOLDER) }
    var showPassword by remember { mutableStateOf(false) }

    val passwordOk = password.isNotBlank() || current != null
    val canSave = !busy && host.isNotBlank() && email.contains('@') && passwordOk && folder.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Correo por IMAP", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Lee los tickets de Mercadona de cualquier correo. Necesitas una " +
                        "contraseña de aplicación: se crea en la seguridad de tu cuenta de correo " +
                        "y no es tu contraseña normal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { value ->
                        email = value.trim()
                        if (host.isBlank() || ImapProvider.entries.any { it.host == host }) {
                            ImapProvider.guessFromEmail(email)?.let { host = it.host }
                        }
                    },
                    label = { Text("Correo") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña de aplicación") },
                    placeholder = if (current != null) {
                        { Text("Sin cambios") }
                    } else {
                        null
                    },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Servidor", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImapProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = host == provider.host,
                            onClick = { host = provider.host },
                            enabled = !busy,
                            label = { Text(provider.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("Servidor IMAP") },
                    placeholder = { Text("imap.ejemplo.com") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Carpeta o etiqueta") },
                    supportingText = {
                        Text(
                            "INBOX es la bandeja de entrada. Para una etiqueta de Gmail, escribe su " +
                                "nombre (por ejemplo, Tickets/Mercadona); debe tener activado «Mostrar en IMAP»."
                        )
                    },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!error.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFEBEE)) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB71C1C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                if (current != null) {
                    TextButton(onClick = onForget, enabled = !busy) {
                        Text("Olvidar esta cuenta", color = Color(0xFFB71C1C))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ImapSettingsInput(
                            host = host.trim(),
                            email = email.trim(),
                            password = password,
                            folder = folder.trim()
                        )
                    )
                },
                enabled = canSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MercadonaGreen)
            ) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Comprobando…")
                    }
                } else {
                    Text("Guardar y sincronizar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}
