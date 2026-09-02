package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** MT 文件管理器的单一建项弹窗；文件与文件夹由底部动作明确区分。 */
@Composable
fun FileManagerNewEntryDialog(
    showDialog: Boolean,
    entryName: String,
    onEntryNameChange: (String) -> Unit,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!showDialog) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(310.dp).height(151.dp),
            shape = RoundedCornerShape(3.dp),
            color = Color.White,
            contentColor = Color.Black,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 21.dp, end = 24.dp),
            ) {
                Text(
                    text = "新建",
                    style = TextStyle(fontSize = 20.sp, lineHeight = 24.sp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = entryName,
                    onValueChange = onEntryNameChange,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, color = Color.Black),
                    cursorBrush = SolidColor(Color(0xFF42A5F5)),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            innerTextField()
                        }
                    },
                )
                Spacer(modifier = Modifier.height(2.dp).fillMaxWidth().background(Color(0xFF42A5F5)))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // MT 的三个动作不是等分排布；按同尺寸截图的文字投影校准，并让触摸槽随文字一起移动。
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp).offset(x = (-12.5).dp),
                        contentPadding = PaddingValues(start = 7.dp, end = 0.dp),
                    ) {
                        Text("取消", color = Color(0xFF42A5F5), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onCreateFile,
                        modifier = Modifier.height(48.dp).offset(x = 33.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("文件", color = Color(0xFF42A5F5), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    TextButton(
                        onClick = onCreateFolder,
                        modifier = Modifier.height(48.dp).offset(x = 8.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("文件夹", color = Color(0xFF42A5F5), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
