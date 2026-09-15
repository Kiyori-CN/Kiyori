package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema

internal val memoryFolderToolPrompt = ToolPrompt(
    name = "memory_folders",
    description = "List/create/rename/delete memory folders in the caller's memory space. The listing returns real folder paths only, parents included; the root (unfiled entries) is not a folder and is addressed with an empty path, or with (root) where a filter needs to name it. Deleting a folder only removes the folder itself and moves its entries to the root; no content is lost. Create rejects an existing path, and rename rejects an existing target and a descendant of the folder itself.",
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
        ToolParameterSchema(name = "source_folder_path", type = "string", description = "Select folder contents including descendants; combined with titles intersects them. Empty string selects unfiled entries", required = false),
        ToolParameterSchema(name = "target_folder_path", type = "string", description = "Destination folder; empty string means root. Titles stay unique per folder", required = true),
    ),
)

/**
 * 日记只追加，不改写。历史记录是判断当时依据的证据，模型不能通过覆盖来抹掉走过的弯路；
 * 需要更正时再追加一条说明，或显式改写全文。
 */
internal val memoryDiaryToolPrompt = ToolPrompt(
    name = "append_diary",
    description = "Append one timestamped entry to an existing diary (library_kind=diary). Existing entries are never rewritten. Create the diary first with create_memory(library_kind=diary); its content becomes the opening entry. A diary covers one work segment, so several diaries per day are normal. phase=closed marks the diary completed; appending again afterwards marks it in progress once more.",
    parametersStructured = listOf(
        ToolParameterSchema(name = "uuid", type = "string", description = "Stable diary UUID; preferred over title to disambiguate", required = false),
        ToolParameterSchema(name = "title", type = "string", description = "Exact diary title, required unless uuid is supplied", required = false),
        ToolParameterSchema(name = "content", type = "string", description = "Entry text, up to 20000 characters. Required unless phase=closed", required = false),
        ToolParameterSchema(name = "phase", type = "string", description = "note, plan, progress, decision, evidence, risk, validation, closed", required = false, default = "\"note\""),
    ),
)
