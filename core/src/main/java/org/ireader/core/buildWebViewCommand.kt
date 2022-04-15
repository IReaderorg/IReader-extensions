package org.ireader.core

import org.ireader.core_api.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

const val WEBVIEW_PARSE = "https://www.ireader.org/"
const val PARSE_BOOKS = "parseBooks"

fun commandSeparator(): String = "$%&$"

fun buildWebViewCommand(
    urL: String = "null",
    ajaxSelector: String = "null",
    cloudflareBypass: Int = 0,
    timeout: Long = 5000L,
    userAgent: String = "null",
    default: String = "null",
    mode: String? = "-1",
    page:String? = "null",
    maxPage:String? = "null",
    reverseList:String?= "null",
    html: String? = "null",
): String {
    return "https://www.ireader.org/$urL${commandSeparator()}$cloudflareBypass${commandSeparator()}$ajaxSelector${commandSeparator()}$timeout${commandSeparator()}$default${commandSeparator()}$userAgent${commandSeparator()}$mode${commandSeparator()}${page}${commandSeparator()}$maxPage${commandSeparator()}$reverseList${commandSeparator()}$html"
}

fun parseWebViewCommand(
    command: String,
): WebViewCommand? {
    val cmd = command.replace("https://www.ireader.org/", "").split(commandSeparator())
    return if (command.contains("https://www.ireader.org/") && cmd.size == 11) {
        return WebViewCommand(
            urL = cmd[0],
            cloudflareBypass = cmd[1].toInt(),
            ajaxSelector = if (cmd[2] == "null") null else cmd[2],
            timeout = cmd[3].toLong(),
            userAgent = if (cmd[4] == "null") null else cmd[4],
            default = cmd[5],
            mode = if (cmd[6] == "null") null else cmd[6],
            page = if (cmd[7] == "null") null else cmd[7],
            maxPage = if (cmd[8] == "null") null else cmd[8],
            reverseList = if (cmd[9] == "null") null else cmd[9],
            html = if (cmd[10] == "null") null else cmd[10],
        )
    } else {
        null
    }
}

fun WebViewCommand.update(mode: String, html: String?, clear: Boolean = false): String {
    return "https://www.ireader.org/${if (clear) "null" else urL}${commandSeparator()}$cloudflareBypass${commandSeparator()}$ajaxSelector${commandSeparator()}$timeout${commandSeparator()}$default${commandSeparator()}$userAgent${commandSeparator()}$mode${commandSeparator()}${page}${commandSeparator()}$maxPage${commandSeparator()}$reverseList${commandSeparator()}$html"
}

data class WebViewCommand(
    val urL: String = "null",
    val ajaxSelector: String? = "null",
    val cloudflareBypass: Int = 0,
    val timeout: Long = 5000L,
    val userAgent: String? = "null",
    val default: String? = "null",
    val mode: String? = "null",
    val page: String? = "null",
    val maxPage: String? = "null",
    val reverseList: String? = "null",
    val html: String? = "null"
)


fun prepareContentWebViewMode(
    chapter: ChapterInfo,
    onSuccess: (ChapterInfo) -> List<Page>,
    selector: String,
    CloudflareBypass: Boolean
): List<Page> {
    return when {
        chapter.scanlator.contains(PARSE_CONTENT) -> onSuccess(chapter)
        else -> {
            val cmd = buildWebViewCommand(
                chapter.key,
                ajaxSelector = selector,
                if (CloudflareBypass) 1 else 0,
                mode = PARSE_CONTENT
            )
            return listOf(Text(cmd))
        }
    }
}

fun prepareDetailWebViewMode(
    manga: MangaInfo,
    onSuccess: (MangaInfo) -> MangaInfo,
    selector: String,
    CloudflareBypass: Boolean
): MangaInfo {
    return when {
        manga.key.contains(PARSE_DETAIL) -> onSuccess(manga)
        else -> {
            val cmd = buildWebViewCommand(
                manga.artist,
                ajaxSelector = selector,
                if (CloudflareBypass) 1 else 0,
                mode = PARSE_DETAIL
            )
            return MangaInfo("", "", artist = cmd)
        }
    }
}

fun prepareBooksWebViewMode(
    urL: String = "",
    mode: String = "-1",
    listing: Listing? = null,
    onSuccess: (String) -> MangasPageInfo,
    selector: String = "",
    CloudflareBypass: Boolean = false
): MangasPageInfo {
    return when {
        listing?.name?.contains(WEBVIEW_PARSE) == true -> onSuccess(listing.name)
        else -> {
            val cmd = buildWebViewCommand(
                urL,
                ajaxSelector = selector,
                if (CloudflareBypass) 1 else 0,
                mode = mode
            )
            return MangasPageInfo(listOf(MangaInfo("", "", artist = cmd)), false)
        }
    }
}

fun booksRequestReceiver(
    document: Document,
    elementSelector: String,
    nextPageSelector: String? = null,
    parser: (element: Element) -> MangaInfo
): MangasPageInfo {
    val books = document.select(elementSelector).map { element ->
        parser(element)
    }

    val hasNextPage = nextPageSelector?.let { selector ->
        document.select(selector).first()
    } != null

    return MangasPageInfo(books, hasNextPage)
}


fun booksRequestSender(
    urL: String,
    selector: String,
    CloudflareBypass: Boolean = false,
    mode: String = "-1",
    userAgent: String = "null",
    timeout: Long = 5000L,

): MangasPageInfo {
    val cmd = buildWebViewCommand(
        urL,
        ajaxSelector = selector,
        if (CloudflareBypass) 1 else 0,
        mode = mode,
        userAgent = userAgent ?: "null",
        timeout = timeout,
    )
    return MangasPageInfo(listOf(MangaInfo("", "", artist = cmd)), false)
}


fun chaptersRequestReceiver(
    document: Document,
    elementSelector: String,
    parser: (element: Element) -> ChapterInfo
): List<ChapterInfo> {
    val chapters = document.select(elementSelector).map { element ->
        parser(element)
    }



    return chapters
}

fun chaptersRequestSender(
    urL: String,
    selector: String,
    CloudflareBypass: Boolean = false,
    mode: String = "-1",
    userAgent: String = "null",
    timeout: Long = 5000L,
    page: String? = "null",
    maxPage: String?= null
    ): List<ChapterInfo> {
    val cmd = buildWebViewCommand(
        urL,
        ajaxSelector = selector,
        if (CloudflareBypass) 1 else 0,
        mode = mode,
        userAgent = userAgent ?: "null",
        timeout = timeout,
        page = page,
        maxPage = maxPage
    )
    return listOf(ChapterInfo("", "", scanlator = cmd))
}