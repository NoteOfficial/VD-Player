package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.LocalizationEngine
import com.example.data.TutorialsRepository
import com.example.model.AppSettingsState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftChip
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioPrimaryButton
import com.example.ui.components.StudioSearchBar
import com.example.ui.components.TutorialStepCard
import com.example.ui.theme.StudioTheme
import kotlinx.coroutines.launch

@Composable
fun TutorialsScreen(
    settings: AppSettingsState,
    onToggleBookmark: (String) -> Unit,
    onToggleStep: (String) -> Unit,
    onNavigateToRoute: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val str = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    BackHandler { onBack() }

    var query by remember { mutableStateOf("") }
    var selectedSeriesKey by remember { mutableStateOf<String?>(null) }
    var bookmarksOnly by remember { mutableStateOf(false) }

    val filtered = remember(query, selectedSeriesKey, bookmarksOnly, settings.bookmarkedTutorials) {
        TutorialsRepository.lessons.filter { lesson ->
            val matchCat = selectedSeriesKey == null || lesson.seriesKey == selectedSeriesKey
            val matchBm = !bookmarksOnly || lesson.id in settings.bookmarkedTutorials
            val matchQ = query.isBlank() ||
                lesson.title.contains(query, ignoreCase = true) ||
                lesson.summary.contains(query, ignoreCase = true) ||
                lesson.category.contains(query, ignoreCase = true)
            matchCat && matchBm && matchQ
        }
    }

    val totalSteps = remember { TutorialsRepository.lessons.sumOf { it.steps.size } }
    val doneSteps = settings.completedTutorialSteps.size.coerceAtMost(totalSteps)
    val progressFraction = if (totalSteps == 0) 0f else doneSteps.toFloat() / totalSteps.toFloat()

    val nextIncompleteLesson = remember(settings.completedTutorialSteps) {
        TutorialsRepository.lessons.firstOrNull { lesson ->
            lesson.steps.indices.any { idx -> "${lesson.id}:$idx" !in settings.completedTutorialSteps }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("tutorials_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = str.backLabel,
                    onClick = onBack
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(str.tutorialsHeader, style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text(
                        "${LocalizationEngine.formatNumber(settings.languageCode, doneSteps.toLong())}/" +
                            "${LocalizationEngine.formatNumber(settings.languageCode, totalSteps.toLong())} ${str.tutorialsCheckpoints}",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.cyan
                    )
                }
            }
        }

        // Progress + Resume Next Lesson + Honest Translation Scope Notice
        item {
            GlassCard(glowAccent = p.primary, contentPadding = PaddingValues(14.dp)) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = p.primary,
                    trackColor = p.elevated
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = str.tutorialsScopeNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
                nextIncompleteLesson?.let { next ->
                    Spacer(Modifier.height(8.dp))
                    StudioPrimaryButton(
                        text = "${str.tutorialsResumeNext}: ${next.id.uppercase()}",
                        icon = Icons.Rounded.PlayArrow,
                        onClick = {
                            query = ""
                            bookmarksOnly = false
                            selectedSeriesKey = next.seriesKey
                            scope.launch {
                                listState.animateScrollToItem(4)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            StudioSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = str.searchPlaceholder
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoftChip(
                    label = "${str.tutorialsSaved} (${settings.bookmarkedTutorials.size})",
                    selected = bookmarksOnly,
                    onClick = { bookmarksOnly = !bookmarksOnly }
                )
                SoftChip(
                    label = str.tutorialsSeriesAll,
                    selected = selectedSeriesKey == null,
                    onClick = { selectedSeriesKey = null }
                )
                TutorialsRepository.seriesRanges.forEach { series ->
                    val localizedSeries = LocalizationEngine.seriesTitle(series.categoryKey, settings.languageCode)
                    SoftChip(
                        label = "${series.rangeLabel} $localizedSeries",
                        selected = selectedSeriesKey == series.categoryKey,
                        onClick = { selectedSeriesKey = series.categoryKey }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateView(
                    title = "No Matching Lessons",
                    description = "Clear filters or search terms to view all 266 TK Studio guided walkthroughs."
                )
            }
        } else {
            items(filtered, key = { it.id }) { lesson ->
                val localizedSeries = LocalizationEngine.seriesTitle(lesson.seriesKey, settings.languageCode)
                TutorialStepCard(
                    lesson = lesson,
                    completedSteps = settings.completedTutorialSteps,
                    isBookmarked = lesson.id in settings.bookmarkedTutorials,
                    onToggleBookmark = { onToggleBookmark(lesson.id) },
                    onToggleStep = onToggleStep,
                    seriesLabel = "${lesson.category.substringBefore(' ')} $localizedSeries",
                    bookmarkLabel = str.tutorialsBookmark,
                    savedLabel = str.tutorialsSaved,
                    prerequisitesLabel = str.tutorialsPrerequisites,
                    expectedResultLabel = str.tutorialsExpectedResult,
                    commonErrorLabel = str.tutorialsCommonError,
                    checkpointLabel = str.tutorialsCheckpointQuestion,
                    openModuleLabel = str.tutorialsOpenModule,
                    onOpenModule = onNavigateToRoute
                )
            }
        }
    }
}
