package com.ai.assistance.operit.ui.features.toolbox.screens.tooltester

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.util.OperitPaths
import com.kiyori.design.theme.KiyoriUiShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.content.ClipData

@Composable
fun ToolTesterScreen(navController: NavController) {
    val context = LocalContext.current
    val state = viewModel<ToolTesterViewModel> {
        val handler = AIToolHandler.getInstance(context.applicationContext)
        ToolTesterViewModel { test ->
            withContext(Dispatchers.IO) { handler.executeTool(AITool(test.id, test.parameters)) }
        }
    }
    val isCurrentScreen = LocalIsCurrentScreen.current
    val currentScreen by rememberUpdatedState(isCurrentScreen)
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var selectedCaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showBatchConfirm by rememberSaveable { mutableStateOf(false) }
    var testInputText by rememberSaveable { mutableStateOf("") }
    val testRoot = remember(state) { "${OperitPaths.testPathSdcard()}/${state.sessionId}" }
    val toolGroups = remember(context, testRoot) { getFinalToolTestGroups(context, testRoot) }
    val batchTests = remember(toolGroups) { toolGroups.filterNot { it.isManual }.flatMap { it.tests } }
    val selectedTest = toolGroups.flatMap { it.tests }.find { it.caseId == selectedCaseId }

    LaunchedEffect(isCurrentScreen) { if (!isCurrentScreen) state.requestStop() }
    DisposableEffect(state) { onDispose { state.requestStop() } }

    CustomScaffold { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ai_tools_availability_test), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.tool_tester_intro), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = testInputText, onValueChange = { testInputText = it },
                        label = { Text(stringResource(R.string.test_input_field)) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("tool_tester_input"),
                    )
                    if (state.isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(if (state.stopRequested) R.string.tool_tester_stopping else R.string.batch_testing_in_progress))
                        OutlinedButton(onClick = state::requestStop, enabled = !state.stopRequested,
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_tester_stop_next)) }
                    } else {
                        Button(onClick = { showBatchConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.start_comprehensive_test))
                        }
                    }
                }
            }
            toolGroups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        if (group.isManual) Text(stringResource(R.string.tool_tester_manual),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(group.tests, key = { it.caseId }) { test ->
                    ToolTestGridItem(test, state.results[test.caseId]) { selectedCaseId = it.caseId }
                }
            }
        }
    }

    if (isCurrentScreen && showBatchConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchConfirm = false },
            title = { Text(stringResource(R.string.start_comprehensive_test)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tool_tester_batch_scope, batchTests.size, testRoot))
                    batchTests.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(enabled = !state.isRunning, onClick = {
                showBatchConfirm = false
                state.run(batchTests, prepare = { currentScreen })
            }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { showBatchConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (isCurrentScreen && selectedTest != null) {
        Dialog(onDismissRequest = { selectedCaseId = null }) {
            Surface(shape = KiyoriUiShapes.dialog) {
                ToolDetailsSheet(selectedTest, state.results[selectedTest.caseId], !state.isRunning,
                    onTest = { test ->
                        if (test.id == "set_input_text") selectedCaseId = null
                        state.run(listOf(test)) {
                            if (test.id == "set_input_text") {
                                // 关闭详情后才请求测试输入框焦点；隐藏页面不能继续操纵前台输入。
                                delay(300)
                                if (currentScreen) {
                                    gridState.scrollToItem(0)
                                    withFrameNanos { }
                                    focusRequester.requestFocus()
                                    delay(100)
                                }
                            }
                            currentScreen
                        }
                    }, onDismiss = { selectedCaseId = null })
            }
        }
    }
}

@Composable
private fun statusLabel(status: TestStatus?): String = stringResource(when (status) {
    TestStatus.SUCCESS -> R.string.tool_tester_success
    TestStatus.FAILED -> R.string.tool_tester_failed
    TestStatus.RUNNING -> R.string.tool_tester_running
    TestStatus.QUEUED -> R.string.tool_tester_queued
    TestStatus.SKIPPED -> R.string.tool_tester_skipped
    TestStatus.INTERRUPTED -> R.string.tool_tester_interrupted
    null -> R.string.status_not_running
})

@Composable
fun ToolTestGridItem(toolTest: ToolTest, testResult: ToolTestResult?, onClick: (ToolTest) -> Unit) {
    val status = testResult?.status
    val container = when (status) {
        TestStatus.FAILED, TestStatus.INTERRUPTED -> MaterialTheme.colorScheme.errorContainer
        TestStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        TestStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(onClick = { onClick(toolTest) }, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
        shape = KiyoriUiShapes.card, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(toolTest.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Text(statusLabel(status), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ToolDetailsSheet(toolTest: ToolTest, testResult: ToolTestResult?, canRun: Boolean,
    onTest: (ToolTest) -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(toolTest.name, style = MaterialTheme.typography.titleLarge)
        Text(statusLabel(testResult?.status), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(toolTest.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(toolTest.description, style = MaterialTheme.typography.bodyMedium)
            toolTest.parameters.forEach { ToolTestTextPanel(it.name, it.value) }
            testResult?.result?.let {
                ToolTestTextPanel(stringResource(R.string.detailed_result),
                    if (it.success) it.result.toString() else it.error ?: stringResource(R.string.unknown_error))
            }
            if (testResult?.status == TestStatus.INTERRUPTED) Text(stringResource(R.string.tool_tester_interrupted_detail))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            Button(onClick = { onTest(toolTest) }, enabled = canRun) { Text(stringResource(R.string.retest)) }
        }
    }
}

@Composable
private fun ToolTestTextPanel(title: String, text: String) {
    var showFull by remember(text) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, KiyoriUiShapes.control)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(text.take(500), style = MaterialTheme.typography.bodyMedium) }
        if (text.length > 500) TextButton(onClick = { showFull = true }) {
            Text(stringResource(R.string.tool_tester_full_text, text.length))
        }
        TextButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, text))) } }) {
            Text(stringResource(R.string.copy))
        }
    }
    if (showFull) Dialog(onDismissRequest = { showFull = false }) {
        Surface(shape = KiyoriUiShapes.dialog) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                // 大写入用例包含数十万字符；分块展示避免一个 Text 测量出超高布局。
                val chunks = remember(text) { text.chunked(4000) }
                SelectionContainer(Modifier.weight(1f, fill = false)) {
                    LazyColumn { items(chunks) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                }
                TextButton(onClick = { showFull = false }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

internal fun getFinalToolTestGroups(context: android.content.Context, testRoot: String): List<ToolGroup> {
    val testBaseDir = "$testRoot/files"
    val testFile = "$testBaseDir/test_file.txt"
    val testLargeWriteFile = "$testBaseDir/write_large.txt"
    val testFileCopy = "$testBaseDir/test_file_copy.txt"
    val testZip = "$testRoot/test.zip"
    val testUnzipDir = "$testRoot/unzipped"
    val testImage = "$testBaseDir/test_image.png"
    val largeWriteContent = "Large write test content.".repeat(12000)

    return listOf(
        ToolGroup(context.getString(R.string.env_setup_group), true, false, listOf(
            ToolTest("make_directory", context.getString(R.string.create_test_dir), context.getString(R.string.create_test_dir_desc), listOf(ToolParameter("path", testBaseDir), ToolParameter("create_parents", "true"))),
            ToolTest("download_file", context.getString(R.string.download_test_image), context.getString(R.string.download_test_image_desc), listOf(ToolParameter("url", "https://picsum.photos/100"), ToolParameter("destination", testImage))),
            ToolTest("write_file", context.getString(R.string.create_text_file), context.getString(R.string.create_text_file_desc), listOf(ToolParameter("path", testFile), ToolParameter("content", "This is a test file for Kiyori tool testing.")))
        )),
        ToolGroup(context.getString(R.string.basic_http_group), false, false, listOf(
            ToolTest("sleep", context.getString(R.string.delay_test), context.getString(R.string.delay_test_desc), listOf(ToolParameter("duration_ms", "1000"))),
            ToolTest("device_info", context.getString(R.string.device_info_test), context.getString(R.string.device_info_test_desc), emptyList()),
            ToolTest("http_request", context.getString(R.string.http_get_test), context.getString(R.string.http_get_test_desc), listOf(ToolParameter("url", "https://httpbin.org/get"), ToolParameter("method", "GET"))),
            ToolTest(
                "multipart_request",
                context.getString(R.string.file_upload_test),
                context.getString(R.string.file_upload_test_desc),
                listOf(
                    ToolParameter("url", "https://httpbin.org/post"),
                    ToolParameter("method", "POST"),
                    ToolParameter(
                        "files",
                        """[{"field_name":"file","file_path":"$testFile"}]"""
                    )
                )
            ),
            ToolTest("manage_cookies", context.getString(R.string.manage_cookies_test), context.getString(R.string.manage_cookies_test_desc), listOf(ToolParameter("action", "get"), ToolParameter("domain", "google.com"))),
            ToolTest("visit_web", context.getString(R.string.visit_web_test), context.getString(R.string.visit_web_test_desc), listOf(ToolParameter("url", "https://www.baidu.com"))),
            ToolTest("use_package", context.getString(R.string.use_package_test), context.getString(R.string.use_package_test_desc), listOf(ToolParameter("package_name", "non_existent_package"))),
            ToolTest("query_memory", context.getString(R.string.query_memory_test), context.getString(R.string.query_memory_test_desc), listOf(ToolParameter("query", "test")))
        )),
        ToolGroup(context.getString(R.string.file_readonly_group), false, false, listOf(
            ToolTest("list_files", context.getString(R.string.list_files_test), context.getString(R.string.list_files_test_desc), listOf(ToolParameter("path", testBaseDir))),
            ToolTest("file_exists", context.getString(R.string.file_exists_test), context.getString(R.string.file_exists_test_desc), listOf(ToolParameter("path", testFile))),
            ToolTest("read_file", context.getString(R.string.ocr_read_test), context.getString(R.string.ocr_read_test_desc), listOf(ToolParameter("path", testImage))),
            ToolTest("read_file_part", context.getString(R.string.chunk_read_test), context.getString(R.string.chunk_read_test_desc), listOf(ToolParameter("path", testFile), ToolParameter("partIndex", "0"))),
            ToolTest("file_info", context.getString(R.string.file_info_test), context.getString(R.string.file_info_test_desc), listOf(ToolParameter("path", testFile))),
            ToolTest("find_files", context.getString(R.string.find_files_test), context.getString(R.string.find_files_test_desc), listOf(ToolParameter("path", testBaseDir), ToolParameter("pattern", "*.txt")))
        )),
        ToolGroup(context.getString(R.string.file_write_group), true, false, listOf(
            ToolTest(
                "write_file",
                context.getString(R.string.write_large_file_test),
                context.getString(R.string.write_large_file_test_desc),
                listOf(
                    ToolParameter("path", testLargeWriteFile),
                    ToolParameter("content", largeWriteContent)
                ),
                caseId = "write_large_file"
            ),
            ToolTest("copy_file", context.getString(R.string.copy_file_test), context.getString(R.string.copy_file_test_desc), listOf(ToolParameter("source", testFile), ToolParameter("destination", testFileCopy))),
            ToolTest("move_file", context.getString(R.string.move_file_test), context.getString(R.string.move_file_test_desc), listOf(ToolParameter("source", testFileCopy), ToolParameter("destination", "$testBaseDir/moved_file.txt"))),
            ToolTest("zip_files", context.getString(R.string.zip_files_test), context.getString(R.string.zip_files_test_desc), listOf(ToolParameter("source", testBaseDir), ToolParameter("destination", testZip))),
            ToolTest("unzip_files", context.getString(R.string.unzip_files_test), context.getString(R.string.unzip_files_test_desc), listOf(ToolParameter("source", testZip), ToolParameter("destination", testUnzipDir))),
        )),
        ToolGroup(context.getString(R.string.system_group), true, true, listOf(
            ToolTest("list_installed_apps", context.getString(R.string.list_apps_test), context.getString(R.string.list_apps_test_desc), listOf(ToolParameter("include_system_apps", "false"))),
            ToolTest("get_notifications", context.getString(R.string.get_notifications_test), context.getString(R.string.get_notifications_test_desc), listOf(ToolParameter("limit", "5"))),
            ToolTest("get_device_location", context.getString(R.string.device_location_test), context.getString(R.string.device_location_test_desc), listOf(ToolParameter("high_accuracy", "false"))),
            ToolTest("get_system_setting", context.getString(R.string.read_system_setting_test), context.getString(R.string.read_system_setting_test_desc), listOf(ToolParameter("setting", "screen_off_timeout"))),
            ToolTest("modify_system_setting", context.getString(R.string.write_system_setting_test), context.getString(R.string.write_system_setting_test_desc), listOf(ToolParameter("setting", "test_setting"), ToolParameter("value", "1"), ToolParameter("namespace", "system")))
        )),
        ToolGroup(context.getString(R.string.ui_automation_group), true, true, listOf(
            ToolTest("get_page_info", context.getString(R.string.page_info_test), context.getString(R.string.page_info_test_desc), emptyList()),
            ToolTest("press_key", context.getString(R.string.simulate_key_test), context.getString(R.string.simulate_key_test_desc), listOf(ToolParameter("key_code", "KEYCODE_VOLUME_UP"))),
            ToolTest("set_input_text", context.getString(R.string.text_input_test), context.getString(R.string.text_input_test_desc), listOf(ToolParameter("text", "Hello from Kiyori!"))),
            ToolTest("tap", context.getString(R.string.simulate_tap_test), context.getString(R.string.simulate_tap_test_desc), listOf(ToolParameter("x", "1"), ToolParameter("y", "1"))),
            ToolTest("swipe", context.getString(R.string.simulate_swipe_test), context.getString(R.string.simulate_swipe_test_desc), listOf(ToolParameter("start_x", "500"), ToolParameter("start_y", "1000"), ToolParameter("end_x", "500"), ToolParameter("end_y", "1200")))
        )),
        ToolGroup(context.getString(R.string.cleanup_group), true, false, listOf(
            ToolTest("delete_file", context.getString(R.string.cleanup_test_dir), context.getString(R.string.cleanup_test_dir_desc), listOf(ToolParameter("path", testRoot), ToolParameter("recursive", "true")))
        ))
    )
}


data class ToolTest(val id: String, val name: String, val description: String, val parameters: List<ToolParameter>, val caseId: String = id)
data class ToolTestResult(val status: TestStatus, val result: ToolResult?)
enum class TestStatus { SUCCESS, FAILED, RUNNING, QUEUED, SKIPPED, INTERRUPTED }
data class ToolGroup(val name: String, val sequential: Boolean, val isManual: Boolean, val tests: List<ToolTest>)
