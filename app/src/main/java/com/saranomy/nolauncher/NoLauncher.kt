package com.saranomy.nolauncher

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class CustomEntry(
    val name: String,
    val pkg: String,
    val cls: String,
    val inList: Boolean,
    val inMenu: Boolean
)

open class NoLauncher : ComponentActivity() {
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
    private var names by mutableStateOf(mapOf<String, String>())
    private var customs by mutableStateOf(listOf<CustomEntry>())
    private var spacing by mutableStateOf(10)
    private var showIcons by mutableStateOf(true)
    private var tapMenu by mutableStateOf(false)
    private var rightLayout by mutableStateOf(true)
    private var settingsOpen by mutableStateOf(false)
    private var customOpen by mutableStateOf(false)
    private var customAddOpen by mutableStateOf(false)
    private var addName by mutableStateOf("")
    private var addComponent by mutableStateOf("")
    private var addInList by mutableStateOf(true)
    private var addInMenu by mutableStateOf(false)
    private var menuApp by mutableStateOf<AppItem?>(null)
    private var menuShortcuts by mutableStateOf(listOf<ShortcutInfo>())
    private var menuShortcutIcons by mutableStateOf(mapOf<String, ImageBitmap>())
    private var renameApp by mutableStateOf<AppItem?>(null)
    private var renameText by mutableStateOf("")
    private lateinit var appChangeReceiver: AppChangeReceiver

    protected open val assistMode: Boolean
        get() = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nolauncher", MODE_PRIVATE)
        hidden = prefs.getStringSet("hidden", emptySet())!!.toSet()
        spacing = prefs.getInt("spacing", 10)
        showIcons = prefs.getBoolean("icons", true)
        tapMenu = prefs.getBoolean("tapmenu", false)
        rightLayout = prefs.getBoolean("right", true)
        customs = parseCustoms(prefs.getString("custom", null))
        names = getSharedPreferences("nolauncher_names", MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
            .toMap()
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
                val barColor = MaterialTheme.colorScheme.background
                SideEffect {
                    window.navigationBarColor = barColor.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightNavigationBars = barColor.luminance() > 0.5f
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LazyColumn(
                        state = lazyListState,
                        reverseLayout = rightLayout,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val footer: LazyListScope.() -> Unit = {
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
                        if (rightLayout) {
                            footer()
                        }
                        items(
                            items = if (rightLayout) shown.reversed() else shown,
                            key = {
                                it.key
                            }
                        ) { app ->
                            val onLaunch: () -> Unit = { launchApp(app) }
                            val onZip: () -> Unit = { openZipScreen() }
                            val openMenu: () -> Unit = {
                                menuShortcuts = loadShortcuts(app)
                                menuShortcutIcons = loadShortcutIcons(menuShortcuts)
                                menuApp = app
                            }
                            app.Display(
                                checked = if (editing) app.key !in hidden else null,
                                spacing = spacing,
                                showIcon = showIcons,
                                rightAlign = rightLayout,
                                onClick = {
                                    if (editing) {
                                        setHidden(app.key, app.key !in hidden)
                                    } else if (tapMenu) {
                                        openMenu()
                                    }
                                },
                                onDoubleClick = if (editing || tapMenu) null else onLaunch,
                                onLongClick = if (tapMenu) onZip else openMenu
                            )
                        }
                        if (!rightLayout) {
                            footer()
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
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Bottom-right layout", modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = rightLayout,
                                            onCheckedChange = { changeRightLayout(it) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tap opens menu", modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = tapMenu,
                                            onCheckedChange = { changeTapMenu(it) }
                                        )
                                    }
                                    Text(
                                        text = "Off: double tap launches, long press opens the menu. On: tap opens the menu, long press opens the compress screen.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Custom activities",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                settingsOpen = false
                                                customOpen = true
                                            }
                                            .padding(vertical = 14.dp)
                                    )
                                }
                            }
                        )
                    }
                    if (customOpen) {
                        AlertDialog(
                            onDismissRequest = { customOpen = false },
                            title = { Text("Custom activities") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    if (customs.isEmpty()) {
                                        Text(
                                            text = "Nothing added yet.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    customs.forEachIndexed { index, c ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(c.name, modifier = Modifier.weight(1f))
                                            TextButton(onClick = {
                                                saveCustoms(customs.filterIndexed { i, _ -> i != index })
                                                load()
                                            }) {
                                                Text("✕")
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = c.inList,
                                                onCheckedChange = { value ->
                                                    saveCustoms(customs.mapIndexed { i, e ->
                                                        if (i == index) e.copy(inList = value) else e
                                                    })
                                                    load()
                                                }
                                            )
                                            Text("List")
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Checkbox(
                                                checked = c.inMenu,
                                                onCheckedChange = { value ->
                                                    saveCustoms(customs.mapIndexed { i, e ->
                                                        if (i == index) e.copy(inMenu = value) else e
                                                    })
                                                }
                                            )
                                            Text("Menu")
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    addName = ""
                                    addComponent = ""
                                    addInList = true
                                    addInMenu = false
                                    customAddOpen = true
                                }) {
                                    Text("Add")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { customOpen = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                    if (customAddOpen) {
                        val parsed = parseComponent(addComponent)
                        AlertDialog(
                            onDismissRequest = { customAddOpen = false },
                            title = { Text("Add activity") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    TextField(
                                        value = addName,
                                        onValueChange = {
                                            addName = it.replace("\n", "").replace("\t", " ")
                                        },
                                        placeholder = { Text("Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextField(
                                        value = addComponent,
                                        onValueChange = {
                                            addComponent = it.replace("\n", "").replace("\t", "").replace(" ", "")
                                        },
                                        placeholder = { Text("package/activity class") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Show in list", modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = addInList,
                                            onCheckedChange = { addInList = it }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Show in menu", modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = addInMenu,
                                            onCheckedChange = { addInMenu = it }
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = parsed != null && addName.isNotBlank() && (addInList || addInMenu),
                                    onClick = {
                                        if (parsed != null) {
                                            saveCustoms(
                                                customs + CustomEntry(
                                                    addName.trim(),
                                                    parsed.packageName,
                                                    parsed.className,
                                                    addInList,
                                                    addInMenu
                                                )
                                            )
                                            load()
                                        }
                                        customAddOpen = false
                                    }
                                ) {
                                    Text("Add")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { customAddOpen = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                    menuApp?.let { app ->
                        val isHidden = app.key in hidden
                        AlertDialog(
                            onDismissRequest = { menuApp = null },
                            confirmButton = {},
                            title = { Text(app.name) },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    if (tapMenu) {
                                        Text(
                                            text = "Open",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    menuApp = null
                                                    launchApp(app)
                                                }
                                                .padding(vertical = 14.dp)
                                        )
                                    }
                                    menuShortcuts.forEach { sc ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    menuApp = null
                                                    launchShortcut(sc)
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val shortcutIcon = menuShortcutIcons[sc.id]
                                            if (shortcutIcon != null) {
                                                Image(
                                                    bitmap = shortcutIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                            }
                                            Text(sc.shortLabel?.toString() ?: sc.id)
                                        }
                                    }
                                    if (menuShortcuts.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Text(
                                        text = "App info",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                menuApp = null
                                                openAppInfo(app)
                                            }
                                            .padding(vertical = 14.dp)
                                    )
                                    if (!tapMenu) {
                                        Text(
                                            text = "Compress",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    menuApp = null
                                                    openZipScreen()
                                                }
                                                .padding(vertical = 14.dp)
                                        )
                                    }
                                    Text(
                                        text = "Rename",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                menuApp = null
                                                renameText = app.name
                                                renameApp = app
                                            }
                                            .padding(vertical = 14.dp)
                                    )
                                    Text(
                                        text = if (isHidden) "Show" else "Hide",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                menuApp = null
                                                setHidden(app.key, !isHidden)
                                            }
                                            .padding(vertical = 14.dp)
                                    )
                                    val menuCustoms = customs.filter { it.inMenu }
                                    if (menuCustoms.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    menuCustoms.forEach { c ->
                                        Text(
                                            text = c.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    menuApp = null
                                                    launchComponent(ComponentName(c.pkg, c.cls), c.name)
                                                }
                                                .padding(vertical = 14.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                    renameApp?.let { app ->
                        val original = apps.firstOrNull { it.key == app.key }?.name ?: app.name
                        AlertDialog(
                            onDismissRequest = { renameApp = null },
                            title = { Text("Rename") },
                            text = {
                                val focusRequester = remember { FocusRequester() }
                                LaunchedEffect(Unit) {
                                    try {
                                        focusRequester.requestFocus()
                                    } catch (ignored: Exception) {
                                    }
                                }
                                TextField(
                                    value = renameText,
                                    onValueChange = { renameText = it.replace("\n", "") },
                                    placeholder = { Text(original) },
                                    singleLine = true,
                                    modifier = Modifier.focusRequester(focusRequester)
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    setName(app.key, renameText)
                                    renameApp = null
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { renameApp = null }) {
                                    Text("Cancel")
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
        if (assistMode) finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (assistMode && !editing) {
            finish()
            return
        }
        editing = false
        updateList()
        scrolling = true
    }

    private fun launchApp(app: AppItem) {
        val isCustom = customs.any {
            it.inList && it.pkg == app.component.packageName && it.cls == app.component.className
        }
        if (isCustom) {
            launchComponent(app.component, app.name)
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
            if (assistMode) finish()
        } catch (ignored: Exception) {
        }
    }

    private fun launchComponent(component: ComponentName, label: String) {
        try {
            startActivity(
                Intent()
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            if (assistMode) finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open $label", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(app: AppItem) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ignored: Exception) {
        }
    }

    private fun openZipScreen() {
        try {
            startActivity(
                Intent()
                    .setComponent(
                        ComponentName(
                            "com.samsung.memorysaver",
                            "com.samsung.memorysaver.zipunzipapps.ui.activities.ZipUnzipActivity"
                        )
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ignored: Exception) {
        }
    }

    private fun loadShortcuts(app: AppItem): List<ShortcutInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val launcherApps = getSystemService(LauncherApps::class.java)
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(app.packageName)
                    .setActivity(app.component)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    )
                launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                    ?.filter { it.isEnabled }
                    ?.take(4)
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun loadShortcutIcons(shortcuts: List<ShortcutInfo>): Map<String, ImageBitmap> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val result = HashMap<String, ImageBitmap>()
            try {
                val launcherApps = getSystemService(LauncherApps::class.java)
                val density = resources.displayMetrics.densityDpi
                shortcuts.forEach { sc ->
                    try {
                        val drawable = launcherApps.getShortcutIconDrawable(sc, density)
                        if (drawable != null) {
                            result[sc.id] = drawableToBitmap(drawable, 96)
                        }
                    } catch (ignored: Exception) {
                    }
                }
            } catch (ignored: Exception) {
            }
            result
        } else {
            emptyMap()
        }
    }

    private fun launchShortcut(shortcut: ShortcutInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                getSystemService(LauncherApps::class.java).startShortcut(shortcut, null, null)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun setHidden(key: String, hide: Boolean) {
        hidden = if (hide) hidden + key else hidden - key
        getSharedPreferences("nolauncher", MODE_PRIVATE)
            .edit().putStringSet("hidden", HashSet(hidden)).apply()
        updateList()
    }

    private fun setName(key: String, name: String) {
        val value = name.trim()
        val editor = getSharedPreferences("nolauncher_names", MODE_PRIVATE).edit()
        if (value.isEmpty()) {
            names = names - key
            editor.remove(key)
        } else {
            names = names + (key to value)
            editor.putString(key, value)
        }
        editor.apply()
        updateList()
    }

    private fun parseCustoms(raw: String?): List<CustomEntry> {
        return raw.orEmpty().split("\n").mapNotNull { line ->
            val f = line.split("\t")
            if (f.size == 5) CustomEntry(f[0], f[1], f[2], f[3] == "1", f[4] == "1") else null
        }
    }

    private fun saveCustoms(list: List<CustomEntry>) {
        customs = list
        val text = list.joinToString("\n") {
            it.name + "\t" + it.pkg + "\t" + it.cls + "\t" +
                    (if (it.inList) "1" else "0") + "\t" + (if (it.inMenu) "1" else "0")
        }
        getSharedPreferences("nolauncher", MODE_PRIVATE)
            .edit().putString("custom", text).apply()
    }

    private fun parseComponent(text: String): ComponentName? {
        val parts = text.trim().split("/")
        if (parts.size != 2) return null
        val pkg = parts[0].trim()
        var cls = parts[1].trim()
        if (pkg.isEmpty() || cls.isEmpty()) return null
        if (cls.startsWith(".")) cls = pkg + cls
        return ComponentName(pkg, cls)
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

    private fun changeTapMenu(value: Boolean) {
        tapMenu = value
        getSharedPreferences("nolauncher", MODE_PRIVATE)
            .edit().putBoolean("tapmenu", value).apply()
    }

    private fun changeRightLayout(value: Boolean) {
        rightLayout = value
        getSharedPreferences("nolauncher", MODE_PRIVATE)
            .edit().putBoolean("right", value).apply()
    }

    private fun updateList() {
        val base = if (editing) apps else apps.filter { it.key !in hidden }
        shown = base.map { it.copy(name = names[it.key] ?: it.name) }.sorted()
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

                val customList = customs.filter { it.inList }
                val expectedKeys = HashSet<String>()
                result.forEach { expectedKeys.add(it.cn().flattenToString()) }
                expectedKeys.add(own.flattenToString())
                customList.forEach { expectedKeys.add(ComponentName(it.pkg, it.cls).flattenToString()) }

                val appHashCount = apps.map { it.key }.toSet().sumOf { it.hashCode() }
                val resultHashCount = expectedKeys.sumOf { it.hashCode() }

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
                    customList.forEach { c ->
                        val cn = ComponentName(c.pkg, c.cls)
                        val item = AppItem(c.name, c.pkg, cn)
                        if (temp.none { it.key == item.key }) {
                            temp.add(item)
                        }
                        if (!icons.containsKey(item.key)) {
                            try {
                                icon(item.key, packageManager.getActivityIcon(cn))
                            } catch (ignored: Exception) {
                                try {
                                    icon(item.key, packageManager.getApplicationIcon(c.pkg))
                                } catch (ignored2: Exception) {
                                }
                            }
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

    private fun drawableToBitmap(drawable: Drawable, size: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
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
