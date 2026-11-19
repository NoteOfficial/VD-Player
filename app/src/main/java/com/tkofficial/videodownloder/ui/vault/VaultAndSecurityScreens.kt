package com.tkofficial.videodownloder.ui.vault

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.tkofficial.videodownloder.core.findActivity
import com.tkofficial.videodownloder.database.VaultMetadataEntity
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaPrimaryButton
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.profile.ProfileViewModel
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onPlayDecryptedVideo: (uri: String, title: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pinState by viewModel.pinState.collectAsStateWithLifecycle()
    val unlocked by viewModel.vaultUnlocked.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    val decryptedPreview by viewModel.decryptedPreviewBytes.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    val hasPinConfigured = pinState.first != null
    var pinInput by remember { mutableStateOf("") }
    var deleteConfirmItem by remember { mutableStateOf<VaultMetadataEntity?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val vaultPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importToVault(uri)
    }

    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (unlocked) {
                        IconButton(onClick = viewModel::verifyVaultIntegrity) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = stringResource(R.string.vault_verify_integrity), tint = tokens.iconTint)
                        }
                        IconButton(onClick = viewModel::lockVault) {
                            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.vault_lock), tint = tokens.danger)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        if (!unlocked) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                VaCard(highlighted = true, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = tokens.primaryAccent, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (hasPinConfigured) stringResource(R.string.vault_enter_pin) else stringResource(R.string.vault_create_pin),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.vault_locked_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinInput = it },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            label = { Text(stringResource(R.string.pin_field_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        VaPrimaryButton(
                            text = if (hasPinConfigured) stringResource(R.string.vault_unlock_btn) else stringResource(R.string.vault_create_open_btn),
                            icon = Icons.Rounded.LockOpen,
                            onClick = {
                                val candidate = pinInput
                                pinInput = ""
                                if (hasPinConfigured) {
                                    scope.launch { viewModel.attemptPinUnlockAsync(candidate) }
                                } else {
                                    viewModel.setOrChangePin(candidate)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val activity = context.findActivity()
                        if (hasPinConfigured && settings.biometricEnabled && activity != null) {
                            val bioTitle = stringResource(R.string.biometric_vault_title)
                            val bioSubtitle = stringResource(R.string.biometric_vault_subtitle)
                            val bioNeg = stringResource(R.string.biometric_use_pin)
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    launchBiometricPrompt(
                                        activity = activity,
                                        title = bioTitle,
                                        subtitle = bioSubtitle,
                                        negativeText = bioNeg
                                    ) { viewModel.unlockWithBiometricSuccess() }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.vault_unlock_biometric))
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    VaPrimaryButton(
                        text = stringResource(R.string.vault_import_media),
                        icon = Icons.Rounded.Add,
                        onClick = {
                            vaultPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (vaultItems.isEmpty()) {
                    item {
                        VaCard {
                            Text(
                                stringResource(R.string.vault_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(vaultItems, key = { it.id }) { item ->
                        VaCard(
                            onClick = { viewModel.openVaultItem(item) },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Security, contentDescription = null, tint = tokens.primaryAccent)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.originalName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${item.mediaType} • ${FormatUtils.formatBytes(item.originalSizeBytes)} • AES-256-GCM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { deleteConfirmItem = item }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete), tint = tokens.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Decrypted Vault Item Viewer (strictly in-memory for photos, ephemeral temp cache cleaned on close for videos)
    decryptedPreview?.let { (meta, bytes) ->
        if (meta.mediaType == "VIDEO") {
            LaunchedEffect(meta.id) {
                val temp = withContext(Dispatchers.IO) {
                    val f = File(context.cacheDir, "vault_play_${meta.id}.mp4")
                    f.writeBytes(bytes)
                    f
                }
                viewModel.closeVaultPreview()
                onPlayDecryptedVideo(android.net.Uri.fromFile(temp).toString(), meta.originalName)
            }
        } else {
            val bitmapState = androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, bytes) {
                value = withContext(Dispatchers.Default) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while ((bounds.outWidth / sample) > 2048 || (bounds.outHeight / sample) > 2048) {
                        sample *= 2
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
            Dialog(
                onDismissRequest = viewModel::closeVaultPreview,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    bitmapState.value?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = meta.originalName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    IconButton(
                        onClick = viewModel::closeVaultPreview,
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close), tint = Color.White)
                    }
                }
            }
        }
    }

    deleteConfirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConfirmItem = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.vault_shred_confirm, item.originalName)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteVaultItem(item)
                    deleteConfirmItem = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmItem = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterScreen(
    viewModel: ProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenVault: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pinState by viewModel.pinState.collectAsStateWithLifecycle()
    val report by viewModel.integrityReport.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showPinSetupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.runIntegrityAudit() }
    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val biometricSupported = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_security)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                VaCard(highlighted = true) {
                    Text(stringResource(R.string.sec_access_protection), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sec_app_vault_pin), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (pinState.first != null) stringResource(R.string.sec_pin_enabled_sub)
                                else stringResource(R.string.sec_pin_not_configured),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { showPinSetupDialog = true }) {
                                Text(if (pinState.first != null) stringResource(R.string.sec_change) else stringResource(R.string.sec_set_pin))
                            }
                            if (pinState.first != null) {
                                OutlinedButton(onClick = viewModel::disablePin) { Text(stringResource(R.string.btn_clear)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sec_biometric_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (biometricSupported) stringResource(R.string.sec_biometric_ready)
                                else stringResource(R.string.sec_biometric_unavailable),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = settings.biometricEnabled,
                            enabled = biometricSupported,
                            onCheckedChange = { en -> viewModel.updateSettings { it.copy(biometricEnabled = en) } }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    VaPrimaryButton(text = stringResource(R.string.menu_vault), icon = Icons.Rounded.Lock, onClick = onOpenVault)
                }
            }

            report?.let { rep ->
                item {
                    VaCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (rep.packageValid && rep.keystoreAvailable) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = if (rep.packageValid) tokens.success else tokens.danger
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sec_integrity_audit), style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(10.dp))
                        val pkgStatus = stringResource(if (rep.packageValid) R.string.sec_verified else R.string.sec_mismatch)
                        val ksStatus = stringResource(if (rep.keystoreAvailable) R.string.sec_active else R.string.sec_unavailable)
                        Text(stringResource(R.string.sec_package_name, rep.packageName, pkgStatus), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.sec_keystore_label, ksStatus), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.sec_debuggable, rep.isDebuggableBuild.toString()), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.sec_debugger, rep.isDebuggerAttached.toString()), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.sec_cert_sha256), style = MaterialTheme.typography.labelLarge)
                        Text(rep.signingCertSha256, style = MaterialTheme.typography.labelSmall)
                        if (rep.suspiciousEnvironmentFlags.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            VaStatusChip(rep.suspiciousEnvironmentFlags.joinToString(), tokens.warning)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.sec_audit_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::runIntegrityAudit) { Text(stringResource(R.string.sec_rerun_audit)) }
                    }
                }
            }
        }
    }

    if (showPinSetupDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text(stringResource(R.string.vault_create_pin)) },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setOrChangePin(newPin)
                    showPinSetupDialog = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

private fun launchBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    negativeText: String,
    onSuccess: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(negativeText)
        .build()
    prompt.authenticate(info)
}
