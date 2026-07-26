package com.ai.assistance.operit.ui.main.shell

import android.Manifest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.opposite
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserSearchScreen
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_CENTER_ICON_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP
import com.ai.assistance.operit.ui.features.websession.browser.chrome.WebSessionBrowserWindowCountIcon
import com.ai.assistance.operit.ui.main.AiHomeQuickAction
import com.ai.assistance.operit.ui.main.weather.KiyoriWeatherRepository
import com.ai.assistance.operit.ui.main.weather.KiyoriWeatherState
import com.ai.assistance.operit.ui.main.weather.KiyoriWeatherVisual
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PrimaryDestinationVisual(
    val destination: PrimaryDestination,
    val labelResId: Int,
    val iconResId: Int,
    val iconSizeDp: Int = WEB_SESSION_BROWSER_BOTTOM_ICON_SIZE_DP,
)

private val primaryDestinationVisuals =
    listOf(
        PrimaryDestinationVisual(
            PrimaryDestination.SOFTWARE_HOME,
            R.string.kiyori_shell_software_home,
            R.drawable.ic_kiyori_nav_home,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.BROWSER_HOME,
            R.string.kiyori_shell_browser_home,
            R.drawable.ic_kiyori_nav_globe,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.MINI_APP_HOME,
            R.string.kiyori_shell_mini_app_home,
            R.drawable.ic_kiyori_nav_apps,
            WEB_SESSION_BROWSER_BOTTOM_CENTER_ICON_SIZE_DP,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.FILE_MANAGEMENT_HOME,
            R.string.kiyori_shell_file_management_home,
            R.drawable.ic_kiyori_nav_folder,
        ),
        PrimaryDestinationVisual(
            PrimaryDestination.SETTINGS_HOME,
            R.string.kiyori_shell_settings_home,
            R.drawable.ic_kiyori_tool_settings,
        ),
    )

@Composable
internal fun KiyoriSoftwareHomePage(
    browserWindowCount: Int,
    onSearchClick: () -> Unit,
    onAiClick: () -> Unit,
    onAiQuickAction: (AiHomeQuickAction) -> Unit,
    onWeatherSearch: (String) -> Unit,
    onWindowsClick: () -> Unit,
) {
    var selectedMode by rememberSaveable { mutableStateOf(KiyoriSoftwareHomeMode.SEARCH) }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(bottom = 72.dp),
    ) {
        val layout = resolveKiyoriSoftwareHomeLayout(maxWidth.value)
        val heightLayout = resolveKiyoriSoftwareHomeHeightLayout(maxHeight.value)
        val horizontalPadding =
            when (layout) {
                KiyoriSoftwareHomeLayout.COMPACT ->
                    KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP.dp
                KiyoriSoftwareHomeLayout.MEDIUM ->
                    KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP.dp
                KiyoriSoftwareHomeLayout.EXPANDED ->
                    KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP.dp
            }
        val contentMaxWidth =
            when (layout) {
                KiyoriSoftwareHomeLayout.COMPACT -> KIYORI_HOME_COMPACT_MAX_WIDTH_DP.dp
                KiyoriSoftwareHomeLayout.MEDIUM -> KIYORI_HOME_MEDIUM_MAX_WIDTH_DP.dp
                KiyoriSoftwareHomeLayout.EXPANDED -> KIYORI_HOME_EXPANDED_MAX_WIDTH_DP.dp
            }
        val useShortViewportLayout = heightLayout == KiyoriSoftwareHomeHeightLayout.SHORT
        val searchFrameHeight =
            if (useShortViewportLayout) {
                KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP.dp
            } else {
                KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP.dp
            }
        val brandSearchSpacing = if (useShortViewportLayout) 8.dp else 16.dp
        val topActionsVerticalPadding = if (useShortViewportLayout) 0.dp else 6.dp

        Layout(
            modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
            content = {
                KiyoriHomeBrandTitle()
                KiyoriHomeSearchFrame(
                    mode = selectedMode,
                    onModeSelected = { mode -> selectedMode = mode },
                    onPrimaryClick = {
                        when (resolveKiyoriSoftwareHomePrimaryTarget(selectedMode)) {
                            KiyoriSoftwareHomePrimaryTarget.WEB_SEARCH -> onSearchClick()
                            KiyoriSoftwareHomePrimaryTarget.AI_HOME -> onAiClick()
                        }
                    },
                    onAiQuickAction = { action ->
                        selectedMode = KiyoriSoftwareHomeMode.AI
                        onAiQuickAction(action)
                    },
                    frameHeight = searchFrameHeight,
                    useShortViewportLayout = useShortViewportLayout,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) { measurables, constraints ->
            val contentWidth = minOf(constraints.maxWidth, contentMaxWidth.roundToPx())
            val titlePlaceable =
                measurables[0].measure(
                    constraints.copy(minWidth = 0, maxWidth = contentWidth, minHeight = 0),
                )
            val searchPlaceable =
                measurables[1].measure(
                    Constraints(
                        minWidth = contentWidth,
                        maxWidth = contentWidth,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight,
                    ),
                )
            val spacing = brandSearchSpacing.roundToPx()
            val searchTopY =
                resolveKiyoriSoftwareHomeSearchTopY(
                    availableHeight = constraints.maxHeight.toFloat(),
                    titleHeight = titlePlaceable.height.toFloat(),
                    titleSpacing = spacing.toFloat(),
                ).roundToInt()
            val titleY = searchTopY - spacing - titlePlaceable.height

            layout(constraints.maxWidth, constraints.maxHeight) {
                titlePlaceable.placeRelative(
                    x = (constraints.maxWidth - titlePlaceable.width) / 2,
                    y = titleY,
                )
                searchPlaceable.placeRelative(
                    x = (constraints.maxWidth - searchPlaceable.width) / 2,
                    y = searchTopY,
                )
            }
        }
        KiyoriHomeTopActions(
            browserWindowCount = browserWindowCount,
            onWeatherSearch = onWeatherSearch,
            onWindowsClick = onWindowsClick,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = topActionsVerticalPadding,
                    ),
        )
    }
}

internal enum class KiyoriSoftwareHomeLayout {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal enum class KiyoriSoftwareHomeHeightLayout {
    SHORT,
    REGULAR,
}

internal enum class KiyoriSoftwareHomeMode {
    SEARCH,
    AI,
}

internal enum class KiyoriSoftwareHomePrimaryTarget {
    WEB_SEARCH,
    AI_HOME,
}

internal const val KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION = 0.382f
internal const val KIYORI_HOME_MODE_SEGMENT_WIDTH_DP = 120
internal const val KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP = 32
internal const val KIYORI_HOME_BRAND_ICON_SIZE_DP = 18
internal const val KIYORI_HOME_REGULAR_SEARCH_FRAME_HEIGHT_DP = 114
internal const val KIYORI_HOME_SHORT_SEARCH_FRAME_HEIGHT_DP = 96
internal const val KIYORI_HOME_COMPACT_HORIZONTAL_PADDING_DP = 24
internal const val KIYORI_HOME_MEDIUM_HORIZONTAL_PADDING_DP = 48
internal const val KIYORI_HOME_EXPANDED_HORIZONTAL_PADDING_DP = 72
internal const val KIYORI_HOME_COMPACT_MAX_WIDTH_DP = 544
internal const val KIYORI_HOME_MEDIUM_MAX_WIDTH_DP = 584
internal const val KIYORI_HOME_EXPANDED_MAX_WIDTH_DP = 624
internal const val KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP = 1
internal const val KIYORI_HOME_TOOL_ICON_SIZE_DP = 20
internal const val KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP = 24
internal const val KIYORI_HOME_ATTACHMENT_ICON_ALPHA = 0.9f
internal val KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS =
    listOf(
        Color(0xFF54C878),
        Color(0xFF45B9D4),
        Color(0xFF8277DA),
        Color(0xFFF09A6C),
        Color(0xFF54C878),
    )
internal val KIYORI_HOME_SELECTED_LIGHT_COLOR = Color(0xFF2F6FED)
internal val KIYORI_HOME_SELECTED_DARK_COLOR = Color(0xFF79A7FF)

internal fun resolveKiyoriSoftwareHomeLayout(widthDp: Float): KiyoriSoftwareHomeLayout =
    when {
        widthDp >= 840f -> KiyoriSoftwareHomeLayout.EXPANDED
        widthDp >= 600f -> KiyoriSoftwareHomeLayout.MEDIUM
        else -> KiyoriSoftwareHomeLayout.COMPACT
    }

internal fun resolveKiyoriSoftwareHomeHeightLayout(heightDp: Float): KiyoriSoftwareHomeHeightLayout =
    if (heightDp < 440f) {
        KiyoriSoftwareHomeHeightLayout.SHORT
    } else {
        KiyoriSoftwareHomeHeightLayout.REGULAR
    }

internal fun resolveKiyoriSoftwareHomeSearchTopY(
    availableHeight: Float,
    titleHeight: Float,
    titleSpacing: Float,
): Float =
    maxOf(
        availableHeight * KIYORI_SOFTWARE_HOME_GOLDEN_TOP_FRACTION,
        titleHeight + titleSpacing,
    )

internal fun resolveKiyoriSoftwareHomePrimaryTarget(
    mode: KiyoriSoftwareHomeMode,
): KiyoriSoftwareHomePrimaryTarget =
    when (mode) {
        KiyoriSoftwareHomeMode.SEARCH -> KiyoriSoftwareHomePrimaryTarget.WEB_SEARCH
        KiyoriSoftwareHomeMode.AI -> KiyoriSoftwareHomePrimaryTarget.AI_HOME
    }

@Composable
private fun KiyoriHomeTopActions(
    browserWindowCount: Int,
    onWeatherSearch: (String) -> Unit,
    onWindowsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val weatherRepository =
        remember(context) { KiyoriWeatherRepository.getInstance(context.applicationContext) }
    val weatherState by weatherRepository.state.collectAsState()
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            weatherRepository.recordPermissionResult(
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            )
        }

    LaunchedEffect(lifecycleOwner, weatherRepository) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                weatherRepository.refresh()
                delay(KiyoriWeatherRepository.REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KiyoriWeatherButton(
            state = weatherState,
            onClick = {
                when (val current = weatherState) {
                    is KiyoriWeatherState.Available -> onWeatherSearch(current.city)
                    KiyoriWeatherState.PermissionRequired,
                    KiyoriWeatherState.PermissionDenied ->
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    KiyoriWeatherState.Loading -> Unit
                    is KiyoriWeatherState.Unavailable -> weatherRepository.refresh(force = true)
                }
            },
            modifier = Modifier.weight(1f, fill = false),
        )
        KiyoriBrowserWindowsButton(
            windowCount = browserWindowCount,
            onClick = onWindowsClick,
        )
    }
}

@Composable
private fun KiyoriHomeBrandTitle() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "Kiyori",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Image(
            painter = painterResource(R.drawable.ic_kiyori_app_icon),
            contentDescription = null,
            modifier =
                Modifier
                    .offset(y = (-4).dp)
                    .size(KIYORI_HOME_BRAND_ICON_SIZE_DP.dp),
        )
    }
}

@Composable
private fun KiyoriHomeSearchFrame(
    mode: KiyoriSoftwareHomeMode,
    onModeSelected: (KiyoriSoftwareHomeMode) -> Unit,
    onPrimaryClick: () -> Unit,
    onAiQuickAction: (AiHomeQuickAction) -> Unit,
    frameHeight: Dp,
    useShortViewportLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(frameHeight)
                .kiyoriGradientSearchFrame(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (useShortViewportLayout) 8.dp else 10.dp,
                    ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onPrimaryClick)
                        .padding(horizontal = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Crossfade(
                    targetState = mode,
                    animationSpec = tween(durationMillis = 150),
                ) { currentMode ->
                    Text(
                        text =
                            stringResource(
                                when (currentMode) {
                                    KiyoriSoftwareHomeMode.SEARCH -> R.string.kiyori_shell_search_hint
                                    KiyoriSoftwareHomeMode.AI -> R.string.kiyori_shell_ai_hint
                                },
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KiyoriSearchAiSegment(
                    selectedMode = mode,
                    onModeSelected = onModeSelected,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    KiyoriHomeToolButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_attachment),
                        onClick = { onAiQuickAction(AiHomeQuickAction.OPEN_ATTACHMENTS) },
                        iconSizeDp = KIYORI_HOME_ATTACHMENT_ICON_SIZE_DP,
                        tintAlpha = KIYORI_HOME_ATTACHMENT_ICON_ALPHA,
                    )
                    KiyoriHomeToolButton(
                        icon = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.voice_input),
                        onClick = { onAiQuickAction(AiHomeQuickAction.START_VOICE_SESSION) },
                    )
                    KiyoriHomeToolButton(
                        icon = Icons.Default.PhotoCamera,
                        contentDescription = stringResource(R.string.attachment_camera),
                        onClick = { onAiQuickAction(AiHomeQuickAction.CAPTURE_PHOTO) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KiyoriSearchAiSegment(
    selectedMode: KiyoriSoftwareHomeMode,
    onModeSelected: (KiyoriSoftwareHomeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .width(KIYORI_HOME_MODE_SEGMENT_WIDTH_DP.dp)
                .height(KIYORI_HOME_MODE_SEGMENT_HEIGHT_DP.dp),
        shape = RoundedCornerShape(11.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KiyoriSearchAiSegmentOption(
                icon = Icons.Default.Search,
                label = stringResource(R.string.kiyori_shell_search_action),
                selected = selectedMode == KiyoriSoftwareHomeMode.SEARCH,
                onClick = { onModeSelected(KiyoriSoftwareHomeMode.SEARCH) },
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            )
            KiyoriSearchAiSegmentOption(
                icon = Icons.Default.AutoAwesome,
                label = stringResource(R.string.kiyori_shell_ai_action),
                selected = selectedMode == KiyoriSoftwareHomeMode.AI,
                onClick = { onModeSelected(KiyoriSoftwareHomeMode.AI) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KiyoriSearchAiSegmentOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColor =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            KIYORI_HOME_SELECTED_DARK_COLOR
        } else {
            KIYORI_HOME_SELECTED_LIGHT_COLOR
        }
    val contentColor by
        animateColorAsState(
            targetValue =
                if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(durationMillis = 150),
            label = "homeModeContent",
        )
    val backgroundColor by
        animateColorAsState(
            targetValue = if (selected) selectedColor.copy(alpha = 0.1f) else Color.Transparent,
            animationSpec = tween(durationMillis = 150),
            label = "homeModeBackground",
        )

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun KiyoriHomeToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSizeDp: Int = KIYORI_HOME_TOOL_ICON_SIZE_DP,
    tintAlpha: Float = 1f,
) {
    Box(
        modifier =
            Modifier
                .size(38.dp)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSizeDp.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = tintAlpha),
        )
    }
}

@Composable
private fun KiyoriWeatherButton(
    state: KiyoriWeatherState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescription =
        when (state) {
            is KiyoriWeatherState.Available ->
                stringResource(
                    R.string.kiyori_home_weather_current,
                    state.city,
                    state.temperatureCelsius.roundToInt(),
                )
            KiyoriWeatherState.Loading -> stringResource(R.string.kiyori_home_weather_loading)
            KiyoriWeatherState.PermissionRequired,
            KiyoriWeatherState.PermissionDenied ->
                stringResource(R.string.kiyori_home_weather_permission)
            is KiyoriWeatherState.Unavailable -> stringResource(R.string.kiyori_home_weather_retry)
        }
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp).height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        enabled = state != KiyoriWeatherState.Loading,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is KiyoriWeatherState.Available -> {
                    Icon(
                        imageVector = kiyoriWeatherIcon(state.visual),
                        contentDescription = contentDescription,
                        modifier = Modifier.size(21.dp),
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "${state.temperatureCelsius.roundToInt()}\u00B0",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Text(
                            text = state.city,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                KiyoriWeatherState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                KiyoriWeatherState.PermissionRequired,
                KiyoriWeatherState.PermissionDenied ->
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(21.dp),
                    )
                is KiyoriWeatherState.Unavailable ->
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(21.dp),
                    )
            }
        }
    }
}

@Composable
private fun KiyoriBrowserWindowsButton(
    windowCount: Int,
    onClick: () -> Unit,
) {
    val windowDescription = stringResource(R.string.kiyori_home_browser_windows, windowCount)
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .size(48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = windowDescription
                },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            WebSessionBrowserWindowCountIcon(
                count = windowCount,
            )
        }
    }
}

private fun kiyoriWeatherIcon(visual: KiyoriWeatherVisual): ImageVector =
    when (visual) {
        KiyoriWeatherVisual.CLEAR -> Icons.Default.WbSunny
        KiyoriWeatherVisual.PARTLY_CLOUDY -> Icons.Default.CloudQueue
        KiyoriWeatherVisual.OVERCAST -> Icons.Default.Cloud
        KiyoriWeatherVisual.FOG -> Icons.Default.Dehaze
        KiyoriWeatherVisual.DRIZZLE -> Icons.Default.Grain
        KiyoriWeatherVisual.RAIN -> Icons.Default.WaterDrop
        KiyoriWeatherVisual.SNOW -> Icons.Default.AcUnit
        KiyoriWeatherVisual.THUNDERSTORM -> Icons.Default.FlashOn
    }

private fun Modifier.kiyoriGradientSearchFrame(): Modifier =
    drawWithCache {
        val strokeWidth = KIYORI_HOME_SEARCH_FRAME_STROKE_WIDTH_DP.dp.toPx()
        val frameInset = strokeWidth / 2f
        val cornerRadius = 18.dp.toPx() - frameInset
        val frameSize =
            Size(
                width = size.width - frameInset * 2f,
                height = size.height - frameInset * 2f,
            )
        val frameTopLeft = Offset(frameInset, frameInset)
        val borderBrush = Brush.linearGradient(KIYORI_HOME_SEARCH_FRAME_GRADIENT_COLORS)

        onDrawBehind {
            drawRoundRect(
                brush = borderBrush,
                topLeft = frameTopLeft,
                size = frameSize,
                cornerRadius = CornerRadius(cornerRadius),
                style = Stroke(width = strokeWidth),
            )
        }
    }

@Composable
internal fun KiyoriPrimaryRootPage(
    destination: PrimaryDestination,
    onOpenAiSettings: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        PrimaryDestination.FILE_MANAGEMENT_HOME -> {
            KiyoriFileManagementPage(modifier = modifier)
            return
        }
        PrimaryDestination.SETTINGS_HOME -> {
            KiyoriSettingsHomePage(
                onOpenAiSettings = onOpenAiSettings,
                onOpenBrowserSettings = onOpenBrowserSettings,
                onOpenDownloadSettings = onOpenDownloadSettings,
                modifier = modifier,
            )
            return
        }
        else -> Unit
    }
    val visual = primaryDestinationVisuals.single { item -> item.destination == destination }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background).padding(bottom = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(visual.iconResId),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(visual.labelResId),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun KiyoriFullScreenWebSearchPage(
    onBack: () -> Unit,
    onSubmitSearch: (KiyoriWebSearchRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val historyStore = remember(context) { WebSessionHistoryStore.getInstance(context) }
    val browserCoordinator = remember(context) { BrowserPresentationCoordinator.getInstance(context) }
    var profileState by
        remember(browserCoordinator) {
            mutableStateOf(browserCoordinator.newSessionProfileState())
        }
    val searchEngine by
        historyStore.searchEngineFlow.collectAsState(initial = WebSessionSearchEngine.DEFAULT)
    val searchHistory by historyStore.searchHistoryFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var isEnginePanelVisible by rememberSaveable { mutableStateOf(false) }
    var selectedProfile by rememberSaveable { mutableStateOf(profileState.defaultProfile) }
    var profileFeedback by remember { mutableStateOf<String?>(null) }
    val incognitoEnabledMessage = stringResource(R.string.web_session_incognito_enabled)
    val incognitoDisabledMessage = stringResource(R.string.web_session_incognito_disabled)

    LaunchedEffect(profileFeedback) {
        if (profileFeedback != null) {
            delay(1_200)
            profileFeedback = null
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        WebSessionBrowserSearchScreen(
            currentUrl = "",
            searchEngine = searchEngine,
            searchHistory = searchHistory,
            draft = query,
            isEnginePanelVisible = isEnginePanelVisible,
            onDraftChange = { query = it },
            onEnginePanelVisibleChange = { isEnginePanelVisible = it },
            onBack = onBack,
            onSubmit = {
                profileState = browserCoordinator.newSessionProfileState()
                if (
                    selectedProfile == WebSessionProfile.NORMAL ||
                        profileState.incognitoAvailability.isAvailable
                ) {
                    resolveKiyoriWebSearchRequest(query, searchEngine, selectedProfile)
                        ?.let(onSubmitSearch)
                }
            },
            onSelectEngine = { engine ->
                scope.launch { historyStore.setSearchEngine(engine) }
            },
            onOpenSearchRecord = { record ->
                profileState = browserCoordinator.newSessionProfileState()
                if (
                    selectedProfile == WebSessionProfile.NORMAL ||
                        profileState.incognitoAvailability.isAvailable
                ) {
                    onSubmitSearch(
                        KiyoriWebSearchRequest(
                            query = record.query,
                            targetUrl = record.targetUrl,
                            profile = selectedProfile,
                        ),
                    )
                }
            },
            onDeleteSearchRecord = { recordId ->
                scope.launch { historyStore.deleteSearchHistory(recordId) }
            },
            onClearSearchHistory = {
                scope.launch { historyStore.clearSearchHistory() }
            },
            onCopyCurrentUrl = {},
            onOpenCurrentUrl = {},
            onUseCurrentUrl = {},
            selectedProfile = selectedProfile,
            incognitoAvailability = profileState.incognitoAvailability,
            onToggleProfile = {
                val requestedProfile = selectedProfile.opposite()
                if (browserCoordinator.setDefaultSessionProfile(requestedProfile)) {
                    selectedProfile = requestedProfile
                    profileFeedback =
                        if (requestedProfile == WebSessionProfile.INCOGNITO) {
                            incognitoEnabledMessage
                        } else {
                            incognitoDisabledMessage
                        }
                }
                profileState = browserCoordinator.newSessionProfileState()
            },
            profileFeedback = profileFeedback,
            modifier = Modifier.fillMaxHeight().widthIn(max = 920.dp).fillMaxWidth(),
        )
    }
}

internal data class KiyoriWebSearchRequest(
    val query: String,
    val targetUrl: String,
    val profile: WebSessionProfile,
)

internal fun resolveKiyoriWebSearchRequest(
    rawQuery: String,
    searchEngine: WebSessionSearchEngine,
    profile: WebSessionProfile,
): KiyoriWebSearchRequest? {
    val query = rawQuery.trim()
    if (query.isBlank()) {
        return null
    }
    return KiyoriWebSearchRequest(
        query = query,
        targetUrl = BrowserAddressResolver.resolve(query, searchEngine),
        profile = profile,
    )
}

@Composable
internal fun KiyoriBottomNavigation(
    selectedDestination: PrimaryDestination,
    alpha: Float,
    onDestinationSelected: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
                            top = WEB_SESSION_BROWSER_BOTTOM_TOP_PADDING_DP.dp,
                            end = WEB_SESSION_BROWSER_BOTTOM_HORIZONTAL_PADDING_DP.dp,
                            bottom = WEB_SESSION_BROWSER_BOTTOM_BOTTOM_PADDING_DP.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primaryDestinationVisuals.forEach { item ->
                    val label = stringResource(item.labelResId)
                    val selected = selectedDestination == item.destination
                    val interactionSource = remember(item.destination) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(WEB_SESSION_BROWSER_BOTTOM_ACTION_SIZE_DP.dp)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        role = Role.Button,
                                        onClick = { onDestinationSelected(item.destination) },
                                    )
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = label
                                        role = Role.Button
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(item.iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(item.iconSizeDp.dp),
                                tint =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
