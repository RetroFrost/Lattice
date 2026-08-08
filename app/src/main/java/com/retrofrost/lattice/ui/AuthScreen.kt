package com.retrofrost.lattice.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.retrofrost.lattice.telegram.TelegramAuthStage
import com.retrofrost.lattice.telegram.TelegramRepository
import com.retrofrost.lattice.telegram.TelegramUiState

@Composable
fun AuthScreen(state: TelegramUiState, repository: TelegramRepository) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Lattice") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text("Maximum Privacy", style = MaterialTheme.typography.titleMedium)
                        Text(state.connectionLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            when (val stage = state.authStage) {
                TelegramAuthStage.Initializing -> Text("Starting Telegram…")
                TelegramAuthStage.NeedApiCredentials -> ApiCredentialsForm(repository)
                TelegramAuthStage.WaitPhoneNumber -> PhoneForm(repository)
                is TelegramAuthStage.WaitCode -> CodeForm(stage.codeLength, repository)
                TelegramAuthStage.WaitPassword -> PasswordForm(repository)
                TelegramAuthStage.WaitEmailAddress -> EmailForm(repository)
                TelegramAuthStage.WaitEmailCode -> EmailCodeForm(repository)
                TelegramAuthStage.WaitRegistration -> RegistrationForm(repository)
                is TelegramAuthStage.WaitOtherDeviceConfirmation -> QrConfirmation(stage.link)
                TelegramAuthStage.Ready -> Text("Telegram connected.")
                TelegramAuthStage.Closing -> Text("Closing Telegram session…")
                TelegramAuthStage.Closed -> Text("Telegram session closed.")
                is TelegramAuthStage.Error -> Text(stage.message)
            }
        }
    }
}

@Composable
private fun ApiCredentialsForm(repository: TelegramRepository) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    Text("Telegram API setup", style = MaterialTheme.typography.headlineSmall)
    Text("This build has no Telegram API credentials embedded. Enter an API ID and API hash from my.telegram.org. Lattice never treats them as your account password.")
    OutlinedTextField(
        value = apiId,
        onValueChange = { apiId = it.filter(Char::isDigit) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API ID") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
    OutlinedTextField(
        value = apiHash,
        onValueChange = { apiHash = it.trim() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API hash") },
        singleLine = true
    )
    Button(
        onClick = { repository.setApiCredentials(apiId.toIntOrNull() ?: 0, apiHash) },
        enabled = apiId.toIntOrNull() != null && apiHash.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Continue") }
}

@Composable
private fun PhoneForm(repository: TelegramRepository) {
    var phone by remember { mutableStateOf("") }
    Text("Log in to Telegram", style = MaterialTheme.typography.headlineSmall)
    Text("Use your phone number or approve Lattice from another logged-in Telegram device.")
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Phone number") },
        placeholder = { Text("+1 555 123 4567") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true
    )
    Button(
        onClick = { repository.submitPhoneNumber(phone) },
        enabled = phone.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Continue with phone number") }
    OutlinedButton(onClick = repository::requestQrLogin, modifier = Modifier.fillMaxWidth()) {
        Text("Log in with QR code")
    }
}

@Composable
private fun CodeForm(length: Int?, repository: TelegramRepository) {
    var code by remember { mutableStateOf("") }
    Text("Authentication code", style = MaterialTheme.typography.headlineSmall)
    Text(if (length != null) "Enter the $length-digit code Telegram sent." else "Enter the code Telegram sent.")
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Code") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true
    )
    Button(onClick = { repository.submitCode(code) }, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Verify")
    }
}

@Composable
private fun PasswordForm(repository: TelegramRepository) {
    var password by remember { mutableStateOf("") }
    Text("Two-step verification", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Telegram password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true
    )
    Button(onClick = { repository.submitPassword(password) }, enabled = password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Continue")
    }
}

@Composable
private fun EmailForm(repository: TelegramRepository) {
    var email by remember { mutableStateOf("") }
    Text("Login email", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Email address") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true
    )
    Button(onClick = { repository.submitEmailAddress(email) }, enabled = email.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Send email code")
    }
}

@Composable
private fun EmailCodeForm(repository: TelegramRepository) {
    var code by remember { mutableStateOf("") }
    Text("Email code", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Code") },
        singleLine = true
    )
    Button(onClick = { repository.submitEmailCode(code) }, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Verify")
    }
}

@Composable
private fun RegistrationForm(repository: TelegramRepository) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    Text("Create Telegram account", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(value = first, onValueChange = { first = it }, modifier = Modifier.fillMaxWidth(), label = { Text("First name") })
    OutlinedTextField(value = last, onValueChange = { last = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Last name") })
    Button(onClick = { repository.register(first, last) }, enabled = first.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Create account")
    }
}

@Composable
private fun QrConfirmation(link: String) {
    Text("Scan to log in", style = MaterialTheme.typography.headlineSmall)
    Text("Open Telegram on a device where you're already logged in and scan this code. TDLib refreshes the QR link automatically.")
    if (link.isNotBlank()) {
        val bitmap = remember(link) { qrBitmap(link, 720) }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Telegram login QR code",
            modifier = Modifier.size(280.dp).align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(4.dp))
        Text(link, style = MaterialTheme.typography.bodySmall)
    }
}

private fun qrBitmap(value: String, size: Int): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
