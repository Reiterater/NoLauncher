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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults.textFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.provider.Settings

class NoLauncher : ComponentActivity() {
    companion object {
        var self: NoLauncher? = null
        var apps by mutableStateOf(listOf<AppItem>())
        var icons = HashMap<String, ImageBitmap>()
    }

    private var queried by mutableStateOf(listOf<AppItem>())
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var scrolling by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var hidden by mutableStateOf(setOf<String>())
    private lateinit var appChangeReceiver: AppChangeReceiver
    private lateinit var focusManager: FocusManager

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hidden = getSharedPreferences("nolauncher", MODE_PRIVATE)
            .getStringSet("hidden", emptySet())!!.toSet()
        setContent {
            focusManager = LocalFocusManager.current
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextField(
                            value = query,
                            onValueChange = {
                                query = it.replace("\n", "")
                                query()
                            },
                            placeholder = {
                                Text(text = stringResource(id = R.string.search_by_name))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        query = ""
                                        query()
                                    }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (!editing) {
                                    queried.firstOrNull()?.let { launchApp(it) }
                                }
                            }),
                            colors = textFieldColors(
                                containerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent
                            )
                        )
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = queried,
                                key = {
                                    it.key
                                }
                            ) { app ->
                                app.Display(
                                    checked = if (editing) app.key !in hidden else null,
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
                            if (apps.isNotEmpty() && query.isEmpty()) {
                                item {
                                    Text(
                                        text = if (editing) "Done" else "Show/Hide apps",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                editing = !editing
                                                query()
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
        query = ""
        editing = false
        query()
        focusManager.clearFocus()
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
        query()
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

                if (appHashCount != resultHashCount || apps.isEmpty() || (query.trim().isEmpty() && queried.isEmpty())) {
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
                    query()
                }
            } catch (ignored: Exception) {
            }
            loading = false
        }
    }

    private fun query() {
        CoroutineScope(Dispatchers.IO).launch {
            val matchQuery = query.trim()
            val source = if (editing) apps else apps.filter { it.key !in hidden }
            queried = if (matchQuery.isEmpty()) {
                source
            } else {
                source.filter { it.name.contains(matchQuery, true) }
            }
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
