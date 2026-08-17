package com.kiyori.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.browser.navigation.BrowserAddressResolver
import com.ai.assistance.operit.core.browser.presentation.BrowserPresentationCoordinator
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionHistoryStore
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionProfile
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.WebSessionSearchEngine
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.opposite
import com.ai.assistance.operit.ui.features.websession.browser.WebSessionBrowserSearchScreen
import com.kiyori.capability.browser.presentation.KiyoriBrowserSearchSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            currentTitle = "",
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
                    resolveKiyoriWebSearchRequest(
                        rawQuery = query,
                        searchEngine = searchEngine,
                        profile = selectedProfile,
                        source = KiyoriBrowserSearchSource.SOFTWARE_HOME,
                    )
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
                            engineId = record.engineId,
                            source = KiyoriBrowserSearchSource.SEARCH_HISTORY,
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
            onEditCurrentUrl = {},
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
    val engineId: String,
    val source: KiyoriBrowserSearchSource,
)

internal fun resolveKiyoriWebSearchRequest(
    rawQuery: String,
    searchEngine: WebSessionSearchEngine,
    profile: WebSessionProfile,
    source: KiyoriBrowserSearchSource,
): KiyoriWebSearchRequest? {
    val query = rawQuery.trim()
    if (query.isBlank()) {
        return null
    }
    return KiyoriWebSearchRequest(
        query = query,
        targetUrl = BrowserAddressResolver.resolve(query, searchEngine),
        profile = profile,
        engineId = searchEngine.id,
        source = source,
    )
}
