package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.SigningKeyInfo
import com.example.ui.components.GlassCard
import com.example.ui.components.SigningVaultCanvas
import com.example.ui.components.SoftChip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioDialog
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState

@Composable
fun SigningWizardScreen(
    state: StudioUiState,
    onCreateKey: (
        alias: String,
        cn: String,
        ou: String,
        org: String,
        locality: String,
        stateName: String,
        country: String,
        algorithm: String,
        keySize: Int,
        validityYears: Int,
        password: String,
        onDone: (Boolean) -> Unit
    ) -> Unit,
    onVerifyKeyPassword: (SigningKeyInfo, String, (Boolean, String) -> Unit) -> Unit,
    onExportEncryptedKey: (SigningKeyInfo, String, (String) -> Unit) -> Unit,
    onDeleteKey: (SigningKeyInfo) -> Unit,
    onNavigateApkInspector: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    BackHandler { onBack() }

    val stepTitles = listOf("Identity", "Key Details", "Secure Storage", "Review", "Create")
    var step by remember { mutableIntStateOf(0) }
    var keyCreatedSuccess by remember { mutableStateOf(false) }

    var cn by remember { mutableStateOf("TK OFFICIAL") }
    var ou by remember { mutableStateOf("Mobile Engineering") }
    var org by remember { mutableStateOf("TK Studio") }
    var locality by remember { mutableStateOf("Studio") }
    var stateName by remember { mutableStateOf("Global") }
    var country by remember { mutableStateOf("US") }

    var alias by remember { mutableStateOf("tk_release") }
    var algorithm by remember { mutableStateOf("RSA") }
    var keySize by remember { mutableIntStateOf(2048) }
    var validityYears by remember { mutableIntStateOf(25) }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var activeVerifyKey by remember { mutableStateOf<SigningKeyInfo?>(null) }
    var activeExportMode by remember { mutableStateOf(false) }
    var dialogPassword by remember { mutableStateOf("") }
    var dialogPasswordVisible by remember { mutableStateOf(false) }
    var keyActionFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("signing_wizard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
                Column {
                    Text("Signing Wizard", style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text("Step ${step + 1} of 5 • ${stepTitles[step]}", style = MaterialTheme.typography.bodySmall, color = p.cyan)
                }
            }
        }

        // Stepper Progress Card
        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SigningVaultCanvas(isLocked = keyCreatedSuccess || step == 4, size = 60.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Key Vault Pipeline", style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(
                            "Identity → Key Details → Secure Storage → Review → Create",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (step) {
                    0 -> {
                        OutlinedTextField(value = cn, onValueChange = { cn = it }, label = { Text("Common Name (CN)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = ou, onValueChange = { ou = it }, label = { Text("Organizational Unit (OU)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = org, onValueChange = { org = it }, label = { Text("Organization (O)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = locality, onValueChange = { locality = it }, label = { Text("City (L)") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = country, onValueChange = { country = it.take(2) }, label = { Text("Country (C)") }, modifier = Modifier.weight(1f))
                        }
                    }
                    1 -> {
                        OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Key Alias") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Algorithm", style = MaterialTheme.typography.labelLarge, color = p.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftChip(label = "RSA", selected = algorithm == "RSA", onClick = { algorithm = "RSA" })
                            SoftChip(label = "EC (P-256)", selected = algorithm == "EC", onClick = { algorithm = "EC" })
                        }
                        if (algorithm == "RSA") {
                            Spacer(Modifier.height(8.dp))
                            Text("Key Size", style = MaterialTheme.typography.labelLarge, color = p.text)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SoftChip(label = "2048 bit", selected = keySize == 2048, onClick = { keySize = 2048 })
                                SoftChip(label = "4096 bit", selected = keySize == 4096, onClick = { keySize = 4096 })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Validity: $validityYears years", style = MaterialTheme.typography.labelLarge, color = p.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 25, 50).forEach { yr ->
                                SoftChip(label = "${yr}y", selected = validityYears == yr, onClick = { validityYears = yr })
                            }
                        }
                    }
                    2 -> {
                        StatusPill("Key Continuity Warning", p.warning)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Losing your signing key or password prevents shipping updates to the same Android package ID. Keys are sealed with PBKDF2-HMAC-SHA256 + AES-256-GCM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.warning
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Keystore Password (min 6 chars)") },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signing_password_input")
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signing_confirm_password_input")
                        )
                        if (password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword) {
                            Spacer(Modifier.height(4.dp))
                            Text("Passwords do not match.", style = MaterialTheme.typography.labelSmall, color = p.error)
                        }
                    }
                    3 -> {
                        Text("Review Certificate Spec", style = MaterialTheme.typography.titleMedium, color = p.cyan)
                        Spacer(Modifier.height(6.dp))
                        Text("Alias: $alias", style = MaterialTheme.typography.bodySmall, color = p.text)
                        Text("Subject: CN=$cn, OU=$ou, O=$org, L=$locality, C=$country", style = MaterialTheme.typography.bodySmall, color = p.text)
                        Text("Algorithm: $algorithm (${if (algorithm == "EC") 256 else keySize}-bit) • $validityYears years", style = MaterialTheme.typography.bodySmall, color = p.text)
                        Text("Encryption: PBKDF2-HMAC-SHA256 + AES-256-GCM", style = MaterialTheme.typography.bodySmall, color = p.success)
                    }
                    4 -> {
                        Text(
                            if (keyCreatedSuccess) "Signing Key Stored in Vault!" else "Ready to Generate Keypair",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (keyCreatedSuccess) p.success else p.text
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Generates an encrypted private key and computes its SHA-256 public key fingerprint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textSecondary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 0) {
                        StudioSecondaryButton(text = "Back", onClick = { step--; keyCreatedSuccess = false })
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (step < 4) {
                        val canNext = step != 2 || (password.length >= 6 && password == confirmPassword)
                        StudioPrimaryButton(
                            text = "Next",
                            enabled = canNext,
                            onClick = { step++ }
                        )
                    } else {
                        StudioPrimaryButton(
                            text = if (keyCreatedSuccess) "Create Another" else "Generate Key",
                            icon = Icons.Rounded.Key,
                            onClick = {
                                if (keyCreatedSuccess) {
                                    keyCreatedSuccess = false
                                    step = 0
                                    password = ""
                                    confirmPassword = ""
                                } else {
                                    onCreateKey(alias, cn, ou, org, locality, stateName, country, algorithm, keySize, validityYears, password) { ok ->
                                        keyCreatedSuccess = ok
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Verify Signed APK Handoff
        item {
            GlassCard(onClick = onNavigateApkInspector, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = p.success)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Verify Signed APK", style = MaterialTheme.typography.titleSmall, color = p.text)
                        Text("Inspect APK Signature Scheme v1/v2/v3 & X.509 certificate SHA-256", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    }
                }
            }
        }

        // Stored Keys
        item {
            Text("Stored Keys (${state.signingKeys.size})", style = MaterialTheme.typography.titleLarge, color = p.text)
        }
        items(state.signingKeys, key = { it.keystoreFileName }) { key ->
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(key.alias, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text("${key.algorithm}-${key.keySize} • ${key.commonName} (${key.organization})", style = MaterialTheme.typography.bodySmall, color = p.cyan)
                        Text("SHA-256: ${key.sha256Fingerprint.take(36)}...", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFontFamily, color = p.textSecondary)
                    }
                    IconButton(onClick = { onDeleteKey(key) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete key ${key.alias}", tint = p.error)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioSecondaryButton(
                        text = "Verify Password",
                        icon = Icons.Rounded.LockOpen,
                        onClick = {
                            activeVerifyKey = key
                            activeExportMode = false
                            dialogPassword = ""
                            keyActionFeedback = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    StudioSecondaryButton(
                        text = "Encrypted Export",
                        icon = Icons.Rounded.FileDownload,
                        onClick = {
                            activeVerifyKey = key
                            activeExportMode = true
                            dialogPassword = ""
                            keyActionFeedback = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    activeVerifyKey?.let { key ->
        StudioDialog(
            title = if (activeExportMode) "Export Encrypted Key Vault" else "Verify Keystore Password",
            onDismiss = { activeVerifyKey = null },
            confirmText = if (activeExportMode) "Verify & Export" else "Verify Password",
            onConfirm = {
                if (activeExportMode) {
                    onExportEncryptedKey(key, dialogPassword) { msg ->
                        keyActionFeedback = msg
                    }
                } else {
                    onVerifyKeyPassword(key, dialogPassword) { _, msg ->
                        keyActionFeedback = msg
                    }
                }
            }
        ) {
            if (activeExportMode) {
                Text(
                    "Security Notice: Exports an AES-256-GCM encrypted backup file (.tkkey.json). Keep both the exported file and keystore password confidential.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.warning
                )
                Spacer(Modifier.height(8.dp))
            }
            Text("Key Alias: ${key.alias}", style = MaterialTheme.typography.labelMedium, color = p.cyan)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = dialogPassword,
                onValueChange = { dialogPassword = it },
                label = { Text("Enter Keystore Password") },
                visualTransformation = if (dialogPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { dialogPasswordVisible = !dialogPasswordVisible }) {
                        Icon(
                            imageVector = if (dialogPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (dialogPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            keyActionFeedback?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = p.cyan)
            }
        }
    }
}
