package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.defaultTool.standard.*
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FileManagerNetworkDialog(profile: NetworkStorageProfile?, groups: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var protocol by remember { mutableStateOf(profile?.protocol ?: NetworkStorageProtocol.WEBDAV) }
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var endpoint by remember { mutableStateOf(profile?.endpoint.orEmpty()) }
    var username by remember { mutableStateOf(profile?.username.orEmpty()) }
    var secret by remember { mutableStateOf("") }
    var root by remember { mutableStateOf(profile?.rootPath ?: "/") }
    var group by remember { mutableStateOf(profile?.group.orEmpty()) }
    var fingerprint by remember { mutableStateOf(profile?.hostKeySha256.orEmpty()) }
    var bucket by remember { mutableStateOf(profile?.bucket.orEmpty()) }
    var region by remember { mutableStateOf(profile?.region ?: "us-east-1") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var successful by remember { mutableStateOf(false) }
    suspend fun model(): NetworkStorageProfile {
        require(name.isNotBlank()) { "请输入名称" }
        val raw = endpoint.trim()
        val scheme = when (protocol) { NetworkStorageProtocol.FTP -> "ftp"; NetworkStorageProtocol.FTPS -> "ftps"; NetworkStorageProtocol.SFTP -> "sftp"; else -> "https" }
        val address = if ("://" in raw) raw else "$scheme://$raw"
        val uri = URI(address)
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.port in -1..65535) { "服务器地址无效，请将账户填写在用户名栏" }
        require(uri.scheme in if (protocol in setOf(NetworkStorageProtocol.WEBDAV, NetworkStorageProtocol.S3)) setOf("http", "https") else setOf(scheme)) { "地址协议与选择不一致" }
        require(protocol in setOf(NetworkStorageProtocol.WEBDAV, NetworkStorageProtocol.S3) || uri.path.isNullOrEmpty() || uri.path == "/") { "请将远程目录填写在根目录栏" }
        networkRemotePath(root.trim(), "/")
        require(username.none { it == '\r' || it == '\n' } && secret.none { it == '\r' || it == '\n' }) { "账户不能包含换行" }
        if (protocol == NetworkStorageProtocol.SFTP) {
            require(username.isNotBlank() && fingerprint.trim().matches(Regex("SHA256:[A-Za-z0-9+/]{43}=?"))) { "请填写用户名及 SHA256 主机公钥指纹" }
        }
        if (protocol == NetworkStorageProtocol.S3) require(bucket.isNotBlank() && '/' !in bucket && region.isNotBlank()) { "请填写存储桶与区域" }
        val encrypted = withContext(Dispatchers.IO) { if (secret.isEmpty() && profile != null) profile.encryptedSecret else NetworkStorageSecret.encrypt(secret) }
        return NetworkStorageProfile(profile?.id ?: UUID.randomUUID().toString(), name.trim(), protocol, address, username.trim(), encrypted, root.trim(), group,
            fingerprint.trim(), bucket.trim(), region.trim())
    }
    fun execute(test: Boolean) {
        if (busy) return
        busy = true; message = null; successful = false
        scope.launch {
            try {
                val model = model()
                if (test) {
                    val count = withContext(Dispatchers.IO) { NetworkDirectoryClient.list(model, "/", NetworkStorageSecret.decrypt(model.encryptedSecret)).size }
                    successful = true; message = "连接成功，根目录 $count 项"
                } else { ApiPreferences.getInstance(context).saveFileNetwork(model); onDismiss() }
            } catch (failure: IllegalArgumentException) { message = failure.message ?: "配置无效"
            } catch (failure: Exception) { message = "连接或保存失败（${failure.javaClass.simpleName}），请检查配置。"
            } finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (profile == null) "添加网络存储" else "编辑网络存储") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NetworkStorageProtocol.entries.forEach { item -> FilterChip(selected = item == protocol, enabled = !busy, onClick = { protocol = item },
                    label = { Text(when (item) { NetworkStorageProtocol.WEBDAV -> "WebDAV"; NetworkStorageProtocol.S3 -> "对象存储"; else -> item.name }) }) }
            }
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, enabled = !busy)
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("服务器地址与端口") }, placeholder = { Text("example.com:443") }, singleLine = true, enabled = !busy)
            OutlinedTextField(username, { username = it }, label = { Text(if (protocol == NetworkStorageProtocol.S3) "Access Key ID" else "用户名") }, singleLine = true, enabled = !busy)
            OutlinedTextField(secret, { secret = it }, label = { Text(if (profile != null) "密码 / Secret Key（留空保留）" else "密码 / Secret Key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
            OutlinedTextField(root, { root = it }, label = { Text("根目录 / 对象前缀") }, singleLine = true, enabled = !busy)
            if (protocol == NetworkStorageProtocol.SFTP) OutlinedTextField(fingerprint, { fingerprint = it }, label = { Text("SHA256 主机公钥指纹") }, enabled = !busy)
            if (protocol == NetworkStorageProtocol.S3) {
                OutlinedTextField(bucket, { bucket = it }, label = { Text("存储桶") }, singleLine = true, enabled = !busy)
                OutlinedTextField(region, { region = it }, label = { Text("区域") }, singleLine = true, enabled = !busy)
            }
            if (groups.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("") + groups).forEach { item -> FilterChip(item == group, { group = item }, enabled = !busy, label = { Text(item.ifEmpty { "未分组" }) }) }
            }
            Text("当前支持连接与目录浏览。FTPS 使用显式 TLS；对象存储使用 S3 路径式访问。远程文件打开与写入暂不可用。", style = MaterialTheme.typography.bodySmall)
            if (protocol == NetworkStorageProtocol.FTP) Text("FTP 以明文传输账户，建议使用 FTPS 或 SFTP。", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = if (successful) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = { execute(true) }) { Text("测试连接") }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = { execute(false) }) { Text("保存") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
}
