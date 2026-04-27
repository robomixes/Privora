package com.privateai.camera.ui.passwords

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.HintCategory
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.PasswordHint
import com.privateai.camera.security.PasswordHintRepository
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHintsScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val crypto = remember { CryptoManager(context) }
    val repo = remember { PasswordHintRepository(File(context.filesDir, "vault"), crypto) }

    val startUnlocked = remember { VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize() }
    var isLocked by remember { mutableStateOf(!startUnlocked) }
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }

    // Auto-lock
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> VaultLockManager.markLeft()
                Lifecycle.Event.ON_START -> {
                    if (!isLocked && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        isLocked = true; crypto.lock(); VaultLockManager.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val currentAuthMode = remember { getAuthMode(context) }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) { if (crypto.initialize()) { VaultLockManager.markUnlocked(); isLocked = false }; return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { VaultLockManager.markUnlocked(); isLocked = false }
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.vault_unlock_title))
                .setSubtitle(context.getString(R.string.vault_unlock_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        )
    }

    var isLockedOut by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context)) }

    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(pin: String) {
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, pin)) {
            isDuressActive = true; VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked(); isLocked = false
            Thread { DuressManager.executeDuress(context, crypto) }.start()
            return
        }
        if (!PinRateLimiter.canAttempt(context)) {
            pinInput = ""; isLockedOut = true; lockoutRemainingMs = PinRateLimiter.remainingLockoutMs(context); return
        }
        if (AppPinManager.verify(context, pin)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) { isDuressActive = false; VaultLockManager.clearDuress(); VaultLockManager.markUnlocked(); isLocked = false; pinInput = ""; pinError = null }
            return
        }
        PinRateLimiter.recordFailure(context)
        val remaining = PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) { isLockedOut = true; lockoutRemainingMs = remaining; pinError = null }
        else { pinError = context.getString(R.string.vault_incorrect_pin) }
        pinInput = ""
    }

    LaunchedEffect(Unit) { if (isLocked && currentAuthMode == AuthMode.PHONE_LOCK) authenticate() }

    if (isLocked) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.feature_passwords)) }, navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.password_locked), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                Spacer(Modifier.height(24.dp))
                if (currentAuthMode == AuthMode.APP_PIN) {
                    if (isLockedOut) {
                        val seconds = (lockoutRemainingMs / 1000).toInt()
                        Text(stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)), color = MaterialTheme.colorScheme.error)
                    } else {
                        OutlinedTextField(
                            value = pinInput, onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = null } },
                            label = { Text(stringResource(R.string.vault_enter_pin)) }, modifier = Modifier.width(200.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                            visualTransformation = PasswordVisualTransformation(), isError = pinError != null,
                            supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        Button(onClick = { if (pinInput.length >= 4) checkPin(pinInput) }, enabled = pinInput.length >= 4, modifier = Modifier.width(200.dp)) { Text(stringResource(R.string.action_unlock)) }
                    }
                } else {
                    Button(onClick = { authenticate() }, modifier = Modifier.width(200.dp)) { Text(stringResource(R.string.action_unlock)) }
                }
            }
        }
        return
    }

    // Unlocked — show hints
    var hints by remember { mutableStateOf(if (isDuressActive) emptyList() else repo.listAll()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<HintCategory?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PasswordHint?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<PasswordHint?>(null) }

    fun refresh() { hints = if (isDuressActive) emptyList() else repo.listAll() }

    val filtered = remember(hints, searchQuery, selectedCategory) {
        var list = hints
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.serviceName.lowercase().contains(q) || it.usernameHint.lowercase().contains(q) ||
                it.passwordHint.lowercase().contains(q) || it.notes.lowercase().contains(q)
            }
        }
        if (selectedCategory != null) list = list.filter { it.category == selectedCategory }
        list
    }
    val favorites = filtered.filter { it.isFavorite }
    val rest = filtered.filter { !it.isFavorite }

    if (showAddDialog) {
        PasswordHintDialog(
            initial = editing,
            onDismiss = { showAddDialog = false; editing = null },
            onSave = { hint -> repo.save(hint); refresh(); showAddDialog = false; editing = null }
        )
    }

    showDeleteConfirm?.let { hint ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.password_delete_title)) },
            text = { Text(stringResource(R.string.password_delete_message, hint.serviceName)) },
            confirmButton = { TextButton(onClick = { repo.delete(hint.id); refresh(); showDeleteConfirm = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.feature_passwords))
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                }
            )
        },
        floatingActionButton = {
            if (!isDuressActive) {
                FloatingActionButton(onClick = { editing = null; showAddDialog = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.password_add_title))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.password_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )

            // Category filter chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelSmall) }
                )
                HintCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text("${cat.icon} ${cat.label}", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (isDuressActive || filtered.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Key, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.password_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Favorites section
                    if (favorites.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.password_favorites), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        items(favorites, key = { it.id }) { hint ->
                            HintCard(hint, onClick = { editing = hint; showAddDialog = true }, onDelete = { showDeleteConfirm = hint })
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // Rest
                    if (rest.isNotEmpty()) {
                        items(rest, key = { it.id }) { hint ->
                            HintCard(hint, onClick = { editing = hint; showAddDialog = true }, onDelete = { showDeleteConfirm = hint })
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HintCard(hint: PasswordHint, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(hint.category.icon, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(hint.serviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (hint.isFavorite) Text("⭐", style = MaterialTheme.typography.labelSmall)
                }
                if (hint.usernameHint.isNotBlank()) {
                    Text(hint.usernameHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hint.passwordHint.isNotBlank()) {
                    Text(hint.passwordHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (hint.pinHint.isNotBlank()) {
                    Text("PIN: ${hint.pinHint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

