package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

private const val PACKAGE_OTHER_CATEGORY = "Other"
private const val PACKAGE_NAME_DIGIT_SORT_INDEX = 26
private const val PACKAGE_NAME_OTHER_SORT_INDEX = 27
private const val PACKAGE_PINYIN_INITIALS = "ABCDEFGHJKLMNOPQRSTWXYZ"

private val PACKAGE_PINYIN_BOUNDARIES =
    listOf(
        "啊",
        "芭",
        "擦",
        "搭",
        "蛾",
        "发",
        "噶",
        "哈",
        "击",
        "喀",
        "垃",
        "妈",
        "拿",
        "哦",
        "啪",
        "期",
        "然",
        "撒",
        "塌",
        "挖",
        "昔",
        "压",
        "匝",
        "座",
    )

internal enum class PackageCategoryIcon {
    MENU_BOOK,
    ACCOUNT_TREE,
    APPS,
    AUTO_MODE,
    CODE,
    EXTENSION,
    FAVORITE,
    FOLDER,
    FORUM,
    LANGUAGE,
    MEMORY,
    PALETTE,
    PLAY_CIRCLE,
    SCIENCE,
    SEARCH,
    SETTINGS,
    SMART_TOY,
    TUNE,
    WIDGETS,
}

@Immutable
internal data class PackageCategoryPalette(
    val lightIcon: Color,
    val lightContainer: Color,
    val darkIcon: Color,
    val darkContainer: Color,
)

@Immutable
internal data class PackageCategoryResolvedColors(
    val icon: Color,
    val container: Color,
)

@Immutable
internal data class PackageCategoryVisual(
    val categoryLabel: String,
    val icon: PackageCategoryIcon,
    val palette: PackageCategoryPalette,
)

private data class PackageCategoryPreset(
    val icon: PackageCategoryIcon,
    val palette: PackageCategoryPalette,
)

internal val PACKAGE_CATEGORY_PRESET_LABELS =
    listOf(
        "Academic",
        "Automatic",
        "Chat",
        "Development",
        "Draw",
        "Experimental",
        "File",
        "Life",
        "Media",
        "Memory",
        "Network",
        "Other",
        "Search",
        "System",
        "ToolPkg",
        "UI_AUTOMATION",
        "Utility",
        "Workflow",
    )

private val PACKAGE_CATEGORY_PRESET_LABELS_BY_KEY =
    PACKAGE_CATEGORY_PRESET_LABELS.associateBy { label -> label.lowercase(Locale.ROOT) }

private val PACKAGE_CATEGORY_PRESETS =
    mapOf(
        "academic" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.MENU_BOOK,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF00695C),
                        lightContainer = Color(0xFFD9F3EE),
                        darkIcon = Color(0xFF78D9C8),
                        darkContainer = Color(0xFF173A35),
                    ),
            ),
        "automatic" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.AUTO_MODE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFFA95D00),
                        lightContainer = Color(0xFFFFF0D7),
                        darkIcon = Color(0xFFFFB86A),
                        darkContainer = Color(0xFF3E2A13),
                    ),
            ),
        "chat" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.FORUM,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF207F50),
                        lightContainer = Color(0xFFE4F3E9),
                        darkIcon = Color(0xFF7AD29C),
                        darkContainer = Color(0xFF19372A),
                    ),
            ),
        "development" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.CODE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF4458B8),
                        lightContainer = Color(0xFFE9EDFC),
                        darkIcon = Color(0xFFAEB9FF),
                        darkContainer = Color(0xFF252C49),
                    ),
            ),
        "draw" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.PALETTE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF6D4BC8),
                        lightContainer = Color(0xFFEFEAFF),
                        darkIcon = Color(0xFFBCA9FF),
                        darkContainer = Color(0xFF2F254B),
                    ),
            ),
        "experimental" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.SCIENCE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFFB43A78),
                        lightContainer = Color(0xFFFBE7F1),
                        darkIcon = Color(0xFFF49BC4),
                        darkContainer = Color(0xFF432337),
                    ),
            ),
        "file" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.FOLDER,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF956500),
                        lightContainer = Color(0xFFFFF3D4),
                        darkIcon = Color(0xFFF0C669),
                        darkContainer = Color(0xFF3A3015),
                    ),
            ),
        "life" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.FAVORITE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFFB74764),
                        lightContainer = Color(0xFFFCE8ED),
                        darkIcon = Color(0xFFF2A1B0),
                        darkContainer = Color(0xFF43252D),
                    ),
            ),
        "media" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.PLAY_CIRCLE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFFC44839),
                        lightContainer = Color(0xFFFBE9E6),
                        darkIcon = Color(0xFFFF9D91),
                        darkContainer = Color(0xFF452721),
                    ),
            ),
        "memory" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.MEMORY,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF7A4A99),
                        lightContainer = Color(0xFFF2EAF6),
                        darkIcon = Color(0xFFD1A7E8),
                        darkContainer = Color(0xFF34263E),
                    ),
            ),
        "network" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.LANGUAGE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF087D96),
                        lightContainer = Color(0xFFE2F3F6),
                        darkIcon = Color(0xFF69CFE0),
                        darkContainer = Color(0xFF18383F),
                    ),
            ),
        "other" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.WIDGETS,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF52677A),
                        lightContainer = Color(0xFFEAF0F4),
                        darkIcon = Color(0xFFB3C5D4),
                        darkContainer = Color(0xFF27343E),
                    ),
            ),
        "search" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.SEARCH,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF176FC0),
                        lightContainer = Color(0xFFE6F1FB),
                        darkIcon = Color(0xFF8FC8FF),
                        darkContainer = Color(0xFF163147),
                    ),
            ),
        "system" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.SETTINGS,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFFA4374A),
                        lightContainer = Color(0xFFF8E8EC),
                        darkIcon = Color(0xFFED92A2),
                        darkContainer = Color(0xFF40252B),
                    ),
            ),
        "toolpkg" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.EXTENSION,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF87513A),
                        lightContainer = Color(0xFFF7EBE6),
                        darkIcon = Color(0xFFE3AD97),
                        darkContainer = Color(0xFF3A2922),
                    ),
            ),
        "ui_automation" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.SMART_TOY,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF5263C2),
                        lightContainer = Color(0xFFEAEDFC),
                        darkIcon = Color(0xFFAEB8FF),
                        darkContainer = Color(0xFF262D4A),
                    ),
            ),
        "utility" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.TUNE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF637C16),
                        lightContainer = Color(0xFFF0F5DD),
                        darkIcon = Color(0xFFB9D86C),
                        darkContainer = Color(0xFF2D3818),
                    ),
            ),
        "workflow" to
            PackageCategoryPreset(
                icon = PackageCategoryIcon.ACCOUNT_TREE,
                palette =
                    PackageCategoryPalette(
                        lightIcon = Color(0xFF00786E),
                        lightContainer = Color(0xFFE1F3F0),
                        darkIcon = Color(0xFF6BD0C5),
                        darkContainer = Color(0xFF173935),
                    ),
            ),
    )

internal fun normalizePackageCategoryLabel(category: String): String {
    val trimmedCategory = category.trim().ifBlank { PACKAGE_OTHER_CATEGORY }
    return PACKAGE_CATEGORY_PRESET_LABELS_BY_KEY[
        trimmedCategory.lowercase(Locale.ROOT)
    ] ?: trimmedCategory
}

internal fun packageCategoryKey(categoryLabel: String): String =
    categoryLabel.lowercase(Locale.ROOT)

internal fun resolvePackageCategoryVisual(categoryLabel: String): PackageCategoryVisual {
    val normalizedLabel = normalizePackageCategoryLabel(categoryLabel)
    val categoryKey = packageCategoryKey(normalizedLabel)
    val preset = PACKAGE_CATEGORY_PRESETS[categoryKey]
    if (preset != null) {
        return PackageCategoryVisual(
            categoryLabel = normalizedLabel,
            icon = preset.icon,
            palette = preset.palette,
        )
    }

    return PackageCategoryVisual(
        categoryLabel = normalizedLabel,
        icon = PackageCategoryIcon.APPS,
        palette = generatedPackageCategoryPalette(categoryKey),
    )
}

internal fun PackageCategoryPalette.resolveColors(
    darkTheme: Boolean,
): PackageCategoryResolvedColors =
    if (darkTheme) {
        PackageCategoryResolvedColors(
            icon = darkIcon,
            container = darkContainer,
        )
    } else {
        PackageCategoryResolvedColors(
            icon = lightIcon,
            container = lightContainer,
        )
    }

@Composable
internal fun PackageCategoryVisual.resolveColors(): PackageCategoryResolvedColors =
    palette.resolveColors(
        darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    )

@Composable
internal fun PackageCategoryIconBadge(
    visual: PackageCategoryVisual,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    containerSize: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    shape: Shape = com.kiyori.design.theme.KiyoriUiShapes.control,
) {
    val colors = visual.resolveColors()
    Surface(
        modifier = modifier.size(containerSize),
        shape = shape,
        color = colors.container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = visual.icon.toImageVector(),
                contentDescription = contentDescription,
                tint = colors.icon,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

internal fun packageCategoryComparator(): Comparator<String> =
    Comparator { left, right ->
        val normalizedLeft = normalizePackageCategoryLabel(left)
        val normalizedRight = normalizePackageCategoryLabel(right)
        val insensitiveComparison =
            String.CASE_INSENSITIVE_ORDER.compare(normalizedLeft, normalizedRight)
        if (insensitiveComparison != 0) {
            insensitiveComparison
        } else {
            normalizedLeft.compareTo(normalizedRight)
        }
    }

internal fun packageNameInitialLetter(name: String): Char? {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) {
        return null
    }

    val firstCodePoint = trimmedName.codePointAt(0)
    latinInitial(firstCodePoint)?.let { initial -> return initial }
    if (!isHanCodePoint(firstCodePoint)) {
        return null
    }

    return resolveHanPinyinInitial(
        firstCharacter = String(Character.toChars(firstCodePoint)),
        collator = createSimplifiedChineseCollator(),
    )
}

internal fun <T> packageDisplayNameComparator(
    displayNameSelector: (T) -> String,
    internalNameSelector: (T) -> String,
): Comparator<T> {
    val collator = createSimplifiedChineseCollator()
    val initialSortIndexCache = mutableMapOf<String, Int>()

    return Comparator { left, right ->
        val leftDisplayName = displayNameSelector(left).trim()
        val rightDisplayName = displayNameSelector(right).trim()
        val initialComparison =
            initialSortIndexCache
                .getOrPut(leftDisplayName) {
                    packageNameInitialSortIndex(leftDisplayName, collator)
                }
                .compareTo(
                    initialSortIndexCache.getOrPut(rightDisplayName) {
                        packageNameInitialSortIndex(rightDisplayName, collator)
                    },
                )
        if (initialComparison != 0) {
            return@Comparator initialComparison
        }

        val localizedComparison = collator.compare(leftDisplayName, rightDisplayName)
        if (localizedComparison != 0) {
            return@Comparator localizedComparison
        }

        val insensitiveComparison =
            String.CASE_INSENSITIVE_ORDER.compare(leftDisplayName, rightDisplayName)
        if (insensitiveComparison != 0) {
            return@Comparator insensitiveComparison
        }

        val exactDisplayNameComparison = leftDisplayName.compareTo(rightDisplayName)
        if (exactDisplayNameComparison != 0) {
            return@Comparator exactDisplayNameComparison
        }

        val leftInternalName = internalNameSelector(left)
        val rightInternalName = internalNameSelector(right)
        val internalInsensitiveComparison =
            String.CASE_INSENSITIVE_ORDER.compare(leftInternalName, rightInternalName)
        if (internalInsensitiveComparison != 0) {
            internalInsensitiveComparison
        } else {
            leftInternalName.compareTo(rightInternalName)
        }
    }
}

internal fun <T> packageCategoryAndDisplayNameComparator(
    categorySelector: (T) -> String,
    displayNameSelector: (T) -> String,
    internalNameSelector: (T) -> String,
): Comparator<T> {
    val categoryComparator = packageCategoryComparator()
    val displayNameComparator =
        packageDisplayNameComparator(
            displayNameSelector = displayNameSelector,
            internalNameSelector = internalNameSelector,
        )

    return Comparator { left, right ->
        val categoryComparison =
            categoryComparator.compare(categorySelector(left), categorySelector(right))
        if (categoryComparison != 0) {
            categoryComparison
        } else {
            displayNameComparator.compare(left, right)
        }
    }
}

internal fun PackageCategoryIcon.toImageVector(): ImageVector =
    when (this) {
        PackageCategoryIcon.MENU_BOOK -> Icons.AutoMirrored.Filled.MenuBook
        PackageCategoryIcon.ACCOUNT_TREE -> Icons.Filled.AccountTree
        PackageCategoryIcon.APPS -> Icons.Filled.Apps
        PackageCategoryIcon.AUTO_MODE -> Icons.Filled.AutoMode
        PackageCategoryIcon.CODE -> Icons.Filled.Code
        PackageCategoryIcon.EXTENSION -> Icons.Filled.Extension
        PackageCategoryIcon.FAVORITE -> Icons.Filled.Favorite
        PackageCategoryIcon.FOLDER -> Icons.Filled.Folder
        PackageCategoryIcon.FORUM -> Icons.Filled.Forum
        PackageCategoryIcon.LANGUAGE -> Icons.Filled.Language
        PackageCategoryIcon.MEMORY -> Icons.Filled.Memory
        PackageCategoryIcon.PALETTE -> Icons.Filled.Palette
        PackageCategoryIcon.PLAY_CIRCLE -> Icons.Filled.PlayCircle
        PackageCategoryIcon.SCIENCE -> Icons.Filled.Science
        PackageCategoryIcon.SEARCH -> Icons.Filled.Search
        PackageCategoryIcon.SETTINGS -> Icons.Filled.Settings
        PackageCategoryIcon.SMART_TOY -> Icons.Filled.SmartToy
        PackageCategoryIcon.TUNE -> Icons.Filled.Tune
        PackageCategoryIcon.WIDGETS -> Icons.Filled.Widgets
    }

private fun createSimplifiedChineseCollator(): Collator =
    Collator.getInstance(Locale.SIMPLIFIED_CHINESE).apply {
        strength = Collator.PRIMARY
    }

private fun packageNameInitialSortIndex(
    name: String,
    collator: Collator,
): Int {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) {
        return PACKAGE_NAME_OTHER_SORT_INDEX
    }

    val firstCodePoint = trimmedName.codePointAt(0)
    latinInitial(firstCodePoint)?.let { initial ->
        return initial - 'A'
    }
    if (Character.isDigit(firstCodePoint)) {
        return PACKAGE_NAME_DIGIT_SORT_INDEX
    }
    if (isHanCodePoint(firstCodePoint)) {
        val initial =
            resolveHanPinyinInitial(
                firstCharacter = String(Character.toChars(firstCodePoint)),
                collator = collator,
            )
        if (initial != null) {
            return initial - 'A'
        }
    }

    return PACKAGE_NAME_OTHER_SORT_INDEX
}

private fun latinInitial(codePoint: Int): Char? {
    val normalized =
        Normalizer.normalize(
            String(Character.toChars(codePoint)),
            Normalizer.Form.NFD,
        )
    val firstBaseCharacter =
        normalized.firstOrNull { character ->
            Character.getType(character) != Character.NON_SPACING_MARK.toInt()
        } ?: return null
    val uppercaseCharacter = firstBaseCharacter.uppercaseChar()
    return uppercaseCharacter.takeIf { character -> character in 'A'..'Z' }
}

private fun isHanCodePoint(codePoint: Int): Boolean =
    codePoint in 0x3400..0x4DBF ||
        codePoint in 0x4E00..0x9FFF ||
        codePoint in 0xF900..0xFAFF ||
        codePoint in 0x20000..0x2FA1F

private fun resolveHanPinyinInitial(
    firstCharacter: String,
    collator: Collator,
): Char? {
    for (index in PACKAGE_PINYIN_INITIALS.indices) {
        val lowerBoundary = PACKAGE_PINYIN_BOUNDARIES[index]
        val upperBoundary = PACKAGE_PINYIN_BOUNDARIES[index + 1]
        if (
            collator.compare(firstCharacter, lowerBoundary) >= 0 &&
                collator.compare(firstCharacter, upperBoundary) < 0
        ) {
            return PACKAGE_PINYIN_INITIALS[index]
        }
    }

    return null
}

private fun generatedPackageCategoryPalette(categoryKey: String): PackageCategoryPalette {
    val hash = categoryKey.hashCode()
    val hue = Math.floorMod(hash, 360).toFloat()
    val saturationAdjustment = Math.floorMod(hash ushr 8, 13) / 100f
    return PackageCategoryPalette(
        lightIcon = hslColor(hue, 0.56f + saturationAdjustment, 0.38f),
        lightContainer = hslColor(hue, 0.28f, 0.93f),
        darkIcon = hslColor(hue, 0.52f, 0.74f),
        darkContainer = hslColor(hue, 0.36f, 0.22f),
    )
}

private fun hslColor(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Color {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val hueSection = normalizedHue / 60f
    val secondary = chroma * (1f - abs(hueSection % 2f - 1f))
    val (redBase, greenBase, blueBase) =
        when {
            hueSection < 1f -> Triple(chroma, secondary, 0f)
            hueSection < 2f -> Triple(secondary, chroma, 0f)
            hueSection < 3f -> Triple(0f, chroma, secondary)
            hueSection < 4f -> Triple(0f, secondary, chroma)
            hueSection < 5f -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
    val match = lightness - chroma / 2f
    return Color(
        red = redBase + match,
        green = greenBase + match,
        blue = blueBase + match,
        alpha = 1f,
    )
}
