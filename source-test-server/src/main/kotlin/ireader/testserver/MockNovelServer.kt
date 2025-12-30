package ireader.testserver

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ireader.core.source.model.*
import kotlinx.coroutines.runBlocking

fun Application.configureMockServer() {
    routing {
        route("/browse") {
            get { call.respondText(generateSourceListPage(), ContentType.Text.Html) }
            get("/{sourceId}") {
                val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                call.respondText(generateExplorePage(sourceId, page), ContentType.Text.Html)
            }
            get("/{sourceId}/search") {
                val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                val query = call.request.queryParameters["q"] ?: ""
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                call.respondText(generateSearchPage(sourceId, query, page), ContentType.Text.Html)
            }
            get("/{sourceId}/novel") {
                val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                val url = call.request.queryParameters["url"] ?: ""
                call.respondText(generateNovelDetailPage(sourceId, url), ContentType.Text.Html)
            }
            get("/{sourceId}/read") {
                val sourceId = call.parameters["sourceId"]?.toLongOrNull()
                val url = call.request.queryParameters["url"] ?: ""
                val novelUrl = call.request.queryParameters["novel"] ?: ""
                call.respondText(generateChapterContentPage(sourceId, url, novelUrl), ContentType.Text.Html)
            }
        }
    }
}

private fun css() = """
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Segoe UI',sans-serif;background:linear-gradient(135deg,#1a1a2e,#16213e);color:#e0e0e0;min-height:100vh;line-height:1.6}
a{color:#64b5f6;text-decoration:none}a:hover{color:#90caf9}
.header{background:rgba(0,0,0,0.3);backdrop-filter:blur(10px);border-bottom:1px solid rgba(255,255,255,0.1);padding:15px 0;position:sticky;top:0;z-index:100}
.header-inner{max-width:1200px;margin:0 auto;padding:0 20px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:15px}
.logo{font-size:1.5rem;font-weight:bold;color:#fff;display:flex;align-items:center;gap:10px}
nav{display:flex;gap:10px;flex-wrap:wrap}nav a{color:#b0b0b0;padding:8px 16px;border-radius:20px;font-size:0.9rem}nav a:hover,nav a.active{background:rgba(255,255,255,0.1);color:#fff}
.search-form{display:flex;gap:10px}.search-form input{padding:10px 20px;border:none;border-radius:25px;background:rgba(255,255,255,0.1);color:#fff;width:200px;outline:none}
.search-form button{padding:10px 20px;border:none;border-radius:25px;background:linear-gradient(135deg,#e94560,#ff6b6b);color:#fff;cursor:pointer;font-weight:bold}
.container{max-width:1200px;margin:0 auto;padding:30px 20px}
.section-title{font-size:1.5rem;margin-bottom:20px;padding-bottom:10px;border-bottom:2px solid #e94560}
.source-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:20px}
.source-card{background:rgba(255,255,255,0.05);border-radius:12px;padding:20px;transition:all 0.3s;border:1px solid rgba(255,255,255,0.1)}
.source-card:hover{transform:translateY(-3px);box-shadow:0 10px 30px rgba(0,0,0,0.3);border-color:#e94560}
.source-name{font-size:1.1rem;font-weight:600;color:#fff;margin-bottom:8px}
.source-meta{font-size:0.85rem;color:#888;display:flex;gap:15px;flex-wrap:wrap}
</style>
"""


private fun css2() = """
<style>
.novel-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:25px}
.novel-card{background:rgba(255,255,255,0.05);border-radius:12px;overflow:hidden;transition:transform 0.3s,box-shadow 0.3s;border:1px solid rgba(255,255,255,0.1)}
.novel-card:hover{transform:translateY(-5px);box-shadow:0 10px 30px rgba(0,0,0,0.3)}
.novel-cover{width:100%;height:250px;object-fit:cover;background:#2a2a4a}
.novel-info{padding:15px}
.novel-title{font-size:0.95rem;font-weight:600;color:#fff;margin-bottom:8px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;min-height:2.8em}
.novel-meta{font-size:0.8rem;color:#888}
.status{display:inline-block;padding:3px 8px;border-radius:4px;font-size:0.7rem;font-weight:bold;text-transform:uppercase}
.status-ongoing{background:#2196f3;color:#fff}.status-completed{background:#4caf50;color:#fff}.status-hiatus,.status-on-hiatus{background:#ff9800;color:#fff}
.pagination{display:flex;justify-content:center;gap:10px;margin-top:40px;flex-wrap:wrap}
.pagination a,.pagination span{padding:10px 18px;border-radius:8px;background:rgba(255,255,255,0.1);color:#fff}
.pagination a:hover{background:#e94560}.pagination .current{background:#e94560}
.error-box{background:rgba(233,69,96,0.2);border:1px solid #e94560;border-radius:12px;padding:30px;text-align:center;margin:20px 0}
.error-box h3{color:#e94560;margin-bottom:10px}
.footer{background:rgba(0,0,0,0.3);padding:30px 0;margin-top:50px;text-align:center;color:#666;border-top:1px solid rgba(255,255,255,0.1)}
.breadcrumb{color:#888;margin-bottom:20px;font-size:0.9rem}.breadcrumb a{color:#64b5f6}
.timing{font-size:0.8rem;color:#666;margin-top:10px}
</style>
"""

private fun header(sourceId: Long? = null, sourceName: String? = null) = """
<header class="header"><div class="header-inner">
<a href="/browse" class="logo"><span style="font-size:2rem">📚</span><span>IReader Browser</span></a>
<nav>
<a href="/browse" class="${if(sourceId==null)"active" else ""}">🏠 Sources</a>
${if(sourceId!=null)"""<a href="/browse/$sourceId" class="active">📖 ${escHtml(sourceName?:"")}</a>""" else ""}
<a href="/">🔧 API Tester</a>
</nav>
${if(sourceId!=null)"""
<form class="search-form" action="/browse/$sourceId/search" method="get">
<input type="text" name="q" placeholder="Search...">
<button type="submit">Search</button>
</form>
""" else ""}
</div></header>
"""

private fun footer() = """<footer class="footer"><p>📚 IReader Source Browser - Real data from loaded sources</p></footer>"""
private fun escHtml(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
private fun langName(c: String) = when(c){"en"->"🇬🇧 English";"ar"->"🇸🇦 Arabic";"tu"->"🇹🇷 Turkish";"id","in"->"🇮🇩 Indonesian";"cn"->"🇨🇳 Chinese";else->"🌍 $c"}
private fun statusClass(s: Long) = when(s){MangaInfo.ONGOING->"ongoing";MangaInfo.COMPLETED->"completed";MangaInfo.ON_HIATUS->"on-hiatus";else->"unknown"}
private fun statusText(s: Long) = when(s){MangaInfo.ONGOING->"Ongoing";MangaInfo.COMPLETED->"Completed";MangaInfo.ON_HIATUS->"Hiatus";MangaInfo.LICENSED->"Licensed";MangaInfo.CANCELLED->"Cancelled";else->""}
private fun formatDate(ts: Long) = if(ts<=0)"" else try{java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()}catch(e:Exception){""}


private fun generateSourceListPage(): String {
    val sources = sourceManager.getAllSources().sortedBy { it.name }
    val byLang = sources.groupBy { it.lang }
    return """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>IReader Source Browser</title>${css()}${css2()}</head><body>${header()}
<main class="container">
<h2 class="section-title">📚 Available Sources (${sources.size})</h2>
${if(sources.isEmpty())"""<div class="error-box"><h3>No Sources Loaded</h3><p>Compile sources first: <code>./gradlew assembleDebug</code></p></div>"""
else byLang.entries.sortedBy{it.key}.joinToString(""){(lang,ls)->"""
<h3 style="margin:30px 0 15px;color:#888">${langName(lang)} (${ls.size})</h3>
<div class="source-grid">${ls.sortedBy{it.name}.joinToString(""){s->"""
<a href="/browse/${s.id}" class="source-card">
<div class="source-name">${escHtml(s.name)}</div>
<div class="source-meta">
<span>🌐 ${(s as? ireader.core.source.HttpSource)?.baseUrl?.replace("https://","")?.replace("http://","")?.take(30)?:"N/A"}</span>
<span>📋 ${s.getListings().size} listings</span>
</div></a>"""}}</div>"""}}
</main>${footer()}</body></html>"""
}

private fun novelCard(sourceId: Long, m: MangaInfo) = """
<div class="novel-card"><a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(m.key,"UTF-8")}">
<img class="novel-cover" src="${escHtml(m.cover)}" alt="" onerror="this.src='https://via.placeholder.com/180x250/2a2a4a/666?text=No+Cover'">
<div class="novel-info">
<h3 class="novel-title">${escHtml(m.title)}</h3>
<div class="novel-meta">
${if(m.status!=0L)"""<span class="status status-${statusClass(m.status)}">${statusText(m.status)}</span>""" else ""}
${if(m.author.isNotBlank())"""<span style="margin-left:5px">by ${escHtml(m.author.take(20))}</span>""" else ""}
</div></div></a></div>"""


private fun generateExplorePage(sourceId: Long?, page: Int): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "Source not found.")
    return try {
        val start = System.currentTimeMillis()
        val result = runBlocking { source.getMangaList(source.getListings().firstOrNull(), page) }
        val timing = System.currentTimeMillis() - start
        """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>${escHtml(source.name)} - IReader</title>${css()}${css2()}</head><body>${header(source.id, source.name)}
<main class="container">
<h2 class="section-title">📖 ${escHtml(source.name)} - Latest</h2>
<p style="color:#888;margin-bottom:20px">Page $page • ${result.mangas.size} novels</p>
<div class="novel-grid">${result.mangas.joinToString(""){novelCard(source.id,it)}}</div>
${if(result.mangas.isEmpty())"""<div class="error-box"><h3>No Results</h3><p>No novels found.</p></div>""" else ""}
<div class="pagination">
${if(page>1)"""<a href="/browse/$sourceId?page=${page-1}">« Prev</a>""" else ""}
<span class="current">Page $page</span>
${if(result.hasNextPage)"""<a href="/browse/$sourceId?page=${page+1}">Next »</a>""" else ""}
</div>
<p class="timing">⏱️ Loaded in ${timing}ms</p>
</main>${footer()}</body></html>"""
    } catch (e: Exception) { errorPage("Error", "Failed: ${escHtml(e.message?:"Unknown")}\n${escHtml(e.stackTraceToString().take(500))}") }
}

private fun generateSearchPage(sourceId: Long?, query: String, page: Int): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "Source not found.")
    if (query.isBlank()) return """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>Search - ${escHtml(source.name)}</title>${css()}${css2()}</head><body>${header(source.id, source.name)}
<main class="container">
<h2 class="section-title">🔍 Search in ${escHtml(source.name)}</h2>
<form class="search-form" action="/browse/$sourceId/search" method="get" style="max-width:500px;margin:30px 0">
<input type="text" name="q" placeholder="Enter search query..." style="flex:1">
<button type="submit">Search</button>
</form></main>${footer()}</body></html>"""
    return try {
        val start = System.currentTimeMillis()
        val filters = listOf(Filter.Title().apply { value = query })
        val result = runBlocking { source.getMangaList(filters, page) }
        val timing = System.currentTimeMillis() - start
        """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>Search: $query - ${escHtml(source.name)}</title>${css()}${css2()}</head><body>${header(source.id, source.name)}
<main class="container">
<h2 class="section-title">🔍 Search: "${escHtml(query)}"</h2>
<p style="color:#888;margin-bottom:20px">${result.mangas.size} result(s) • Page $page</p>
<div class="novel-grid">${result.mangas.joinToString(""){novelCard(source.id,it)}}</div>
${if(result.mangas.isEmpty())"""<div class="error-box"><h3>No Results</h3><p>No novels found for "${escHtml(query)}"</p></div>""" else ""}
<div class="pagination">
${if(page>1)"""<a href="/browse/$sourceId/search?q=${java.net.URLEncoder.encode(query,"UTF-8")}&page=${page-1}">« Prev</a>""" else ""}
<span class="current">Page $page</span>
${if(result.hasNextPage)"""<a href="/browse/$sourceId/search?q=${java.net.URLEncoder.encode(query,"UTF-8")}&page=${page+1}">Next »</a>""" else ""}
</div>
<p class="timing">⏱️ Loaded in ${timing}ms</p>
</main>${footer()}</body></html>"""
    } catch (e: Exception) { errorPage("Search Error", "Failed: ${escHtml(e.message?:"Unknown")}") }
}


private fun generateNovelDetailPage(sourceId: Long?, url: String): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "Source not found.")
    if (url.isBlank()) return errorPage("Invalid URL", "No novel URL provided.")
    return try {
        val start1 = System.currentTimeMillis()
        val manga = runBlocking { source.getMangaDetails(MangaInfo(key = url, title = ""), emptyList()) }
        val detailTime = System.currentTimeMillis() - start1
        val start2 = System.currentTimeMillis()
        val chapters = runBlocking { source.getChapterList(MangaInfo(key = url, title = ""), emptyList()) }
        val chapterTime = System.currentTimeMillis() - start2
        val detailCss = """<style>
.detail{display:grid;grid-template-columns:280px 1fr;gap:40px;margin-bottom:40px}@media(max-width:768px){.detail{grid-template-columns:1fr}}
.detail-cover{width:100%;border-radius:12px;box-shadow:0 10px 40px rgba(0,0,0,0.4)}
.detail-info h1{font-size:1.8rem;margin-bottom:15px;color:#fff}
.meta-row{display:flex;flex-wrap:wrap;gap:15px;margin-bottom:20px}
.meta-item{background:rgba(255,255,255,0.1);padding:8px 15px;border-radius:8px}
.meta-label{color:#888;font-size:0.8rem;display:block}.meta-value{color:#fff;font-weight:600}
.genre-tags{display:flex;flex-wrap:wrap;gap:10px;margin:20px 0}
.genre-tag{background:linear-gradient(135deg,#e94560,#ff6b6b);padding:6px 15px;border-radius:20px;font-size:0.85rem;color:#fff}
.desc{background:rgba(255,255,255,0.05);padding:25px;border-radius:12px;line-height:1.8;color:#ccc;max-height:300px;overflow-y:auto}
.btn{display:inline-block;margin:20px 0;padding:12px 30px;background:linear-gradient(135deg,#e94560,#ff6b6b);border-radius:25px;color:#fff;font-weight:bold}
.ch-list{list-style:none;max-height:600px;overflow-y:auto;background:rgba(0,0,0,0.2);border-radius:12px}
.ch-item{border-bottom:1px solid rgba(255,255,255,0.1)}.ch-item:last-child{border-bottom:none}
.ch-item a{display:flex;justify-content:space-between;padding:15px 20px;transition:background 0.2s;color:#fff}.ch-item a:hover{background:rgba(255,255,255,0.05)}
.ch-name{flex:1}.ch-date{color:#666;font-size:0.85rem}
</style>"""
        """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>${escHtml(manga.title)} - ${escHtml(source.name)}</title>${css()}${css2()}$detailCss</head><body>${header(source.id, source.name)}
<main class="container">
<div class="breadcrumb"><a href="/browse">Sources</a> › <a href="/browse/$sourceId">${escHtml(source.name)}</a> › ${escHtml(manga.title.take(50))}</div>
<div class="detail">
<div>
<img class="detail-cover" src="${escHtml(manga.cover)}" alt="" onerror="this.src='https://via.placeholder.com/280x400/2a2a4a/666?text=No+Cover'">
${if(chapters.isNotEmpty())"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(chapters.first().key,"UTF-8")}&novel=${java.net.URLEncoder.encode(url,"UTF-8")}" class="btn" style="display:block;text-align:center;margin-top:20px">📖 Start Reading</a>""" else ""}
</div>
<div class="detail-info">
<h1>${escHtml(manga.title)}</h1>
<div class="meta-row">
${if(manga.author.isNotBlank())"""<div class="meta-item"><span class="meta-label">Author</span><span class="meta-value">${escHtml(manga.author)}</span></div>""" else ""}
${if(manga.status!=0L)"""<div class="meta-item"><span class="meta-label">Status</span><span class="meta-value">${statusText(manga.status)}</span></div>""" else ""}
<div class="meta-item"><span class="meta-label">Chapters</span><span class="meta-value">${chapters.size}</span></div>
</div>
${if(manga.genres.isNotEmpty())"""<div class="genre-tags">${manga.genres.take(10).joinToString(""){"""<span class="genre-tag">${escHtml(it)}</span>"""}}</div>""" else ""}
${if(manga.description.isNotBlank())"""<div class="desc"><h3 style="margin-bottom:15px;color:#fff">📝 Synopsis</h3><p>${escHtml(manga.description)}</p></div>""" else ""}
</div></div>
<div style="margin-top:40px">
<h2 class="section-title">📚 Chapters (${chapters.size})</h2>
${if(chapters.isEmpty())"""<p style="color:#888">No chapters found</p>""" else """
<ul class="ch-list">${chapters.take(100).joinToString(""){ch->"""
<li class="ch-item"><a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(ch.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(url,"UTF-8")}">
<span class="ch-name">${escHtml(ch.name)}</span>
${if(ch.dateUpload>0)"""<span class="ch-date">${formatDate(ch.dateUpload)}</span>""" else ""}
</a></li>"""}}</ul>
${if(chapters.size>100)"""<p style="color:#888;margin-top:15px">Showing first 100 of ${chapters.size} chapters</p>""" else ""}"""}
</div>
<p class="timing">⏱️ Details: ${detailTime}ms • Chapters: ${chapterTime}ms</p>
</main>${footer()}</body></html>"""
    } catch (e: Exception) { errorPage("Error Loading Novel", "Failed: ${escHtml(e.message?:"Unknown")}\n${escHtml(e.stackTraceToString().take(800))}") }
}


private fun generateChapterContentPage(sourceId: Long?, chapterUrl: String, novelUrl: String): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "Source not found.")
    if (chapterUrl.isBlank()) return errorPage("Invalid URL", "No chapter URL provided.")
    return try {
        val start = System.currentTimeMillis()
        val pages = runBlocking { source.getPageList(ChapterInfo(key = chapterUrl, name = ""), emptyList()) }
        val contentTime = System.currentTimeMillis() - start
        var chapters: List<ChapterInfo> = emptyList()
        if (novelUrl.isNotBlank()) { try { chapters = runBlocking { source.getChapterList(MangaInfo(key = novelUrl, title = ""), emptyList()) } } catch (e: Exception) {} }
        val currentIndex = chapters.indexOfFirst { it.key == chapterUrl }
        val prevChapter = if (currentIndex > 0) chapters.getOrNull(currentIndex - 1) else null
        val nextChapter = if (currentIndex >= 0) chapters.getOrNull(currentIndex + 1) else null
        val currentChapter = chapters.getOrNull(currentIndex)
        val content = pages.mapNotNull { p -> when (p) { is Text -> p.text; else -> null } }
        val readerCss = """<style>
.reader{max-width:800px;margin:0 auto}
.ch-header{text-align:center;margin-bottom:40px;padding-bottom:30px;border-bottom:1px solid rgba(255,255,255,0.1)}
.ch-header h1{font-size:1.2rem;color:#888;margin-bottom:10px}.ch-header h2{font-size:1.5rem;color:#fff}
.ch-nav{display:flex;justify-content:space-between;gap:15px;margin:30px 0}
.ch-nav a{flex:1;text-align:center;padding:15px;background:rgba(255,255,255,0.1);border-radius:8px;color:#fff;transition:all 0.2s}
.ch-nav a:hover{background:#e94560}
.ch-nav .disabled{opacity:0.3;pointer-events:none;background:rgba(255,255,255,0.05)}
.ch-content{background:rgba(255,255,255,0.03);padding:40px;border-radius:12px;line-height:2;font-size:1.1rem;color:#ddd}
.ch-content p{margin-bottom:1.5em}
</style>"""
        """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>${escHtml(currentChapter?.name?:"Chapter")} - ${escHtml(source.name)}</title>${css()}${css2()}$readerCss</head><body>${header(source.id, source.name)}
<main class="container reader">
<div class="breadcrumb">
<a href="/browse">Sources</a> › <a href="/browse/$sourceId">${escHtml(source.name)}</a>
${if(novelUrl.isNotBlank())""" › <a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">Novel</a>""" else ""} › Chapter
</div>
<article>
<header class="ch-header"><h1>Chapter</h1><h2>${escHtml(currentChapter?.name?:"Reading")}</h2></header>
<nav class="ch-nav">
${prevChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">« Previous</a>"""}?:"""<span class="disabled">« Previous</span>"""}
${if(novelUrl.isNotBlank())"""<a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">📚 Chapters</a>""" else """<span class="disabled">📚 Chapters</span>"""}
${nextChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">Next »</a>"""}?:"""<span class="disabled">Next »</span>"""}
</nav>
<div class="ch-content">
${if(content.isEmpty())"""<p style="color:#888;text-align:center">No content found</p>""" else content.joinToString("\n"){"""<p>${escHtml(it)}</p>"""}}
</div>
<nav class="ch-nav">
${prevChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">« Previous</a>"""}?:"""<span class="disabled">« Previous</span>"""}
${if(novelUrl.isNotBlank())"""<a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">📚 Chapters</a>""" else """<span class="disabled">📚 Chapters</span>"""}
${nextChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">Next »</a>"""}?:"""<span class="disabled">Next »</span>"""}
</nav>
</article>
<p class="timing">⏱️ Content loaded in ${contentTime}ms • ${content.size} paragraphs</p>
</main>${footer()}</body></html>"""
    } catch (e: Exception) { errorPage("Error Loading Chapter", "Failed: ${escHtml(e.message?:"Unknown")}\n${escHtml(e.stackTraceToString().take(800))}") }
}

private fun errorPage(title: String, message: String) = """<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>$title - IReader</title>${css()}${css2()}</head><body>${header()}
<main class="container">
<div class="error-box">
<h3>❌ $title</h3>
<p style="white-space:pre-wrap;text-align:left;margin-top:15px;font-family:monospace;font-size:0.9rem">${escHtml(message)}</p>
<a href="/browse" class="btn" style="margin-top:20px;display:inline-block">← Back to Sources</a>
</div>
</main>${footer()}</body></html>"""
