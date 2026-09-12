/* METADATA
{
    "name": "12306_ticket",
    "display_name": {
        "zh": "12306 火车票",
        "en": "12306 Train Tickets"
    },
    "description": {
        "zh": "查询 12306 车站编码、直达或中转余票及列车经停站；相对日期查询前可先获取上海时区当前日期。",
        "en": "Query 12306 station codes, direct or transfer ticket availability, and train stops; resolve relative dates against the current Asia/Shanghai date first."
    },
    "enabledByDefault": true,
    "category": "Life",
    "tools": [
        {
            "name": "get_current_date",
            "description": {
                "zh": "获取当前日期，以上海时区（Asia/Shanghai, UTC+8）为准，返回格式为 'yyyy-MM-dd'。主要用于解析用户提到的相对日期（如“明天”、“下周三”），为其他需要日期的接口提供准确的日期输入。",
                "en": "Get the current date in the Shanghai timezone (Asia/Shanghai, UTC+8). Returns format 'yyyy-MM-dd'. Mainly used to resolve relative dates (e.g. \"tomorrow\", \"next Wednesday\") and provide correct date input for other APIs."
            },
            "parameters": []
        },
        {
            "name": "get_stations_code_in_city",
            "description": {
                "zh": "列出官网车站表中归属指定城市的车站，先明确具体车站再查询；此列表不保证覆盖所有运营站。",
                "en": "List stations assigned to a city in the official station catalog. Select explicit stations before querying; the catalog may not cover every operating station."
            },
            "parameters": [
                {
                    "name": "city",
                    "description": {
                        "zh": "中文城市名称，例如：'北京', '上海'",
                        "en": "Chinese city name, e.g. '北京', '上海'."
                    },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "get_station_code_of_citys",
            "description": {
                "zh": "查询与城市同名的车站编码，不代表全市范围；没有同名站时返回候选车站，由调用方明确选择。支持 | 分隔城市。",
                "en": "Look up the station sharing a city name, not an all-city code. If no exact station exists, return candidates for explicit selection. Separate cities with |."
            },
            "parameters": [
                {
                    "name": "citys",
                    "description": {
                        "zh": "要查询的城市，比如'北京'。若要查询多个城市，请用|分割，比如'北京|上海'。",
                        "en": "City to query, e.g. '北京'. For multiple cities, separate with |, e.g. '北京|上海'."
                    },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "get_station_code_by_names",
            "description": {
                "zh": "通过具体的中文车站名查询其 `station_code` 和车站名。此接口主要用于在用户提供**具体车站名**作为出发地或到达地时，为接口准备 `station_code` 参数。",
                "en": "Given a specific Chinese station name, return its `station_code` and station name. Use this when the user provides a **specific station name** for origin/destination."
            },
            "parameters": [
                {
                    "name": "station_names",
                    "description": {
                        "zh": "具体的中文车站名称，例如：'北京南', '上海虹桥'。若要查询多个站点，请用|分割，比如'北京南|上海虹桥'。",
                        "en": "Specific Chinese station names, e.g. '北京南', '上海虹桥'. For multiple stations, separate with |, e.g. '北京南|上海虹桥'."
                    },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "get_station_by_telecode",
            "description": {
                "zh": "通过车站的 `station_telecode` 查询车站的详细信息，包括名称、拼音、所属城市等。此接口主要用于在已知 `telecode` 的情况下获取更完整的车站数据，或用于特殊查询及调试目的。一般用户对话流程中较少直接触发。",
                "en": "Query station details by `station_telecode`, including name, pinyin, city, etc. Mainly for getting more complete station data when `telecode` is known, or for special queries/debugging."
            },
            "parameters": [
                {
                    "name": "station_telecode",
                    "description": {
                        "zh": "车站的 `station_telecode` (3位字母编码)",
                        "en": "Station `station_telecode` (3-letter code)."
                    },
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "get_tickets",
            "description": {
                "zh": "查询指定日期与站点的直达车次和座席余票，返回可读正文及 details.trains。价格缺失不影响余票展示；结果以官网实时状态为准。",
                "en": "Query direct trains and seat availability for a date and station pair. Return readable content plus details.trains. Missing prices do not hide availability; confirm current status on the official site."
            },
            "parameters": [
                {
                    "name": "date",
                    "description": {
                        "zh": "查询日期，格式为 'yyyy-MM-dd'。如果用户提供的是相对日期（如“明天”），请务必先调用 `get_current_date` 接口获取当前日期，并计算出目标日期。",
                        "en": "Query date in 'yyyy-MM-dd'. If the user gives a relative date (e.g. \"tomorrow\"), call `get_current_date` first and compute the target date."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "from_station",
                    "description": {
                        "zh": "出发地的 `station_code` 。必须是通过 `get_station_code_by_names` 或 `get_station_code_of_citys` 接口查询得到的编码，严禁直接使用中文地名。",
                        "en": "Origin `station_code`. Must be obtained via `get_station_code_by_names` or `get_station_code_of_citys` (do NOT pass Chinese names directly)."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "to_station",
                    "description": {
                        "zh": "到达地的 `station_code` 。必须是通过 `get_station_code_by_names` 或 `get_station_code_of_citys` 接口查询得到的编码，严禁直接使用中文地名。",
                        "en": "Destination `station_code`. Must be obtained via `get_station_code_by_names` or `get_station_code_of_citys` (do NOT pass Chinese names directly)."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "train_filter_flags",
                    "description": {
                        "zh": "空值不筛选。G 含 G/C，C 仅城际；另支持 D/Z/T/K/O(其他)/F(复兴号)/S(智能动车组)。标志之间为或；中转要求每一程匹配。",
                        "en": "Empty means no filter. G includes G/C; C selects intercity. Also D/Z/T/K/O(other)/F(Fuxing)/S(smart EMU). Flags use OR; every transfer leg must match."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "sort_flag",
                    "description": {
                        "zh": "排序方式，默认为空，即不排序。仅支持单一标识。可选标志：[startTime(出发时间从早到晚), arriveTime(抵达时间从早到晚), duration(历时从短到长)]",
                        "en": "Sort mode. Default empty (no sorting). Only one mode is supported. Options: [startTime (earliest departure), arriveTime (earliest arrival), duration (shortest duration)]."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "sort_reverse",
                    "description": {
                        "zh": "是否逆向排序结果，默认为false。仅在设置了sortFlag时生效。",
                        "en": "Reverse sort order (default: false). Only effective when sort_flag is set."
                    },
                    "type": "boolean",
                    "required": false
                },
                {
                    "name": "limited_num",
                    "description": {
                        "zh": "返回上限：0–1000 的整数，默认 0（不限制）。",
                        "en": "Result limit: integer 0–1000, default 0 (unlimited)."
                    },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "get_interline_tickets",
            "description": {
                "zh": "查询中转方案；官网要求登录时明确返回 AUTH_REQUIRED。先筛选再限制条数，排序覆盖最多 10 页，details 标明扫描是否完整。",
                "en": "Query transfer routes; return AUTH_REQUIRED when the official page requires login. Filter before limiting results; sort across at most 10 pages and report scan coverage in details."
            },
            "parameters": [
                {
                    "name": "date",
                    "description": {
                        "zh": "查询日期，格式为 'yyyy-MM-dd'。如果用户提供的是相对日期（如“明天”），请务必先调用 `get_current_date` 接口获取当前日期，并计算出目标日期。",
                        "en": "Query date in 'yyyy-MM-dd'. If the user gives a relative date, call `get_current_date` first and compute the target date."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "from_station",
                    "description": {
                        "zh": "出发地的 `station_code` 。必须是通过 `get_station_code_by_names` 或 `get_station_code_of_citys` 接口查询得到的编码，严禁直接使用中文地名。",
                        "en": "Origin `station_code`. Must be obtained via station-code lookup APIs (do NOT pass Chinese names directly)."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "to_station",
                    "description": {
                        "zh": "到达地的 `station_code` 。必须是通过 `get_station_code_by_names` 或 `get_station_code_of_citys` 接口查询得到的编码，严禁直接使用中文地名。",
                        "en": "Destination `station_code`. Must be obtained via station-code lookup APIs (do NOT pass Chinese names directly)."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "middle_station",
                    "description": {
                        "zh": "中转地的 `station_code` ，可选。必须是通过 `get_station_code_by_names` 或 `get_station_code_of_citys` 接口查询得到的编码，严禁直接使用中文地名。",
                        "en": "Optional transfer station `station_code`. Must be obtained via station-code lookup APIs (do NOT pass Chinese names directly)."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "show_wz",
                    "description": {
                        "zh": "是否显示无座车，默认不显示无座车。",
                        "en": "Whether to include no-seat (无座) tickets (default: false)."
                    },
                    "type": "boolean",
                    "required": false
                },
                {
                    "name": "train_filter_flags",
                    "description": {
                        "zh": "空值不筛选。G 含 G/C，C 仅城际；另支持 D/Z/T/K/O(其他)/F(复兴号)/S(智能动车组)。标志之间为或；中转要求每一程匹配。",
                        "en": "Empty means no filter. G includes G/C; C selects intercity. Also D/Z/T/K/O(other)/F(Fuxing)/S(smart EMU). Flags use OR; every transfer leg must match."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "sort_flag",
                    "description": {
                        "zh": "排序方式，默认为空，即不排序。仅支持单一标识。可选标志：[startTime(出发时间从早到晚), arriveTime(抵达时间从早到晚), duration(历时从短到长)]",
                        "en": "Sort mode. Default empty. Options: startTime / arriveTime / duration."
                    },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "sort_reverse",
                    "description": {
                        "zh": "是否逆向排序结果，默认为false。仅在设置了sortFlag时生效。",
                        "en": "Reverse sort order (default: false). Only effective when sort_flag is set."
                    },
                    "type": "boolean",
                    "required": false
                },
                {
                    "name": "limited_num",
                    "description": {
                        "zh": "返回上限：1–100，默认 10。按筛选后方案计数，扫描有界。",
                        "en": "Result limit: 1–100, default 10; count filtered routes within the bounded scan."
                    },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "get_train_route_stations",
            "description": {
                "zh": "使用余票结果的 train_no、站点编码和上车日期查询经停站，分别返回到达、出发和停站时间；不把始发站出发时间写成到达时间。",
                "en": "Use train_no, station codes, and boarding date from ticket results to query stops. Preserve separate arrival, departure, and stopover times."
            },
            "parameters": [
                {
                    "name": "train_no",
                    "description": {
                        "zh": "要查询的实际车次编号 `train_no`，例如 '240000G10336'，而非'G1033'。此编号通常可以从 `get_tickets` 的查询结果中获取，或者由用户直接提供。",
                        "en": "Actual train number `train_no`, e.g. '240000G10336' (not 'G1033'). Usually obtained from `get_tickets` results or provided by the user."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "from_station_telecode",
                    "description": {
                        "zh": "该列车行程的**出发站**的 `station_telecode` (3位字母编码`)。通常来自 `get_tickets` 结果中的 `telecode` 字段，或者通过 `get_station_code_by_names` 得到。",
                        "en": "`station_telecode` (3-letter code) of the **origin station**. Usually from `telecode` fields in `get_tickets`, or obtained via station code lookup."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "to_station_telecode",
                    "description": {
                        "zh": "该列车行程的到达站编码（3 位字母）。可从 get_tickets 的 details.trains 中读取，或通过 get_station_code_by_names 查询。",
                        "en": "`station_telecode` (3-letter code) of the **destination station**. Usually from `telecode` fields in `get_tickets`, or obtained via station code lookup."
                    },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "depart_date",
                    "description": {
                        "zh": "列车从 `from_station_telecode` 指定的车站出发的日期 (格式: yyyy-MM-dd)。如果用户提供的是相对日期，请务必先调用 `get_current_date` 解析。",
                        "en": "Departure date from the origin station (format: yyyy-MM-dd). If the user provides a relative date, resolve it via `get_current_date`."
                    },
                    "type": "string",
                    "required": true
                }
            ]
        }
    ]
}
*/

const TicketDataKeys: string[] = [
    'secret_Sstr', 'button_text_info', 'train_no', 'station_train_code', 'start_station_telecode',
    'end_station_telecode', 'from_station_telecode', 'to_station_telecode', 'start_time', 'arrive_time',
    'lishi', 'canWebBuy', 'yp_info', 'start_train_date', 'train_seat_feature',
    'location_code', 'from_station_no', 'to_station_no', 'is_support_card', 'controlled_train_flag',
    'gg_num', 'gr_num', 'qt_num', 'rw_num', 'rz_num',
    'tz_num', 'wz_num', 'yb_num', 'yw_num', 'yz_num',
    'ze_num', 'zy_num', 'swz_num', 'srrb_num', 'yp_ex',
    'seat_types', 'exchange_train_flag', 'houbu_train_flag', 'houbu_seat_limit', 'yp_info_new',
    '40', '41', '42', '43', '44',
    '45', 'dw_flag', '47', 'stopcheckTime', 'country_flag',
    'local_arrive_time', 'local_start_time', '52', 'bed_level_info', 'seat_discount_info',
    'sale_time', '56',
];

type StationData = { station_id: string; station_name: string; station_code: string; station_pinyin: string; station_short: string; station_index: string; code: string; city: string; r1: string; r2: string };

const ticket12306 = (function () {
    const API_BASE = 'https://kyfw.12306.cn';
    const STATION_URL = `${API_BASE}/otn/resources/js/framework/station_name.js`;
    const DIRECT_INIT = `${API_BASE}/otn/leftTicket/init`;
    const TRANSFER_INIT = `${API_BASE}/otn/lcQuery/init`;
    const MAX_TRANSFER_PAGES = 10;
    const MAX_TRANSFER_ROWS = 500;
    const QUERY_DEADLINE_MS = 90000;
    const SEATS: Record<string, { name: string; short: string }> = {
        '9': { name: '商务座', short: 'swz' }, P: { name: '特等座', short: 'tz' },
        M: { name: '一等座', short: 'zy' }, D: { name: '优选一等座', short: 'zy' },
        O: { name: '二等座', short: 'ze' }, S: { name: '二等包座', short: 'ze' },
        '6': { name: '高级软卧', short: 'gr' }, A: { name: '高级动卧', short: 'gr' },
        '4': { name: '软卧', short: 'rw' }, I: { name: '一等卧', short: 'rw' }, F: { name: '动卧', short: 'rw' },
        '3': { name: '硬卧', short: 'yw' }, J: { name: '二等卧', short: 'yw' },
        '2': { name: '软座', short: 'rz' }, '1': { name: '硬座', short: 'yz' },
        W: { name: '无座', short: 'wz' }, H: { name: '其他', short: 'qt' },
    };
    type JsonRecord = Record<string, unknown>;
    type StationRef = { station_code: string; station_name: string };
    type StationCatalog = { stations: Map<string, StationData>; names: Map<string, StationRef>; cities: Map<string, StationRef[]> };
    type QueryParams = { date: string; from_station: string; to_station: string; train_filter_flags?: string; sort_flag?: string; sort_reverse?: boolean; limited_num?: number };
    type TransferParams = QueryParams & { middle_station?: string; show_wz?: boolean };
    type Seat = { seat_name: string; num: string; status: string; price: number | null };
    type Train = { train_no: string; start_train_code: string; start_date: string; arrive_date: string; start_time: string; arrive_time: string; lishi: string; duration_minutes: number; from_station: string; to_station: string; from_station_telecode: string; to_station_telecode: string; can_buy: boolean | null; seats: Seat[]; dw_flag: string[] };
    type Transfer = { start_date: string; start_time: string; arrive_date: string; arrive_time: string; duration_minutes: number; lishi: string; wait_time: string; same_station: boolean | null; same_train: boolean; ticketList: Train[] };
    type QueryOutput = { text: string; details: { query_date: string; result_count: number; available_count: number; truncated: boolean; source: string; trains?: Train[]; transfers?: Transfer[]; pages?: number; scan_complete?: boolean } };
    class RailwayError extends Error {
        constructor(readonly code: string, message: string, readonly stage: string = 'input') { super(message); }
    }
    let catalogPromise: Promise<StationCatalog> | undefined;

    function record(value: unknown, stage: string): JsonRecord {
        if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new RailwayError('INVALID_RESPONSE', '12306 响应结构异常。', stage);
        return value as JsonRecord;
    }
    function text(value: unknown): string { return typeof value === 'string' ? value : ''; }
    function required(value: unknown, field: string): string {
        if (typeof value !== 'string' || !value.trim()) throw new RailwayError('INVALID_ARGUMENT', `${field} 必须为非空字符串。`);
        return value.trim();
    }
    function formatDate(value: Date): string { return value.toISOString().slice(0, 10); }
    function today(): string { return formatDate(new Date(Date.now() + 8 * 3600000)); }
    function dateValue(value: unknown, field: string, pastAllowed = false): string {
        const input = required(value, field);
        if (!/^\d{4}-\d{2}-\d{2}$/.test(input)) throw new RailwayError('INVALID_ARGUMENT', `${field} 必须为 yyyy-MM-dd。`);
        const date = new Date(`${input}T00:00:00Z`);
        if (!Number.isFinite(date.getTime()) || formatDate(date) !== input || (!pastAllowed && input < today())) {
            throw new RailwayError('INVALID_ARGUMENT', `${field} 日期无效或早于上海时区当前日期。`);
        }
        return input;
    }
    function code(value: unknown, field: string): string {
        const result = required(value, field).toUpperCase();
        if (!/^[A-Z]{3}$/.test(result)) throw new RailwayError('INVALID_ARGUMENT', `${field} 必须为查询得到的三位车站编码。`);
        return result;
    }
    function names(value: unknown, field: string): string[] {
        const parts = required(value, field).split('|').map(s => s.trim());
        if (parts.some(s => !s) || parts.length > 50) throw new RailwayError('INVALID_ARGUMENT', `${field} 需为 1–50 个非空名称，以 | 分隔。`);
        return [...new Set(parts)];
    }
    function options(params: QueryParams, transfer = false) {
        const date = dateValue(params.date, 'date');
        const from = code(params.from_station, 'from_station');
        const to = code(params.to_station, 'to_station');
        if (from === to) throw new RailwayError('INVALID_ARGUMENT', '出发站与到达站不能相同。');
        if (params.train_filter_flags !== undefined && typeof params.train_filter_flags !== 'string') throw new RailwayError('INVALID_ARGUMENT', 'train_filter_flags 必须为字符串。');
        const flags = (params.train_filter_flags ?? '').trim().toUpperCase();
        if (/[^GCDZTKOFS]/.test(flags)) throw new RailwayError('INVALID_ARGUMENT', 'train_filter_flags 仅支持 G/C/D/Z/T/K/O/F/S。');
        const sort = params.sort_flag ?? '';
        if (!['', 'startTime', 'arriveTime', 'duration'].includes(sort)) throw new RailwayError('INVALID_ARGUMENT', 'sort_flag 仅支持 startTime、arriveTime、duration。');
        if (params.sort_reverse !== undefined && typeof params.sort_reverse !== 'boolean') throw new RailwayError('INVALID_ARGUMENT', 'sort_reverse 必须为布尔值。');
        const limit = params.limited_num ?? (transfer ? 10 : 0);
        if (!Number.isSafeInteger(limit) || limit < (transfer ? 1 : 0) || limit > 1000 || (transfer && limit > 100)) throw new RailwayError('INVALID_ARGUMENT', transfer ? 'limited_num 必须为 1–100 的整数。' : 'limited_num 必须为 0–1000 的整数，0 表示不限制。');
        return { date, from, to, flags, sort, reverse: params.sort_reverse === true, limit };
    }
    function client(timeout = 20000) {
        return OkHttp.newBuilder().connectTimeout(Math.min(10000, timeout)).readTimeout(timeout).writeTimeout(timeout).followRedirects(false).retryOnConnectionFailure(false).build();
    }
    async function request(url: string, stage: string, cookies = '', deadline = Date.now() + QUERY_DEADLINE_MS): Promise<OkHttpResponse> {
        const remaining = deadline - Date.now();
        if (remaining <= 0) throw new RailwayError('QUERY_TIMEOUT', '查询已达到等待上限，请稍后重新查询。', stage);
        const headers: Record<string, string> = { 'User-Agent': 'Mozilla/5.0', Referer: stage.startsWith('transfer') ? TRANSFER_INIT : DIRECT_INIT };
        if (cookies) headers.Cookie = cookies;
        let response: OkHttpResponse;
        try { response = await client(Math.min(20000, remaining)).newRequest().url(url).method('GET').headers(headers).build().execute(); }
        catch (_error) { throw new RailwayError('NETWORK_ERROR', '12306 连接失败或超时，请检查网络后重试。', stage); }
        if (response.statusCode >= 300 && response.statusCode < 400) {
            const location = Object.entries(response.headers ?? {}).find(([name]) => name.toLowerCase() === 'location')?.[1] ?? '';
            if (/(?:\/passport\/|\/login[/.?]|\/login$)/i.test(location)) throw new RailwayError('AUTH_REQUIRED', '12306 要求登录，请使用官方页面；本工具不会绕过登录。', stage);
            throw new RailwayError('REDIRECT_REJECTED', '12306 返回了未受支持的跳转，请在官网确认访问状态。', stage);
        }
        if (!response.isSuccessful()) throw new RailwayError('HTTP_ERROR', `12306 返回 HTTP ${response.statusCode}。`, stage);
        return response;
    }
    function cookieHeader(response: OkHttpResponse): string {
        const result = new Map<string, string>();
        for (const [key, value] of Object.entries(response.headers ?? {})) {
            if (key.toLowerCase() !== 'set-cookie') continue;
            // Expires 内也含逗号，只在下一个 cookie 名=值之前分隔。
            for (const item of (Array.isArray(value) ? value : [value])) {
                for (const cookie of item.split(/,(?=\s*[^;,=\s]+=)/)) {
                    const pair = cookie.split(';')[0].trim();
                    const split = pair.indexOf('=');
                    if (split > 0) result.set(pair.slice(0, split), pair.slice(split + 1));
                }
            }
        }
        return [...result].map(([key, value]) => `${key}=${value}`).join('; ');
    }
    function queryUrl(path: string, params: Record<string, string>): string {
        return `${API_BASE}${path}?${Object.entries(params).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&')}`;
    }
    function jsonResponse(response: OkHttpResponse, stage: string): JsonRecord {
        let parsed: unknown;
        try { parsed = JSON.parse(response.content.replace(/^\uFEFF/, '')); }
        catch (_error) { throw new RailwayError('INVALID_RESPONSE', '12306 未返回 JSON，可能是访问校验页或服务异常；不能据此判断无票。', stage); }
        const data = record(parsed, stage);
        if (data.status === false || (typeof data.httpstatus === 'number' && data.httpstatus !== 200)) throw new RailwayError('SERVICE_REJECTED', '12306 拒绝查询，请核对日期、预售期及官方服务状态。', stage);
        return data;
    }
    function initPath(content: string, transfer: boolean): string {
        const match = content.match(transfer ? /\blc_search_url\s*=\s*['"]([^'"]+)['"]/ : /\bCLeftTicketUrl\s*=\s*['"]([^'"]+)['"]/);
        if (!match) {
            if (/passport|login|登录/i.test(content) && transfer) throw new RailwayError('AUTH_REQUIRED', '12306 中转查询当前要求登录，请使用官网；车站、直达和经停查询仍可独立使用。', 'transfer_init');
            throw new RailwayError('ENDPOINT_UNAVAILABLE', '官网未提供查询路径，停止查询以避免请求错误接口。', transfer ? 'transfer_init' : 'direct_init');
        }
        const path = match[1].replace(/^\/otn\//, '').replace(/^\//, '');
        if (!(transfer ? /^lcQuery\/query[A-Za-z0-9]*$/ : /^leftTicket\/query[A-Za-z0-9]*$/).test(path)) throw new RailwayError('INVALID_RESPONSE', '官网返回了无法识别的查询路径。', 'init');
        return `/otn/${path}`;
    }
    async function catalog(): Promise<StationCatalog> {
        if (catalogPromise) return catalogPromise;
        catalogPromise = (async () => {
            const source = (await request(STATION_URL, 'stations')).content;
            const match = source.match(/\bstation_names\s*=\s*(['"])([\s\S]*?)\1\s*;?/);
            if (!match) throw new RailwayError('INVALID_RESPONSE', '无法读取官网车站表。', 'stations');
            const stations = new Map<string, StationData>();
            const stationNames = new Map<string, StationRef>();
            const cities = new Map<string, StationRef[]>();
            // @ 是记录边界；字段新增不能让后续所有车站错位。不注入自造车站编码。
            for (const row of match[2].split('@').filter(Boolean)) {
                const fields = row.split('|');
                if (fields.length < 8 || !fields[1] || !/^[A-Z]{3}$/.test(fields[2])) throw new RailwayError('INVALID_RESPONSE', '官网车站记录格式异常。', 'stations');
                const station: StationData = { station_id: `@${fields[0]}`, station_name: fields[1], station_code: fields[2], station_pinyin: fields[3], station_short: fields[4], station_index: fields[5], code: fields[6], city: fields[7], r1: fields[8] ?? '', r2: fields[9] ?? '' };
                stations.set(station.station_code, station);
                const ref = { station_code: station.station_code, station_name: station.station_name };
                stationNames.set(station.station_name, ref);
                if (station.city) cities.set(station.city, [...(cities.get(station.city) ?? []), ref]);
            }
            if (!stations.size) throw new RailwayError('INVALID_RESPONSE', '官网车站表为空。', 'stations');
            return { stations, names: stationNames, cities };
        })();
        try { return await catalogPromise; }
        catch (error) { catalogPromise = undefined; throw error; }
    }
    async function checkStations(...codes: string[]): Promise<StationCatalog> {
        const data = await catalog();
        if (codes.some(value => !data.stations.has(value))) throw new RailwayError('STATION_NOT_FOUND', '未找到车站编码，请先查询车站名称及编码。');
        return data;
    }
    function minutes(value: string, stage: string, clock = false): number {
        const match = value.match(clock ? /^(\d{2}):(\d{2})$/ : /^(\d{1,3}):(\d{2})$/);
        if (!match || Number(match[2]) > 59 || (clock && Number(match[1]) > 23)) throw new RailwayError('INVALID_RESPONSE', '车次时间格式异常。', stage);
        return Number(match[1]) * 60 + Number(match[2]);
    }
    function durationText(total: number): string { return `${Math.floor(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}`; }
    function seatStatus(value: string): string {
        if (/^\d+$/.test(value)) return Number(value) === 0 ? '无票' : `剩余${Number(value)}张票`;
        if (['有', '充足'].includes(value)) return '有票';
        if (value === '无') return '无票';
        if (value === '候补') return '候补';
        if (value === '--' || !value) return '未提供';
        return value;
    }
    function seats(raw: JsonRecord): Seat[] {
        const prices = new Map<string, number>();
        const priceNames = new Map<string, string>();
        const priceText = text(raw.yp_info_new) || text(raw.yp_info);
        for (let i = 0; i + 10 <= priceText.length; i += 10) {
            const part = priceText.slice(i, i + 10);
            if (!/^.[0-9]{9}$/.test(part)) continue;
            const seat = Number(part.slice(6)) >= 3000 ? SEATS.W : SEATS[part[0]];
            if (seat && !prices.has(seat.short)) { prices.set(seat.short, Number(part.slice(1, 6)) / 10); priceNames.set(seat.short, seat.name); }
        }
        const groups = new Map<string, string>([['swz', '商务座'], ['tz', '特等座'], ['zy', '一等座'], ['ze', '二等座'], ['gr', '高级软卧'], ['rw', '软卧'], ['yw', '硬卧'], ['rz', '软座'], ['yz', '硬座'], ['wz', '无座'], ['srrb', '动卧'], ['qt', '其他']]);
        return [...groups].flatMap(([short, name]) => {
            const num = text(raw[`${short}_num`]);
            if (!num && !prices.has(short)) return [];
            return [{ seat_name: priceNames.get(short) ?? name, num, status: seatStatus(num), price: prices.get(short) ?? null }];
        });
    }
    function train(raw: JsonRecord, date: string, stationNames: Record<string, string>): Train {
        const start = text(raw.start_time), arrive = text(raw.arrive_time), duration = text(raw.lishi);
        const startMinutes = minutes(start, 'tickets', true), durationMinutes = minutes(duration, 'tickets');
        const arriveMinutes = minutes(arrive, 'tickets', true);
        if (durationMinutes <= 0 || (startMinutes + durationMinutes) % 1440 !== arriveMinutes) throw new RailwayError('INVALID_RESPONSE', '车次起止时间与历时不一致。', 'tickets');
        const id = text(raw.train_no), display = text(raw.station_train_code);
        if (!id || !display || !text(raw.from_station_telecode) || !text(raw.to_station_telecode)) throw new RailwayError('INVALID_RESPONSE', '车次缺少标识或站点。', 'tickets');
        const from = text(raw.from_station_telecode), to = text(raw.to_station_telecode);
        const flags = text(raw.dw_flag).split('#');
        // start_train_date 是始发日期；上车站日期来自本次查询，跨日排序统一使用 UTC 算术。
        const arrivalDate = formatDate(new Date(Date.parse(`${date}T00:00:00Z`) + (startMinutes + durationMinutes) * 60000));
        return { train_no: id, start_train_code: display, start_date: date, arrive_date: arrivalDate, start_time: start, arrive_time: arrive, lishi: duration, duration_minutes: durationMinutes,
            from_station: text(raw.from_station_name) || stationNames[from] || from,
            to_station: text(raw.to_station_name) || stationNames[to] || to, from_station_telecode: from, to_station_telecode: to,
            can_buy: raw.canWebBuy === 'Y' ? true : raw.canWebBuy === 'N' ? false : null, seats: seats(raw),
            dw_flag: [...(flags[0] === '5' ? ['智能动车组'] : []), ...(flags[1] === '1' ? ['复兴号'] : [])] };
    }
    function trainMatches(item: Train, flags: string): boolean {
        return !flags || [...flags].some(flag => {
            if (flag === 'G') return /^[GC]/.test(item.start_train_code);
            if (flag === 'O') return !/^[GCDZTK]/.test(item.start_train_code);
            if (flag === 'F') return item.dw_flag.includes('复兴号');
            if (flag === 'S') return item.dw_flag.includes('智能动车组');
            return item.start_train_code.startsWith(flag);
        });
    }
    function filtered<T extends Train | Transfer>(items: T[], opts: ReturnType<typeof options>, applyLimit = true): T[] {
        const result = items.filter(item => 'ticketList' in item ? item.ticketList.every(t => trainMatches(t, opts.flags)) : trainMatches(item, opts.flags));
        if (opts.sort) result.sort((a, b) => {
            const key = (item: T) => opts.sort === 'duration' ? item.duration_minutes : Date.parse(`${opts.sort === 'startTime' ? item.start_date : item.arrive_date}T${opts.sort === 'startTime' ? item.start_time : item.arrive_time}:00Z`);
            return (key(a) - key(b)) * (opts.reverse ? -1 : 1);
        });
        return applyLimit && opts.limit ? result.slice(0, opts.limit) : result;
    }
    function trainText(item: Train): string {
        const route = `${item.start_train_code}(实际车次train_no: ${item.train_no}) ${item.from_station}(telecode: ${item.from_station_telecode}) -> ${item.to_station}(telecode: ${item.to_station_telecode})`;
        const lines = [`${route} ${item.start_date} ${item.start_time} -> ${item.arrive_date} ${item.arrive_time} 历时：${item.lishi}`];
        if (item.can_buy === false) lines.push('- 当前不可在线预订，请以官网销售状态为准。');
        if (!item.seats.length) lines.push('- 座席余票及票价未提供，不表示无票。');
        for (const seat of item.seats) lines.push(`- ${seat.seat_name}: ${seat.status} ${seat.price === null ? '票价未提供' : `${seat.price}元`}`);
        return lines.join('\n');
    }
    async function get_current_date() { return today(); }
    async function get_stations_code_in_city(params: { city: string }) {
        const name = required(params.city, 'city').replace(/市$/, '');
        const found = (await catalog()).cities.get(name);
        if (!found) throw new RailwayError('CITY_NOT_FOUND', '未找到城市，请使用官方城市名或查询具体车站。');
        return found;
    }
    async function get_station_code_of_citys(params: { citys: string }) {
        const requested = names(params.citys, 'citys');
        const data = await catalog();
        return Object.fromEntries(requested.map(input => {
            const city = input.replace(/市$/, '');
            const candidates = data.cities.get(city) ?? [];
            const exact = candidates.find(station => station.station_name === city);
            return [city, exact ? { ...exact, scope: 'station', note: '这是同名车站编码，不保证涵盖全市车站；全市范围请查询城市车站列表。' } : { error: candidates.length ? '城市没有同名车站，请从 candidates 中选择，不能自动代选。' : '未检索到城市。', candidates }];
        }));
    }
    async function get_station_code_by_names(params: { station_names: string }) {
        const requested = names(params.station_names, 'station_names');
        const data = await catalog();
        return Object.fromEntries(requested.map(input => {
            // 先匹配官方全名，再解释用户附加的“站”；不损坏本身以“站”结尾的正式站名。
            const exact = data.names.get(input);
            const name = exact ? input : input.replace(/站$/, '');
            return [name, exact ?? data.names.get(name) ?? { error: '未检索到车站。' }];
        }));
    }
    async function get_station_by_telecode(params: { station_telecode: string }) {
        const value = code(params.station_telecode, 'station_telecode');
        return (await checkStations(value)).stations.get(value)!;
    }
    async function get_tickets(params: QueryParams): Promise<QueryOutput> {
        const opts = options(params);
        const deadline = Date.now() + QUERY_DEADLINE_MS;
        const data = await checkStations(opts.from, opts.to);
        const init = await request(DIRECT_INIT, 'direct_init', '', deadline);
        let path = initPath(init.content, false);
        const cookies = cookieHeader(init);
        const args = { 'leftTicketDTO.train_date': opts.date, 'leftTicketDTO.from_station': opts.from, 'leftTicketDTO.to_station': opts.to, purpose_codes: 'ADULT' };
        let response = jsonResponse(await request(queryUrl(path, args), 'direct_query', cookies, deadline), 'direct_query');
        // c_url 是服务端明确的端点选择指令，只接受本站查询路径且最多转接一次。
        if (typeof response.c_url === 'string') {
            const next = response.c_url.replace(/^\/otn\//, '');
            if (!/^leftTicket\/query[A-Za-z0-9]*$/.test(next) || `/otn/${next}` === path) throw new RailwayError('INVALID_RESPONSE', '官网查询路径循环或无效。', 'direct_query');
            path = `/otn/${next}`;
            response = jsonResponse(await request(queryUrl(path, args), 'direct_query', cookies, deadline), 'direct_query');
        }
        const body = record(response.data, 'direct_query');
        if (!Array.isArray(body.result)) throw new RailwayError('INVALID_RESPONSE', '余票响应缺少车次列表。', 'direct_query');
        const mapping = Object.fromEntries([...data.stations].map(([key, station]) => [key, station.station_name]));
        if (body.map && typeof body.map === 'object' && !Array.isArray(body.map)) for (const [key, value] of Object.entries(body.map)) if (typeof value === 'string') mapping[key] = value;
        const all = body.result.map(row => {
            if (typeof row !== 'string' || row.split('|').length < 34) throw new RailwayError('INVALID_RESPONSE', '余票车次字段不完整。', 'direct_query');
            const fields = row.split('|');
            return train(Object.fromEntries(TicketDataKeys.map((key, index) => [key, fields[index] ?? ''])), opts.date, mapping);
        });
        const matched = filtered(all, opts, false), selected = filtered(matched, opts);
        return { text: selected.length ? selected.map(trainText).join('\n\n') : '没有查询到符合条件的车次。', details: { query_date: opts.date, source: `${API_BASE}${path}`, result_count: selected.length, available_count: matched.length, truncated: selected.length < matched.length, trains: selected } };
    }
    function transferRow(value: unknown, date: string): Transfer {
        const row = record(value, 'transfer_query');
        if (!Array.isArray(row.fullList) || row.fullList.length !== 2) throw new RailwayError('INVALID_RESPONSE', '中转方案必须包含两个行程段。', 'transfer_query');
        const trains: Train[] = [];
        for (const value of row.fullList) {
            const leg = record(value, 'transfer_query');
            // 中转第二程使用官方 middle_date；始发日期不等于乘客上车日期。
            let departure = trains.length === 0 ? date : text(row.middle_date);
            if (/^\d{8}$/.test(departure)) departure = `${departure.slice(0, 4)}-${departure.slice(4, 6)}-${departure.slice(6)}`;
            if (!departure) throw new RailwayError('INVALID_RESPONSE', '中转方案缺少第二程上车日期，不能用始发日期代替。', 'transfer_query');
            const parsed = train(leg, dateValue(departure, 'middle_date', true), {});
            const previous = trains[trains.length - 1];
            if (previous && Date.parse(`${parsed.start_date}T${parsed.start_time}:00Z`) < Date.parse(`${previous.arrive_date}T${previous.arrive_time}:00Z`)) throw new RailwayError('INVALID_RESPONSE', '中转方案的第二程早于第一程到达时间。', 'transfer_query');
            trains.push(parsed);
        }
        const suppliedDuration = row.all_lishi_minutes;
        let duration: number;
        if (typeof suppliedDuration === 'number' && Number.isSafeInteger(suppliedDuration) && suppliedDuration > 0) {
            duration = suppliedDuration;
        } else {
            const raw = text(row.all_lishi);
            const match = raw.match(/^(?:(\d+)小时)?(?:(\d+)分钟)?$/);
            if (!match || (!match[1] && !match[2])) throw new RailwayError('INVALID_RESPONSE', '中转总历时缺失或格式异常。', 'transfer_query');
            duration = Number(match[1] || 0) * 60 + Number(match[2] || 0);
            if (!Number.isSafeInteger(duration) || duration <= 0) throw new RailwayError('INVALID_RESPONSE', '中转总历时必须为正整数分钟。', 'transfer_query');
        }
        const total = duration;
        const first = trains[0], last = trains[trains.length - 1];
        const elapsed = (Date.parse(`${last.arrive_date}T${last.arrive_time}:00Z`) - Date.parse(`${first.start_date}T${first.start_time}:00Z`)) / 60000;
        if (total !== elapsed) throw new RailwayError('INVALID_RESPONSE', '中转总历时与两程日期时间不一致。', 'transfer_query');
        return { start_date: first.start_date, start_time: first.start_time, arrive_date: last.arrive_date, arrive_time: last.arrive_time, duration_minutes: total, lishi: durationText(total), wait_time: text(row.wait_time), same_station: row.same_station === '0' ? true : row.same_station === '1' ? false : null, same_train: row.same_train === 'Y', ticketList: trains };
    }
    async function get_interline_tickets(params: TransferParams): Promise<QueryOutput> {
        const opts = options(params, true);
        const middle = params.middle_station ? code(params.middle_station, 'middle_station') : '';
        if (params.show_wz !== undefined && typeof params.show_wz !== 'boolean') throw new RailwayError('INVALID_ARGUMENT', 'show_wz 必须为布尔值。');
        const deadline = Date.now() + QUERY_DEADLINE_MS;
        await checkStations(opts.from, opts.to, ...(middle ? [middle] : []));
        const init = await request(TRANSFER_INIT, 'transfer_init', '', deadline);
        const path = initPath(init.content, true), cookies = cookieHeader(init);
        const args = { train_date: opts.date, from_station_telecode: opts.from, to_station_telecode: opts.to, middle_station: middle, result_index: '0', can_query: 'Y', isShowWZ: params.show_wz ? 'Y' : 'N', purpose_codes: '00', channel: 'E' };
        const cursors = new Set<string>(), seen = new Set<string>(), rows: Transfer[] = [];
        let pages = 0, scanComplete = false;
        while (pages < MAX_TRANSFER_PAGES && rows.length < MAX_TRANSFER_ROWS) {
            if (cursors.has(args.result_index)) throw new RailwayError('PAGINATION_STALLED', '12306 返回重复分页游标，已停止查询。', 'transfer_query');
            cursors.add(args.result_index);
            const response = jsonResponse(await request(queryUrl(path, args), 'transfer_query', cookies, deadline), 'transfer_query');
            const body = record(response.data, 'transfer_query');
            if (!Array.isArray(body.middleList)) throw new RailwayError('INVALID_RESPONSE', '中转响应缺少方案列表，不能据此判断无票。', 'transfer_query');
            pages++;
            let added = 0;
            if (body.middleList.length > MAX_TRANSFER_ROWS - rows.length) throw new RailwayError('RESPONSE_TOO_LARGE', '中转响应超过本次方案数量上限，请缩小查询范围。', 'transfer_query');
            for (const value of body.middleList) {
                const parsed = transferRow(value, opts.date);
                const identity = JSON.stringify(parsed.ticketList.map(leg => [leg.train_no, leg.start_date, leg.from_station_telecode, leg.to_station_telecode]));
                if (!seen.has(identity)) { seen.add(identity); rows.push(parsed); added++; }
            }
            scanComplete = body.can_query === 'N' || body.middleList.length === 0;
            if (scanComplete) break;
            if (!added) throw new RailwayError('PAGINATION_STALLED', '分页未返回新方案，已停止查询。', 'transfer_query');
            if (!opts.sort && filtered(rows, opts, false).length >= opts.limit) break;
            const next = body.result_index;
            if ((typeof next !== 'string' && typeof next !== 'number') || !/^\d+$/.test(`${next}`)) throw new RailwayError('INVALID_RESPONSE', '中转响应缺少有效分页游标。', 'transfer_query');
            args.result_index = `${next}`;
        }
        const matched = filtered(rows, opts, false), selected = filtered(matched, opts);
        const resultText = selected.map(item => `${item.same_train ? '同车换乘' : item.same_station === null ? '换乘类型未提供' : item.same_station ? '同站换乘' : '换站换乘'} | 等待 ${item.wait_time || '未提供'} | 总历时 ${item.lishi}\n${item.ticketList.map(trainText).join('\n')}`).join('\n\n');
        return { text: (resultText || '已扫描范围内没有符合条件的中转方案。') + (!scanComplete ? `\n仅覆盖已扫描的 ${pages} 页，排序与筛选不代表全部方案。` : ''), details: { query_date: opts.date, source: `${API_BASE}${path}`, result_count: selected.length, available_count: matched.length, truncated: !scanComplete || selected.length < matched.length, transfers: selected, pages, scan_complete: scanComplete } };
    }
    async function get_train_route_stations(params: { train_no: string; from_station_telecode: string; to_station_telecode: string; depart_date: string }) {
        const id = required(params.train_no, 'train_no');
        if (!/^[A-Za-z0-9]{8,20}$/.test(id)) throw new RailwayError('INVALID_ARGUMENT', 'train_no 必须为余票结果中的内部车次编号，例如 240000G53106，不能使用 G531。');
        const from = code(params.from_station_telecode, 'from_station_telecode'), to = code(params.to_station_telecode, 'to_station_telecode');
        const date = dateValue(params.depart_date, 'depart_date');
        if (from === to) throw new RailwayError('INVALID_ARGUMENT', '出发站与到达站不能相同。');
        await checkStations(from, to);
        const init = await request(DIRECT_INIT, 'route_init');
        const result = jsonResponse(await request(queryUrl('/otn/czxx/queryByTrainNo', { train_no: id, from_station_telecode: from, to_station_telecode: to, depart_date: date }), 'route_query', cookieHeader(init)), 'route_query');
        const data = record(result.data, 'route_query');
        if (!Array.isArray(data.data)) throw new RailwayError('INVALID_RESPONSE', '经停响应缺少车站列表。', 'route_query');
        return data.data.map(value => {
            const stop = record(value, 'route_query');
            if (!text(stop.station_name) || !/^\d+$/.test(text(stop.station_no))) throw new RailwayError('INVALID_RESPONSE', '经停站信息不完整。', 'route_query');
            return { station_name: text(stop.station_name), station_no: Number(stop.station_no), arrive_time: text(stop.arrive_time), start_time: text(stop.start_time), stopover_time: text(stop.stopover_time) };
        });
    }
    async function wrap<P>(fn: (params: P) => Promise<unknown>, params: P) {
        let result: Record<string, unknown>;
        try {
            const value = await fn(params ?? {} as P);
            const query = value !== null && typeof value === 'object' && 'text' in value && 'details' in value ? value as QueryOutput : undefined;
            result = { success: true, message: '查询完成', data: query ? query.text : value, ...(query ? { details: query.details } : {}) };
        } catch (error) {
            const failure = error instanceof RailwayError ? error : new RailwayError('INTERNAL_ERROR', '工具未能完成查询，请检查参数或反馈问题。', 'runtime');
            console.error(`12306.${fn.name}: ${failure.code} stage=${failure.stage}`);
            result = { success: false, message: failure.message, error: { code: failure.code, stage: failure.stage } };
        }
        // 唯一完成点位于 catch 之外，避免完成回调异常触发第二次 complete。
        complete(result);
    }
    async function main() {
        return { date: today(), station_count: (await catalog()).stations.size, message: '车站数据检查完成。余票、中转和经停需使用明确日期与站点分别验收。' };
    }
    return {
        get_current_date: (p: {}) => wrap(get_current_date, p),
        get_stations_code_in_city: (p: Parameters<typeof get_stations_code_in_city>[0]) => wrap(get_stations_code_in_city, p),
        get_station_code_of_citys: (p: Parameters<typeof get_station_code_of_citys>[0]) => wrap(get_station_code_of_citys, p),
        get_station_code_by_names: (p: Parameters<typeof get_station_code_by_names>[0]) => wrap(get_station_code_by_names, p),
        get_station_by_telecode: (p: Parameters<typeof get_station_by_telecode>[0]) => wrap(get_station_by_telecode, p),
        get_tickets: (p: QueryParams) => wrap(get_tickets, p),
        get_interline_tickets: (p: TransferParams) => wrap(get_interline_tickets, p),
        get_train_route_stations: (p: Parameters<typeof get_train_route_stations>[0]) => wrap(get_train_route_stations, p),
        main: (p: {}) => wrap(main, p),
    };
})();

exports.get_current_date = ticket12306.get_current_date;
exports.get_stations_code_in_city = ticket12306.get_stations_code_in_city;
exports.get_station_code_of_citys = ticket12306.get_station_code_of_citys;
exports.get_station_code_by_names = ticket12306.get_station_code_by_names;
exports.get_station_by_telecode = ticket12306.get_station_by_telecode;
exports.get_tickets = ticket12306.get_tickets;
exports.get_interline_tickets = ticket12306.get_interline_tickets;
exports.get_train_route_stations = ticket12306.get_train_route_stations;
exports.main = ticket12306.main;
