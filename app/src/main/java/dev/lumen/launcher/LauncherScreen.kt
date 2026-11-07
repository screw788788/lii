@file:OptIn(ExperimentalFoundationApi::class)

package dev.lumen.launcher

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- constants & styles ---------------------------------------------------

private const val Columns = 4
private val IconBox = (LauncherViewModel.ICON_DP + 2 * LauncherViewModel.SHADOW_DP).dp
private val RowHeight = 104.dp

private val ClockStyle = TextStyle(
    color = Color.White,
    fontSize = 66.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-1).sp,
    shadow = Shadow(Color(0x40000000), Offset(0f, 3f), 14f),
)
private val DateStyle = TextStyle(
    color = Color(0xE6FFFFFF),
    fontSize = 15.sp,
    fontWeight = FontWeight.Medium,
    shadow = Shadow(Color(0x40000000), Offset(0f, 2f), 8f),
)
private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color(0x73000000), Offset(0f, 2f), 6f),
)
private val HintStyle = TextStyle(
    color = Color(0xB3FFFFFF),
    fontSize = 13.sp,
    textAlign = TextAlign.Center,
)
private val SheetTitleStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
private val SheetLabelStyle = TextStyle(color = Color(0x99FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val RowTitleStyle = TextStyle(color = Color.White, fontSize = 15.sp)
private val RowSubStyle = TextStyle(color = Color(0x99FFFFFF), fontSize = 12.sp)

private data class MenuTarget(val app: AppEntry, val bounds: Rect)

private class MenuAction(
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

private class DragInfo {
    var total = 0f
    var fromClosed = true
}

// ---- root -----------------------------------------------------------------

@Composable
fun LauncherApp(
    vm: LauncherViewModel,
    blurSupported: Boolean,
    onBlur: (Float) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val drawer = remember { DrawerState(scope) }
    val shadePx = with(LocalDensity.current) { 80.dp.toPx() }
    val dragInfo = remember { DragInfo() }
    val enter = remember { Animatable(1f) }

    var query by rememberSaveable { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }

    // Only flips twice per open/close, so the whole tree doesn't recompose every animation frame.
    val drawerVisible by remember { derivedStateOf { drawer.progress > 0.001f } }

    val openApp = remember(context, view) {
        { app: AppEntry, bounds: Rect -> launchApp(context, view, app, bounds) }
    }
    val showMenu = remember(haptics) {
        { app: AppEntry, bounds: Rect ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menuTarget = MenuTarget(app, bounds)
            menuVisible = true
        }
    }

    BackHandler {
        if (sheetVisible) {
            sheetVisible = false
        } else if (menuVisible) {
            menuVisible = false
        } else if (drawer.progress > 0f) {
            if (query.isNotEmpty()) {
                query = ""
            } else {
                drawer.close()
            }
        }
    }

    // Home pressed while we're already home
    LaunchedEffect(vm.homeSignal) {
        if (vm.homeSignal > 0) {
            menuVisible = false
            sheetVisible = false
            drawer.close()
            focus.clearFocus()
        }
    }

    // Coming back to the launcher (from an app, or the first launch): home zooms/fades in.
    LaunchedEffect(vm.resumeSignal) {
        if (vm.resumeSignal > 0) {
            enter.snapTo(0f)
            enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f))
        }
    }

    LaunchedEffect(drawerVisible) {
        if (!drawerVisible) {
            query = ""
            focus.clearFocus()
        }
    }

    // Wallpaper blur follows the drawer drag (Android 12+, when the device allows it)
    LaunchedEffect(drawer, blurSupported) {
        if (blurSupported) {
            snapshotFlow { drawer.progress.coerceIn(0f, 1f) }.collect { onBlur(it) }
        }
    }

    val actions = menuTarget?.let { t ->
        buildMenuActions(context, vm, t.app) { menuVisible = false }
    } ?: emptyList()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                enabled = !sheetVisible,
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    dragInfo.total += delta
                    drawer.dragBy(delta)
                },
                onDragStarted = {
                    dragInfo.total = 0f
                    dragInfo.fromClosed = drawer.progress <= 0f
                    menuVisible = false
                },
                onDragStopped = { velocity ->
                    if (dragInfo.fromClosed && drawer.progress <= 0f && dragInfo.total > shadePx) {
                        expandNotifications(context)
                    } else {
                        drawer.settle(velocity)
                    }
                },
            ),
    ) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight
        SideEffect { drawer.travelPx = screenH * 0.55f }

        val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)

        // ---- home layer: scales back and fades as the drawer comes in, springs in on return
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawer.progress.coerceIn(0f, 1f)
                    val e = enter.value
                    val s = (1f - 0.08f * p) * (1.1f - 0.1f * e)
                    scaleX = s
                    scaleY = s
                    alpha = (1f - p) * e.coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .pointerInput(Unit) {
                    // Long-press empty space -> home settings
                    detectTapGestures(
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sheetVisible = true
                        },
                    )
                },
        ) {
            // Soft shading so white text stays readable on any wallpaper
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0x59000000), Color.Transparent))),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)))),
            )

            Column(Modifier.fillMaxSize().windowInsetsPadding(safe)) {
                ClockHeader(now = vm.now)
                HomePager(
                    apps = vm.homeApps,
                    homeSignal = vm.homeSignal,
                    onOpen = openApp,
                    onMenu = showMenu,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                SwipeHandle(onClick = { drawer.open() })
                Dock(apps = vm.dockApps, onOpen = openApp, onMenu = showMenu)
                Spacer(Modifier.height(10.dp))
            }
        }

        // ---- drawer layer
        if (drawerVisible) {
            val tint = if (blurSupported) 0.30f else 0.66f
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(Color.Black, alpha = tint * drawer.progress.coerceIn(0f, 1f))
                    }
                    // Swallows touches so nothing underneath reacts; tap empty space to close.
                    .pointerInput(Unit) { detectTapGestures { drawer.close() } },
            ) {
                AppDrawer(
                    apps = vm.apps,
                    query = query,
                    onQueryChange = { query = it },
                    drawer = drawer,
                    onOpen = { app, bounds ->
                        openApp(app, bounds)
                        scope.launch {
                            delay(450)
                            drawer.close()
                        }
                    },
                    onMenu = showMenu,
                    modifier = Modifier.graphicsLayer {
                        val p = drawer.progress.coerceIn(0f, 1f)
                        val s = 0.92f + 0.08f * p
                        alpha = p
                        scaleX = s
                        scaleY = s
                        translationY = (1f - p) * 56.dp.toPx()
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                )
            }
        }

        // ---- long-press menu
        MenuOverlay(
            visible = menuVisible,
            target = menuTarget,
            actions = actions,
            screenW = screenW,
            screenH = screenH,
            onDismiss = { menuVisible = false },
        )

        // ---- home settings sheet
        SettingsSheet(
            visible = sheetVisible,
            allOnHome = vm.allOnHome,
            onAllOnHome = { vm.setAllOnHome(it) },
            onWallpaper = { style ->
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { applyWallpaper(context, style) }
                    Toast.makeText(
                        context,
                        if (ok) "Wallpaper applied" else "Couldn't set wallpaper",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onMoreWallpapers = {
                sheetVisible = false
                runCatching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onReset = {
                vm.resetLayout()
                sheetVisible = false
                Toast.makeText(context, "Home layout reset", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { sheetVisible = false },
        )
    }
}

// ---- home -----------------------------------------------------------------

@Composable
private fun ClockHeader(now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val time = remember(now, is24) {
        SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    val date = remember(now) {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 8.dp)
            .pointerInput(Unit) { detectTapGestures { openClock(context) } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(time, style = ClockStyle)
        BasicText(date, style = DateStyle)
    }
}

@Composable
private fun HomePager(
    apps: List<AppEntry>,
    homeSignal: Int,
    onOpen: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current

    BoxWithConstraints(modifier) {
        val rows = ((maxHeight - 28.dp) / RowHeight).toInt().coerceIn(1, 6)
        val perPage = rows * Columns
        val pageCount = maxOf(1, (apps.size + perPage - 1) / perPage)
        val pager = rememberPagerState(pageCount = { pageCount })

        LaunchedEffect(homeSignal) {
            if (homeSignal > 0) pager.animateScrollToPage(0)
        }

        // Wallpaper parallax: the wallpaper slides a little as you swipe between pages.
        LaunchedEffect(pager, pageCount) {
            val wm = WallpaperManager.getInstance(context)
            snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { position ->
                val token = view.windowToken
                if (token != null) {
                    try {
                        val x = if (pageCount > 1) (position / (pageCount - 1)).coerceIn(0f, 1f) else 0.5f
                        wm.setWallpaperOffsetSteps(if (pageCount > 1) 1f / (pageCount - 1) else 1f, 1f)
                        wm.setWallpaperOffsets(token, x, 0.5f)
                    } catch (e: Throwable) {
                        // not supported on this device
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            if (apps.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText("Swipe up to see all apps", style = HintStyle)
                }
            } else {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    val slice = apps.drop(page * perPage).take(perPage)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .graphicsLayer {
                                // Pages shrink + fade slightly as they slide away
                                val distance = abs((pager.currentPage - page) + pager.currentPageOffsetFraction)
                                    .coerceIn(0f, 1f)
                                val s = 1f - 0.10f * distance
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.65f * distance
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                    ) {
                        slice.chunked(Columns).forEach { rowApps ->
                            Row(Modifier.fillMaxWidth().height(RowHeight)) {
                                rowApps.forEach { app ->
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        AppIcon(app, showLabel = true, onClick = onOpen, onLongClick = onMenu)
                                    }
                                }
                                repeat(Columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (pageCount > 1) PageDots(count = pageCount, current = pager.currentPage)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { i ->
                val width by animateDpAsState(
                    targetValue = if (i == current) 16.dp else 6.dp,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                    label = "dot",
                )
                Box(
                    Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.45f)),
                )
            }
        }
    }
}

/** Small pill above the dock: shows there's a drawer, and tapping it opens it. */
@Composable
private fun SwipeHandle(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun Dock(
    apps: List<AppEntry>,
    onOpen: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(92.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.16f))))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BasicText("Long-press an app in the drawer to dock it", style = HintStyle)
            }
        } else {
            apps.forEach { app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AppIcon(app, showLabel = false, onClick = onOpen, onLongClick = onMenu)
                }
            }
            repeat(LauncherViewModel.DOCK_SIZE - apps.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ---- icon -----------------------------------------------------------------

@Composable
private fun AppIcon(
    app: AppEntry,
    showLabel: Boolean,
    onClick: (AppEntry, Rect) -> Unit,
    onLongClick: (AppEntry, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)

    // Springy press-down/release
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "iconPress",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(app.key) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { click(app, coords.bounds()) },
                    onLongPress = { longClick(app, coords.bounds()) },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(IconBox)
                .onGloballyPositioned { coords[0] = it },
        )
        if (showLabel) {
            BasicText(
                text = app.label,
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(IconBox + 14.dp),
            )
        }
    }
}

private fun Array<LayoutCoordinates?>.bounds(): Rect =
    this[0]?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero

// ---- drawer ---------------------------------------------------------------

private fun letterOf(label: String): String {
    val c = label.firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    drawer: DrawerState,
    onOpen: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val gridState = rememberLazyGridState()
    val connection = remember(drawer) { DrawerNestedScroll(drawer) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // A-Z index (only for long lists, and not while searching)
    val firstIndex = remember(filtered) {
        val map = LinkedHashMap<String, Int>()
        filtered.forEachIndexed { i, app -> map.getOrPut(letterOf(app.label)) { i } }
        map
    }
    val letters = remember(firstIndex) { firstIndex.keys.toList() }
    val showIndex = query.isEmpty() && filtered.size > 24 && letters.size > 3
    var activeLetter by remember { mutableStateOf<String?>(null) }
    var bubbleLetter by remember { mutableStateOf("") }

    LaunchedEffect(query) { gridState.scrollToItem(0) }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onGo = { filtered.firstOrNull()?.let { onOpen(it, Rect.Zero) } },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
                    BasicText("No apps found", style = HintStyle)
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(Columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(connection),
                    contentPadding = PaddingValues(
                        start = 10.dp,
                        end = if (showIndex) 28.dp else 10.dp,
                        top = 8.dp,
                        bottom = navBottom + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(filtered, key = { it.key }) { app ->
                        Box(
                            Modifier
                                .animateItem()
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            AppIcon(app, showLabel = true, onClick = onOpen, onLongClick = onMenu)
                        }
                    }
                }
            }
        }

        if (showIndex) {
            LetterIndex(
                letters = letters,
                active = activeLetter,
                onPick = { letter ->
                    if (letter != activeLetter) {
                        activeLetter = letter
                        bubbleLetter = letter
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { gridState.scrollToItem(firstIndex[letter] ?: 0) }
                    }
                },
                onRelease = { activeLetter = null },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.7f)
                    .padding(end = 2.dp),
            )
        }

        AnimatedVisibility(
            visible = activeLetter != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(80)) + scaleIn(initialScale = 0.7f),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xCC1E1F24)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    bubbleLetter,
                    style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

private fun letterAt(y: Float, height: Int, letters: List<String>): String {
    val i = (y / height.coerceAtLeast(1) * letters.size).toInt().coerceIn(0, letters.lastIndex)
    return letters[i]
}

/** The A-Z strip on the right edge of the drawer: touch or drag along it to jump. */
@Composable
private fun LetterIndex(
    letters: List<String>,
    active: String?,
    onPick: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pick by rememberUpdatedState(onPick)
    val release by rememberUpdatedState(onRelease)
    Column(
        modifier
            .width(30.dp)
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(letterAt(down.position.y, size.height, letters))
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        pick(letterAt(change.position.y, size.height, letters))
                        change.consume()
                    } while (change.pressed)
                    release()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        letters.forEach { letter ->
            val isActive = letter == active
            BasicText(
                letter,
                style = TextStyle(
                    color = if (isActive) Color.White else Color(0xB3FFFFFF),
                    fontSize = if (isActive) 14.sp else 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * Lets the grid and the drawer share one gesture: pulling down while the list is at the
 * top drags the drawer closed, and pushing back up re-opens it before the list scrolls.
 */
private class DrawerNestedScroll(private val drawer: DrawerState) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y < 0f && drawer.progress < 1f) {
            drawer.dragBy(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            drawer.dragBy(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drawer.progress > 0.001f && drawer.progress < 0.999f) {
            drawer.settle(available.y)
            return available
        }
        return Velocity.Zero
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.16f))))
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                BasicText(
                    "Search apps",
                    style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 16.sp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
    }
}

@Composable
private fun SearchGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        val r = size.minDimension * 0.32f
        val c = Offset(size.width * 0.42f, size.height * 0.42f)
        val color = Color.White.copy(alpha = 0.85f)
        drawCircle(color, radius = r, center = c, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// ---- long-press menu ------------------------------------------------------

@Composable
private fun MenuOverlay(
    visible: Boolean,
    target: MenuTarget?,
    actions: List<MenuAction>,
    screenW: Int,
    screenH: Int,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(140)),
    ) {
        if (target != null) {
            val cardW = with(density) { 210.dp.toPx() }
            val margin = with(density) { 16.dp.toPx() }
            val gap = with(density) { 10.dp.toPx() }
            val bounds = target.bounds
            val below = bounds.center.y < screenH * 0.55f
            val x = (bounds.center.x - cardW / 2f)
                .coerceIn(margin, (screenW - cardW - margin).coerceAtLeast(margin))
                .roundToInt()
            val origin = TransformOrigin(0.5f, if (below) 0f else 1f)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            ) {
                val placement = if (below) {
                    Modifier.offset { IntOffset(x, (bounds.bottom + gap).roundToInt()) }
                } else {
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(x, -(screenH - bounds.top + gap).roundToInt()) }
                }
                Column(
                    placement
                        .width(210.dp)
                        .animateEnterExit(
                            enter = fadeIn(tween(120)) + scaleIn(
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                                initialScale = 0.82f,
                                transformOrigin = origin,
                            ),
                            exit = fadeOut(tween(100)) + scaleOut(
                                targetScale = 0.92f,
                                transformOrigin = origin,
                            ),
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xF01E1F24))
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    actions.forEach { MenuRow(it) }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(action: MenuAction) {
    var pressed by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(action)
    BasicText(
        text = action.label,
        style = TextStyle(
            color = if (action.danger) Color(0xFFFF6B6B) else Color.White,
            fontSize = 15.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { current.onClick() },
                )
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    )
}

private fun buildMenuActions(
    context: Context,
    vm: LauncherViewModel,
    app: AppEntry,
    dismiss: () -> Unit,
): List<MenuAction> = buildList {
    if (!vm.allOnHome) {
        if (app.key in vm.homeKeys) {
            add(MenuAction("Remove from Home") { vm.removeFromHome(app); dismiss() })
        } else {
            add(MenuAction("Add to Home") { vm.addToHome(app); dismiss() })
        }
    }
    if (app.key in vm.dockKeys) {
        add(MenuAction("Remove from Dock") { vm.removeFromDock(app); dismiss() })
    } else if (vm.dockKeys.size < LauncherViewModel.DOCK_SIZE) {
        add(MenuAction("Add to Dock") { vm.addToDock(app); dismiss() })
    }
    add(MenuAction("App info") { openAppInfo(context, app); dismiss() })
    add(MenuAction("Uninstall", danger = true) { uninstall(context, app); dismiss() })
}

// ---- home settings sheet --------------------------------------------------

@Composable
private fun SettingsSheet(
    visible: Boolean,
    allOnHome: Boolean,
    onAllOnHome: (Boolean) -> Unit,
    onWallpaper: (WallpaperStyle) -> Unit,
    onMoreWallpapers: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Small previews rendered from the same generator that sets the real wallpaper
    val thumbs = remember {
        WallpaperStyles.all.map { it to renderWallpaper(it, 108, 192).asImageBitmap() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(180)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                            initialOffsetY = { it },
                        ),
                        exit = slideOutVertically(
                            animationSpec = tween(200),
                            targetOffsetY = { it },
                        ),
                    )
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xF2202228))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                )
                Spacer(Modifier.height(14.dp))
                BasicText("Home settings", style = SheetTitleStyle)
                Spacer(Modifier.height(16.dp))

                BasicText("WALLPAPER", style = SheetLabelStyle)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    thumbs.forEach { (style, bitmap) ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = style.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(72.dp)
                                .height(128.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .pointerInput(style) { detectTapGestures { onWallpaper(style) } },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                SheetRow(
                    title = "More wallpapers…",
                    subtitle = "Pick a photo or live wallpaper",
                    onClick = onMoreWallpapers,
                )
                SheetRow(
                    title = "Show all apps on home",
                    subtitle = if (allOnHome) "Every app is on the home pages" else "You choose which apps live on home",
                    trailing = { LumenSwitch(allOnHome) },
                    onClick = { onAllOnHome(!allOnHome) },
                )
                SheetRow(
                    title = "Reset home layout",
                    subtitle = "Restore the default dock and home apps",
                    onClick = onReset,
                )
            }
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(onClick)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { current() },
                )
            }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = RowTitleStyle)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(subtitle, style = RowSubStyle)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun LumenSwitch(checked: Boolean) {
    val knobX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "knob",
    )
    val track by animateColorAsState(
        targetValue = if (checked) Color(0xFF14B8A6) else Color(0x40FFFFFF),
        label = "track",
    )
    Box(
        Modifier
            .width(50.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 2.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

// ---- system actions -------------------------------------------------------

private fun launchApp(context: Context, view: View, app: AppEntry, bounds: Rect) {
    val options = if (bounds != Rect.Zero) {
        // Zoom-from-icon open animation
        ActivityOptions.makeScaleUpAnimation(
            view,
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.width.roundToInt(),
            bounds.height.roundToInt(),
        ).toBundle()
    } else {
        null
    }
    try {
        context.getSystemService(LauncherApps::class.java)
            .startMainActivity(app.component, Process.myUserHandle(), null, options)
    } catch (e: Exception) {
        Toast.makeText(context, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

private fun openAppInfo(context: Context, app: AppEntry) {
    runCatching {
        context.getSystemService(LauncherApps::class.java)
            .startAppDetailsActivity(app.component, Process.myUserHandle(), null, null)
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", app.component.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun uninstall(context: Context, app: AppEntry) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts("package", app.component.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "Can't uninstall ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

private fun openClock(context: Context) {
    runCatching {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Best effort: works on most devices; silently does nothing if the OS blocks it. */
private fun expandNotifications(context: Context) {
    try {
        val service = context.getSystemService("statusbar") ?: return
        service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
    } catch (e: Throwable) {
        // ignored
    }
}
