package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema

internal val memoryFolderToolPrompt = ToolPrompt(
    name = "memory_folders",
    description = "List/create/rename/delete memory folders in the caller's memory space. Deleting a folder only removes the folder itself and moves its entries to the root; no content is lost. Rename rejects an existing target and a descendant of the folder itself.",
    parametersStructured = listOf(
        ToolParameterSchema(name = "action", type = "string", description = "list (default), create, rename, delete", required = false),
        ToolParameterSchema(name = "path", type = "string", description = "Folder path; required for mutations", required = false),
        ToolParameterSchema(name = "target_path", type = "string", description = "Required for rename; full target folder path", required = false),
        ToolParameterSchema(name = "offset", type = "integer", description = "List pagination offset, default 0; at most 100 paths per call", required = false),
    ),
)

internal val memoryMoveToolPrompt = ToolPrompt(
    name = "move_memory",
    description = "Move entries using stable UUIDs; title selection rejects ambiguous titles. UUIDs take precedence over titles. Folder-only selection includes descendants. A UUID does not change when the entry moves, so it stays valid afterwards.",
    parametersStructured = listOf(
        ToolParameterSchema(name = "uuids", type = "string", description = "Comma-separated UUIDs; preferred exact selection", required = false),
        ToolParameterSchema(name = "titles", type = "string", description = "Comma-separated exact titles, only if unique", required = false),
        ToolParameterSchema(name = "source_folder_path", type = "string", description = "Select folder contents; combined with titles intersects them", required = false),
        ToolParameterSchema(name = "target_folder_path", type = "string", description = "Destination folder; empty string means root", required = true),
    ),
)
