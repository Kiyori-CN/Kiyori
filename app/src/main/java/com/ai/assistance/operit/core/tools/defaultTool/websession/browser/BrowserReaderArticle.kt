package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.json.JSONObject

/** 阅读模式正文块的语义类型；样式由界面决定，提取层只负责判定结构。 */
enum class BrowserReaderBlockKind {
    HEADING_1,
    HEADING_2,
    HEADING_3,
    PARAGRAPH,
    LIST_ITEM,
    QUOTE,
    CODE,
}

data class BrowserReaderBlock(val kind: BrowserReaderBlockKind, val text: String)

/**
 * 一次正文提取的完整结果。
 *
 * `failed` 与 `blocks.isEmpty()` 是两件事：脚本被页面环境拒绝（返回 `null` 或非法 JSON）与
 * 页面确实没有正文段落，对用户的下一步操作不同，界面必须能分辨，不能都显示成“空”。
 */
data class BrowserReaderArticle(
    val title: String,
    val byline: String,
    val siteLabel: String,
    val blocks: List<BrowserReaderBlock>,
    val characterCount: Int,
    val truncated: Boolean,
    val failed: Boolean,
) {
    val isEmpty: Boolean get() = blocks.isEmpty()

    /** 中文按每分钟约 400 字估算；不足一分钟按一分钟显示，避免出现“约 0 分钟”。 */
    val estimatedMinutes: Int get() = ((characterCount + 399) / 400).coerceAtLeast(1)
}

/** 提取脚本的正文上限，与既有浏览器契约一致。 */
const val BROWSER_READER_MAX_CHARACTERS: Int = 120_000

/**
 * 在当前宿主 WebView 上提取正文的脚本。
 *
 * 取 `article` / `[role=main]` / `main` / `body` 中可见文字最多的一个作为正文根，只收集最内层的
 * 块级元素，因此不会把同一段文字随祖先重复输出一次。导航、页眉页脚、表单控件和被隐藏的节点
 * 按标签与计算样式排除。结构化收集不到任何块时改用根节点 `innerText` 的换行切分——这是同一次
 * 提取的第二条路径，不是产品级降级：没有它，纯 `div` 排版的网页会被判成“没有正文”。
 *
 * 这是 Kotlin 原始字符串，反斜杠不会被 Kotlin 处理，正则里的 `\t`、`\n`、` ` 原样交给 JS。
 */
val BROWSER_READER_EXTRACTION_SCRIPT: String = """
(() => {
  const LIMIT = $BROWSER_READER_MAX_CHARACTERS;
  const BLOCKS = 'p,h1,h2,h3,h4,h5,h6,li,blockquote,pre,figcaption,dd,dt';
  const SKIP = new Set(['NAV','ASIDE','FOOTER','HEADER','SCRIPT','STYLE','NOSCRIPT','FORM','BUTTON','SELECT','TEXTAREA','INPUT','IFRAME','SVG','CANVAS','VIDEO','AUDIO']);
  const visible = (el) => {
    const style = window.getComputedStyle(el);
    return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0';
  };
  const excluded = (el, root) => {
    for (let node = el; node && node !== root; node = node.parentElement) {
      if (SKIP.has(node.tagName)) return true;
      if (node.getAttribute && node.getAttribute('aria-hidden') === 'true') return true;
      if (node.hidden) return true;
    }
    return false;
  };
  const candidates = [];
  document.querySelectorAll('article,[role=main],main').forEach((el) => candidates.push(el));
  if (document.body) candidates.push(document.body);
  let root = null;
  let rootLength = -1;
  candidates.forEach((el) => {
    if (!el || !visible(el)) return;
    const length = (el.innerText || '').trim().length;
    if (length > rootLength) { root = el; rootLength = length; }
  });
  if (!root) return JSON.stringify({ blocks: [], characters: 0, truncated: false, title: document.title || '', byline: '' });
  const kindOf = (tag) => {
    if (tag === 'H1') return 'h1';
    if (tag === 'H2') return 'h2';
    if (tag === 'H3' || tag === 'H4' || tag === 'H5' || tag === 'H6') return 'h3';
    if (tag === 'LI' || tag === 'DD' || tag === 'DT') return 'li';
    if (tag === 'BLOCKQUOTE') return 'quote';
    if (tag === 'PRE') return 'code';
    return 'p';
  };
  const blocks = [];
  let characters = 0;
  let truncated = false;
  const push = (kind, raw) => {
    if (truncated) return;
    const source = raw || '';
    const collapsed = kind === 'code'
      ? source.replace(/\n{3,}/g, '\n\n')
      : source.replace(/[ \t ]+/g, ' ').replace(/\n{3,}/g, '\n\n');
    const text = collapsed.trim();
    if (!text) return;
    if (characters + text.length > LIMIT) {
      const room = LIMIT - characters;
      if (room > 0) { blocks.push({ k: kind, x: text.slice(0, room) }); characters = LIMIT; }
      truncated = true;
      return;
    }
    blocks.push({ k: kind, x: text });
    characters += text.length;
  };
  root.querySelectorAll(BLOCKS).forEach((el) => {
    if (truncated) return;
    if (el.querySelector(BLOCKS)) return;
    if (excluded(el, root)) return;
    if (!visible(el)) return;
    push(kindOf(el.tagName), el.innerText);
  });
  if (blocks.length === 0) {
    (root.innerText || '').split(/\n+/).forEach((line) => push('p', line));
  }
  const authorMeta = document.querySelector('meta[name="author"]');
  const headline = document.querySelector('article h1') || document.querySelector('h1');
  return JSON.stringify({
    blocks: blocks,
    characters: characters,
    truncated: truncated,
    title: (headline && headline.innerText) || document.title || '',
    byline: (authorMeta && authorMeta.content) || ''
  });
})()
"""

/**
 * 解析提取脚本的返回值。`raw` 是 `evaluateJavascript` 的原始结果：脚本返回字符串，
 * WebView 再把它 JSON 编码一次，因此先解一层字符串再解析对象。
 */
fun parseBrowserReaderArticle(
    raw: String?,
    fallbackTitle: String,
    siteLabel: String,
): BrowserReaderArticle {
    val failure = BrowserReaderArticle(
        title = fallbackTitle,
        byline = "",
        siteLabel = siteLabel,
        blocks = emptyList(),
        characterCount = 0,
        truncated = false,
        failed = true,
    )
    val payload = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return failure
    val json = runCatching {
        // 脚本返回的是字符串字面量；先解外层，已经是对象时直接解析。
        val unwrapped = runCatching { JSONObject("{\"v\":$payload}").getString("v") }.getOrNull()
        JSONObject(unwrapped ?: payload)
    }.getOrNull() ?: return failure

    val array = json.optJSONArray("blocks")
    val blocks = buildList {
        for (index in 0 until (array?.length() ?: 0)) {
            val entry = array?.optJSONObject(index) ?: continue
            val text = entry.optString("x").trim()
            if (text.isEmpty()) continue
            val kind = when (entry.optString("k")) {
                "h1" -> BrowserReaderBlockKind.HEADING_1
                "h2" -> BrowserReaderBlockKind.HEADING_2
                "h3" -> BrowserReaderBlockKind.HEADING_3
                "li" -> BrowserReaderBlockKind.LIST_ITEM
                "quote" -> BrowserReaderBlockKind.QUOTE
                "code" -> BrowserReaderBlockKind.CODE
                else -> BrowserReaderBlockKind.PARAGRAPH
            }
            add(BrowserReaderBlock(kind, text))
        }
    }
    return BrowserReaderArticle(
        title = json.optString("title").trim().ifBlank { fallbackTitle },
        byline = json.optString("byline").trim(),
        siteLabel = siteLabel,
        blocks = blocks,
        characterCount = json.optInt("characters", blocks.sumOf { it.text.length }),
        truncated = json.optBoolean("truncated", false),
        failed = false,
    )
}

/** 朗读用的连续文本：标题与正文块之间补换行，避免 TTS 把两段连读成一句。 */
fun browserReaderSpeechText(article: BrowserReaderArticle): String =
    (listOf(article.title) + article.blocks.map { it.text })
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")

/** 阅读模式可选字号倍率；界面按档位加减，不提供自由滑动以保证版心稳定。 */
val BROWSER_READER_FONT_SCALES: List<Float> = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.75f)

/** 把任意存量倍率吸附到最近档位；旧偏好或异常值不能让加减按钮失去可用档。 */
fun browserReaderFontScaleIndex(scale: Float): Int {
    val nearest = BROWSER_READER_FONT_SCALES.minByOrNull { kotlin.math.abs(it - scale) }
        ?: return BROWSER_READER_FONT_SCALES.indexOf(1.0f)
    return BROWSER_READER_FONT_SCALES.indexOf(nearest)
}
