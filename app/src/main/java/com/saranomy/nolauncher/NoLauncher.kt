package com.saranomy.nolauncher

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoLauncher : ComponentActivity() {
    companion object {
        var self: NoLauncher? = null
        var apps by mutableStateOf(listOf<AppItem>())
        var icons = HashMap<String, ImageBitmap>()
    }

    private var shown by mutableStateOf(listOf<AppItem>())
    private var loading by mutableStateOf(false)
    private var scrolling by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var hidden by mutableStateOf(setOf<String>())
    private var spacing by mutableStateOf(10)
    private var showIcons by mutableStateOf(true)
    private var settingsOpen by mutableStateOf(false)
    private lateinit var appChangeReceiver: AppChangeReceiver

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nolauncher", MODE_PRIVATE)
        hidden = prefs.getStringSet("hidden", emptySet())!!.toSet()
        spacing = prefs.getInt("spacing", 10)
        showIcons = prefs.getBoolean("icons", true)
        setContent {
            val coroutineScope = rememberCoroutineScope()
            val lazyListState = rememberLazyListState()
            if (scrolling) {
                LaunchedEffect(coroutineScope) {
                    lazyListState.scrollToItem(0)
                }
                scrolling = false
            }
            NoLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(top = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = shown,
                            key = {
                                it.key
                            }
                        ) { app ->
                            app.Display(
                                checked = if (editing) app.key !in hidden else null,
                                spacing = spacing,
                                showIcon = showIcons,
                                onClick = {
                                    if (editing) {
                                        setHidden(app.key, app.key !in hidden)
                                    } else {
                                        launchApp(app)
                                    }
                                },
                                onLongClick = {
                                    try {
                                        startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (ignored: Exception) {
                                    }
                                }
                            )
                        }
                        if (apps.isNotEmpty() && !editing) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Show/Hide apps",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                editing = true
                                                updateList()
                                            }
                                            .padding(16.dp)
                                    )
                                    Text(
                                        text = "Settings",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                settingsOpen = true
                                            }
                                            .padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (loading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    if (settingsOpen) {
                        AlertDialog(
                            onDismissRequest = { settingsOpen = false },
                            confirmButton = {
                                TextButton(onClick = { settingsOpen = false }) {
                                    Text("OK")
                                }
                            },
                            title = { Text("Settings") },
                            text = {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Spacing", modifier = Modifier.weight(1f))
                                        TextButton(onClick = { changeSpacing(spacing - 2) }) {
                                            Text("−")
                                        }
                                        Text("$spacing")
                                        TextButton(onClick = { changeSpacing(spacing + 2) }) {
                                            Text("+")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Show icons", modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = showIcons,
                                            onCheckedChange = { changeShowIcons(it) }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        appChangeReceiver = AppChangeReceiver()
        registerReceiver(appChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }

    override fun onResume() {
        super.onResume()
        self = this
        load()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(appChangeReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        self = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        editing = false
        updateList()
        scrolling = true
    }

    private fun launchApp(app: AppItem) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (ignored: Exception) {
        }
    }

    private fun setHidden(key: String, hide: Boolean) {
        hidden = if (hide) hidden + key else hidden - key
        getSharedPreferences("nolauncher", MODE_PRIVATE)
            .edit().putStringSet("hidden", HashSet(hidden)).apply()
        updateList()
    }

    private fun changeSpacing(value: Int) {
        spacing = value.coerceIn(0, 24)
        getSharedPreferences("nolauncher", MODE_PRIVATE)
            .edit().putInt("spacing", spacing).apply()
    }

    private fun changeShowIcons(value: Boolean) {
        showIcons = value
        getSharedPreferences("nolauncher", MODE_PRIVATE)
            .edit().putBoolean("icons", value).apply()
    }

    private fun updateList() {
        shown = if (editing) apps else apps.filter { it.key !in hidden }
    }

    fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            loading = true
            try {
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    packageManager.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
                else packageManager.queryIntentActivities(launcherIntent, 0)

                val own = ComponentName(this@NoLauncher, NoLauncher::class.java)
                fun ResolveInfo.cn() = ComponentName(activityInfo.packageName, activityInfo.name)

                val appHashCount = apps.sumOf { it.key.hashCode() }
                val resultHashCount = result.sumOf { it.cn().flattenToString().hashCode() } +
                        own.flattenToString().hashCode()

                if (appHashCount != resultHashCount || apps.isEmpty() || shown.isEmpty()) {
                    val temp = arrayListOf<AppItem>()
                    result.forEach { ri ->
                        val cn = ri.cn()
                        val item = AppItem(ri.loadLabel(packageManager).toString(), cn.packageName, cn)
                        temp.add(item)
                        if (!icons.containsKey(item.key)) {
                            icon(item.key, ri.loadIcon(packageManager))
                        }
                    }
                    if (temp.none { it.component == own }) {
                        val item = AppItem(applicationInfo.loadLabel(packageManager).toString(), packageName, own)
                        temp.add(item)
                        if (!icons.containsKey(item.key)) {
                            icon(item.key, applicationInfo.loadIcon(packageManager))
                        }
                    }
                    temp.sort()
                    apps = temp.toList()
                    updateList()
                }
            } catch (ignored: Exception) {
            }
            loading = false
        }
    }

    private fun icon(key: String, drawable: Drawable) {
        val bitmap = Bitmap.createBitmap(
            if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1,
            if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.apply {
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
        icons[key] = bitmap.asImageBitmap()
    }
}
