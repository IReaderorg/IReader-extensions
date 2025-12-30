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

private fun baseStyles() = """
<style>
    :root {
        --bg-gradient-1: #0f0c29;
        --bg-gradient-2: #302b63;
        --bg-gradient-3: #24243e;
        --glass-bg: rgba(255, 255, 255, 0.05);
        --glass-border: rgba(255, 255, 255, 0.1);
        --glass-hover: rgba(255, 255, 255, 0.1);
        --accent: #6366f1;
        --accent-light: #818cf8;
        --accent-glow: rgba(99, 102, 241, 0.4);
        --success: #10b981;
        --error: #ef4444;
        --text-primary: #f8fafc;
        --text-secondary: #94a3b8;
        --text-muted: #64748b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
        font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
        background: linear-gradient(135deg, var(--bg-gradient-1) 0%, var(--bg-gradient-2) 50%, var(--bg-gradient-3) 100%);
        background-attachment: fixed;
        color: var(--text-primary);
        min-height: 100vh;
        line-height: 1.6;
    }
    ::-webkit-scrollbar { width: 8px; height: 8px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: var(--glass-border); border-radius: 4px; }
    ::-webkit-scrollbar-thumb:hover { background: var(--text-muted); }
    a { color: var(--accent-light); text-decoration: none; transition: color 0.2s; }
    a:hover { color: var(--text-primary); }
    .glass {
        background: var(--glass-bg);
        backdrop-filter: blur(20px);
        -webkit-backdrop-filter: blur(20px);
        border: 1px solid var(--glass-border);
        border-radius: 16px;
    }
    .container { max-width: 1400px; margin: 0 auto; padding: 30px 24px; }
</style>
"""

private fun headerStyles() = """
<style>
    header {
        background: var(--glass-bg);
        backdrop-filter: blur(20px);
        border-bottom: 1px solid var(--glass-border);
        position: sticky;
        top: 0;
        z-index: 100;
    }
    .header-inner {
        max-width: 1400px;
        margin: 0 auto;
        padding: 16px 24px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 20px;
        flex-wrap: wrap;
    }
    .logo {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 1.4rem;
        font-weight: 700;
        color: var(--text-primary);
        text-decoration: none;
    }
    .logo-icon {
        width: 42px;
        height: 42px;
        background: linear-gradient(135deg, var(--accent), #8b5cf6);
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 1.3rem;
    }
    .logo-text {
        background: linear-gradient(135deg, var(--accent-light), #a78bfa);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
    }
    nav { display: flex; gap: 10px; flex-wrap: wrap; }
    .nav-btn {
        padding: 10px 20px;
        border-radius: 10px;
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        color: var(--text-secondary);
        font-weight: 500;
        font-size: 0.9rem;
        transition: all 0.3s ease;
        display: flex;
        align-items: center;
        gap: 8px;
        text-decoration: none;
    }
    .nav-btn:hover { background: var(--glass-hover); color: var(--text-primary); border-color: var(--accent); }
    .nav-btn.active { background: var(--accent); color: white; border-color: var(--accent); }
    .search-form { display: flex; gap: 10px; }
    .search-form input {
        padding: 10px 20px;
        border: 1px solid var(--glass-border);
        border-radius: 25px;
        background: rgba(0, 0, 0, 0.2);
        color: var(--text-primary);
        width: 220px;
        font-size: 0.9rem;
        transition: all 0.2s;
    }
    .search-form input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 20px var(--accent-glow); }
    .search-form input::placeholder { color: var(--text-muted); }
    .search-form button {
        padding: 10px 24px;
        border: none;
        border-radius: 25px;
        background: linear-gradient(135deg, var(--accent), #8b5cf6);
        color: white;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.3s;
    }
    .search-form button:hover { transform: translateY(-2px); box-shadow: 0 10px 30px var(--accent-glow); }
</style>
"""

private fun componentStyles() = """
<style>
    .section-title {
        font-size: 1.5rem;
        font-weight: 700;
        margin-bottom: 24px;
        display: flex;
        align-items: center;
        gap: 12px;
    }
    .section-title::before {
        content: '';
        width: 4px;
        height: 28px;
        background: linear-gradient(180deg, var(--accent), #8b5cf6);
        border-radius: 2px;
    }
    .source-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: 16px;
    }
    .source-card {
        padding: 20px;
        transition: all 0.3s ease;
        text-decoration: none;
        color: inherit;
    }
    .source-card:hover { transform: translateY(-3px); box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3); border-color: var(--accent); }
    .source-name { font-size: 1.1rem; font-weight: 600; margin-bottom: 8px; color: var(--text-primary); }
    .source-meta { font-size: 0.85rem; color: var(--text-muted); display: flex; gap: 16px; flex-wrap: wrap; }
    .lang-section { margin-bottom: 40px; }
    .lang-title {
        font-size: 1rem;
        font-weight: 600;
        color: var(--text-muted);
        margin-bottom: 16px;
        padding-bottom: 8px;
        border-bottom: 1px solid var(--glass-border);
    }
    .novel-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
        gap: 24px;
    }
    .novel-card {
        border-radius: 16px;
        overflow: hidden;
        transition: all 0.3s ease;
        text-decoration: none;
        color: inherit;
        display: block;
    }
    .novel-card:hover { transform: translateY(-8px); box-shadow: 0 25px 50px rgba(0, 0, 0, 0.4); }
    .novel-card:hover .novel-cover { transform: scale(1.05); }
    .novel-cover-wrap { overflow: hidden; position: relative; }
    .novel-cover {
        width: 100%;
        height: 260px;
        object-fit: cover;
        background: linear-gradient(135deg, var(--bg-gradient-2), var(--bg-gradient-3));
        transition: transform 0.3s ease;
    }
    .novel-info { padding: 16px; background: var(--glass-bg); }
    .novel-title {
        font-size: 0.95rem;
        font-weight: 600;
        margin-bottom: 8px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
        min-height: 2.8em;
        color: var(--text-primary);
    }
    .novel-meta { font-size: 0.8rem; color: var(--text-muted); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .status-badge {
        display: inline-flex;
        align-items: center;
        padding: 3px 10px;
        border-radius: 6px;
        font-size: 0.7rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }
    .status-ongoing { background: rgba(59, 130, 246, 0.2); color: #60a5fa; }
    .status-completed { background: rgba(16, 185, 129, 0.2); color: #34d399; }
    .status-hiatus { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
</style>
"""

private fun paginationStyles() = """
<style>
    .pagination {
        display: flex;
        justify-content: center;
        gap: 10px;
        margin-top: 50px;
        flex-wrap: wrap;
    }
    .pagination a, .pagination span {
        padding: 12px 20px;
        border-radius: 10px;
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        color: var(--text-primary);
        font-weight: 500;
        transition: all 0.2s;
    }
    .pagination a:hover { background: var(--accent); border-color: var(--accent); }
    .pagination .current { background: var(--accent); border-color: var(--accent); }
    .breadcrumb {
        color: var(--text-muted);
        margin-bottom: 24px;
        font-size: 0.9rem;
        display: flex;
        align-items: center;
        gap: 8px;
        flex-wrap: wrap;
    }
    .breadcrumb a { color: var(--accent-light); }
    .breadcrumb span { color: var(--text-muted); }
    .timing {
        font-size: 0.8rem;
        color: var(--text-muted);
        margin-top: 20px;
        padding: 12px 16px;
        background: var(--glass-bg);
        border-radius: 8px;
        display: inline-block;
    }
    .error-box {
        background: rgba(239, 68, 68, 0.1);
        border: 1px solid rgba(239, 68, 68, 0.3);
        border-radius: 16px;
        padding: 40px;
        text-align: center;
    }
    .error-box h3 { color: var(--error); margin-bottom: 16px; font-size: 1.3rem; }
    .error-box p { color: var(--text-secondary); }
    .error-box pre { text-align: left; margin-top: 20px; font-size: 0.85rem; color: var(--text-muted); white-space: pre-wrap; }
    .btn {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 14px 28px;
        border-radius: 12px;
        background: linear-gradient(135deg, var(--accent), #8b5cf6);
        color: white;
        font-weight: 600;
        text-decoration: none;
        transition: all 0.3s;
    }
    .btn:hover { transform: translateY(-2px); box-shadow: 0 10px 30px var(--accent-glow); color: white; }
    footer {
        background: var(--glass-bg);
        border-top: 1px solid var(--glass-border);
        padding: 30px 0;
        margin-top: 60px;
        text-align: center;
        color: var(--text-muted);
        font-size: 0.9rem;
    }
</style>
"""

private fun detailStyles() = """
<style>
    .detail-grid { display: grid; grid-template-columns: 280px 1fr; gap: 40px; margin-bottom: 40px; }
    @media (max-width: 900px) { .detail-grid { grid-template-columns: 1fr; } }
    .detail-cover {
        width: 100%;
        border-radius: 16px;
        box-shadow: 0 25px 50px rgba(0, 0, 0, 0.5);
        aspect-ratio: 2/3;
        object-fit: cover;
        background: linear-gradient(135deg, var(--bg-gradient-2), var(--bg-gradient-3));
    }
    .detail-sidebar { display: flex; flex-direction: column; gap: 16px; }
    .detail-info h1 { font-size: 2rem; margin-bottom: 16px; line-height: 1.3; }
    .meta-grid { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 24px; }
    .meta-item {
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        padding: 12px 18px;
        border-radius: 12px;
    }
    .meta-label { color: var(--text-muted); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 4px; }
    .meta-value { color: var(--text-primary); font-weight: 600; }
    .genre-tags { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; }
    .genre-tag {
        background: linear-gradient(135deg, var(--accent), #8b5cf6);
        padding: 8px 18px;
        border-radius: 25px;
        font-size: 0.85rem;
        color: white;
        font-weight: 500;
    }
    .description-box {
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        padding: 24px;
        border-radius: 16px;
        line-height: 1.9;
        color: var(--text-secondary);
        max-height: 300px;
        overflow-y: auto;
    }
    .description-box h4 { color: var(--text-primary); margin-bottom: 16px; font-size: 1rem; }
    .chapter-section { margin-top: 50px; }
    .chapter-list {
        list-style: none;
        max-height: 600px;
        overflow-y: auto;
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        border-radius: 16px;
    }
    .chapter-item { border-bottom: 1px solid var(--glass-border); }
    .chapter-item:last-child { border-bottom: none; }
    .chapter-item a {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 18px 24px;
        transition: all 0.2s;
        color: var(--text-primary);
        text-decoration: none;
    }
    .chapter-item a:hover { background: var(--glass-hover); }
    .chapter-name { font-weight: 500; }
    .chapter-date { color: var(--text-muted); font-size: 0.85rem; }
</style>
"""

private fun readerStyles() = """
<style>
    .reader-container { max-width: 850px; margin: 0 auto; }
    .chapter-header {
        text-align: center;
        margin-bottom: 40px;
        padding-bottom: 30px;
        border-bottom: 1px solid var(--glass-border);
    }
    .chapter-header .subtitle { color: var(--text-muted); font-size: 0.9rem; margin-bottom: 8px; }
    .chapter-header h1 { font-size: 1.6rem; color: var(--text-primary); }
    .chapter-nav {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        margin: 30px 0;
    }
    .chapter-nav a, .chapter-nav .disabled {
        flex: 1;
        text-align: center;
        padding: 16px 20px;
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        border-radius: 12px;
        color: var(--text-primary);
        font-weight: 500;
        transition: all 0.2s;
        text-decoration: none;
    }
    .chapter-nav a:hover { background: var(--accent); border-color: var(--accent); }
    .chapter-nav .disabled { opacity: 0.3; cursor: not-allowed; }
    .chapter-content {
        background: var(--glass-bg);
        border: 1px solid var(--glass-border);
        padding: 50px;
        border-radius: 20px;
        line-height: 2.1;
        font-size: 1.15rem;
    }
    .chapter-content p { margin-bottom: 1.5em; color: var(--text-secondary); }
    .chapter-content p:last-child { margin-bottom: 0; }
    .empty-content { text-align: center; color: var(--text-muted); padding: 60px; }
</style>
"""

private fun header(sourceId: Long? = null, sourceName: String? = null) = """
<header>
    <div class="header-inner">
        <a href="/browse" class="logo">
            <div class="logo-icon">📚</div>
            <span class="logo-text">IReader Browser</span>
        </a>
        <nav>
            <a href="/browse" class="nav-btn ${if(sourceId==null)"active" else ""}">🏠 Sources</a>
            ${if(sourceId!=null)"""<a href="/browse/$sourceId" class="nav-btn active">📖 ${escHtml(sourceName?:"")}</a>""" else ""}
            <a href="/" class="nav-btn">🔧 API Tester</a>
        </nav>
        ${if(sourceId!=null)"""
        <form class="search-form" action="/browse/$sourceId/search" method="get">
            <input type="text" name="q" placeholder="Search novels...">
            <button type="submit">Search</button>
        </form>
        """ else ""}
    </div>
</header>
"""

private fun footer() = """<footer><p>📚 IReader Source Browser • Real data from ${sourceManager.getAllSources().size} loaded sources</p></footer>"""
private fun escHtml(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
private fun langName(c: String) = when(c){"en"->"🇬🇧 English";"ar"->"🇸🇦 Arabic";"tu"->"🇹🇷 Turkish";"id","in"->"🇮🇩 Indonesian";"cn"->"🇨🇳 Chinese";"es"->"🇪🇸 Spanish";"fr"->"🇫🇷 French";"de"->"🇩🇪 German";"pt"->"🇵🇹 Portuguese";"ru"->"🇷🇺 Russian";"ja"->"🇯🇵 Japanese";"ko"->"🇰🇷 Korean";else->"🌍 ${c.uppercase()}"}
private fun statusClass(s: Long) = when(s){MangaInfo.ONGOING->"ongoing";MangaInfo.COMPLETED->"completed";MangaInfo.ON_HIATUS->"hiatus";else->""}
private fun statusText(s: Long) = when(s){MangaInfo.ONGOING->"Ongoing";MangaInfo.COMPLETED->"Completed";MangaInfo.ON_HIATUS->"Hiatus";MangaInfo.LICENSED->"Licensed";MangaInfo.CANCELLED->"Cancelled";else->""}
private fun formatDate(ts: Long) = if(ts<=0)"" else try{java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()}catch(e:Exception){""}

private fun htmlHead(title: String) = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title - IReader</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    ${baseStyles()}${headerStyles()}${componentStyles()}${paginationStyles()}${detailStyles()}${readerStyles()}
</head>
<body>
"""

private fun generateSourceListPage(): String {
    val sources = sourceManager.getAllSources().sortedBy { it.name }
    val byLang = sources.groupBy { it.lang }
    return """${htmlHead("Source Browser")}${header()}
<main class="container">
    <h2 class="section-title">Available Sources (${sources.size})</h2>
    
    <div style="margin-bottom: 30px;">
        <input type="text" id="sourceSearch" placeholder="🔍 Search sources by name, language, or URL..." 
               style="width: 100%; max-width: 500px; padding: 14px 20px; border: 1px solid var(--glass-border); border-radius: 12px; background: rgba(0,0,0,0.2); color: var(--text-primary); font-size: 1rem;"
               oninput="filterSources()">
    </div>
    
    ${if(sources.isEmpty())"""
    <div class="error-box">
        <h3>No Sources Loaded</h3>
        <p>Compile sources first with: <code>./gradlew assembleDebug</code></p>
        <a href="/" class="btn" style="margin-top: 20px;">Go to API Tester</a>
    </div>
    """ else """
    <div id="sourceContainer">
    ${byLang.entries.sortedBy{it.key}.joinToString(""){(lang,ls)->"""
    <div class="lang-section" data-lang="${lang}">
        <h3 class="lang-title">${langName(lang)} (<span class="lang-count">${ls.size}</span>)</h3>
        <div class="source-grid">
            ${ls.sortedBy{it.name}.joinToString(""){s->"""
            <a href="/browse/${s.id}" class="source-card glass" data-name="${escHtml(s.name.lowercase())}" data-lang="${lang}" data-url="${(s as? ireader.core.source.HttpSource)?.baseUrl?.lowercase()?:""}">
                <div class="source-name">${escHtml(s.name)}</div>
                <div class="source-meta">
                    <span>🌐 ${(s as? ireader.core.source.HttpSource)?.baseUrl?.replace("https://","")?.replace("http://","")?.take(35)?:"N/A"}</span>
                    <span>📋 ${s.getListings().size} listings</span>
                </div>
            </a>
            """}}
        </div>
    </div>
    """}}
    </div>
    <div id="noResults" style="display: none;" class="error-box">
        <h3>No Matching Sources</h3>
        <p>Try a different search term</p>
    </div>
    """}
</main>
<script>
function filterSources() {
    const query = document.getElementById('sourceSearch').value.toLowerCase().trim();
    const cards = document.querySelectorAll('.source-card');
    const sections = document.querySelectorAll('.lang-section');
    let totalVisible = 0;
    
    sections.forEach(function(section) {
        let visibleInSection = 0;
        const sectionCards = section.querySelectorAll('.source-card');
        sectionCards.forEach(function(card) {
            const name = card.dataset.name || '';
            const lang = card.dataset.lang || '';
            const url = card.dataset.url || '';
            const matches = !query || name.includes(query) || lang.includes(query) || url.includes(query);
            card.style.display = matches ? '' : 'none';
            if (matches) visibleInSection++;
        });
        section.style.display = visibleInSection > 0 ? '' : 'none';
        section.querySelector('.lang-count').textContent = visibleInSection;
        totalVisible += visibleInSection;
    });
    
    document.getElementById('noResults').style.display = totalVisible === 0 ? 'block' : 'none';
    document.getElementById('sourceContainer').style.display = totalVisible === 0 ? 'none' : 'block';
}
</script>
${footer()}
</body></html>"""
}

private fun novelCard(sourceId: Long, m: MangaInfo) = """
<a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(m.key,"UTF-8")}" class="novel-card glass">
    <div class="novel-cover-wrap">
        <img class="novel-cover" src="${escHtml(m.cover)}" alt="" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 150%22><rect fill=%22%23302b63%22 width=%22100%22 height=%22150%22/><text x=%2250%22 y=%2280%22 text-anchor=%22middle%22 fill=%22%2364748b%22 font-size=%228%22>No Cover</text></svg>'">
    </div>
    <div class="novel-info">
        <h3 class="novel-title">${escHtml(m.title)}</h3>
        <div class="novel-meta">
            ${if(m.status!=0L)"""<span class="status-badge status-${statusClass(m.status)}">${statusText(m.status)}</span>""" else ""}
            ${if(m.author.isNotBlank())"""<span>${escHtml(m.author.take(20))}</span>""" else ""}
        </div>
    </div>
</a>
"""

private fun generateExplorePage(sourceId: Long?, page: Int): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "The requested source could not be found.")
    return try {
        val start = System.currentTimeMillis()
        val result = runBlocking { source.getMangaList(source.getListings().firstOrNull(), page) }
        val timing = System.currentTimeMillis() - start
        """${htmlHead(source.name)}${header(source.id, source.name)}
<main class="container">
    <h2 class="section-title">${escHtml(source.name)} - Latest</h2>
    <p style="color: var(--text-muted); margin-bottom: 30px;">Page $page • ${result.mangas.size} novels found</p>
    
    <div class="novel-grid">
        ${result.mangas.joinToString(""){novelCard(source.id,it)}}
    </div>
    
    ${if(result.mangas.isEmpty())"""
    <div class="error-box">
        <h3>No Results</h3>
        <p>No novels found on this page.</p>
    </div>
    """ else ""}
    
    <div class="pagination">
        ${if(page>1)"""<a href="/browse/$sourceId?page=${page-1}">« Previous</a>""" else ""}
        <span class="current">Page $page</span>
        ${if(result.hasNextPage)"""<a href="/browse/$sourceId?page=${page+1}">Next »</a>""" else ""}
    </div>
    
    <div class="timing">⏱️ Loaded in ${timing}ms</div>
</main>
${footer()}
</body></html>"""
    } catch (e: Exception) { errorPage("Error Loading Source", "Failed to load novels: ${escHtml(e.message?:"Unknown error")}\n\n${escHtml(e.stackTraceToString().take(600))}") }
}

private fun generateSearchPage(sourceId: Long?, query: String, page: Int): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "The requested source could not be found.")
    if (query.isBlank()) return """${htmlHead("Search - ${source.name}")}${header(source.id, source.name)}
<main class="container">
    <h2 class="section-title">Search in ${escHtml(source.name)}</h2>
    <div class="glass" style="padding: 40px; max-width: 600px;">
        <form class="search-form" action="/browse/$sourceId/search" method="get" style="flex-direction: column; gap: 16px;">
            <input type="text" name="q" placeholder="Enter your search query..." style="width: 100%; padding: 16px 24px; font-size: 1rem;">
            <button type="submit" style="width: 100%; padding: 16px;">Search</button>
        </form>
    </div>
</main>
${footer()}
</body></html>"""
    
    return try {
        val start = System.currentTimeMillis()
        val filters = listOf(Filter.Title().apply { value = query })
        val result = runBlocking { source.getMangaList(filters, page) }
        val timing = System.currentTimeMillis() - start
        """${htmlHead("Search: $query - ${source.name}")}${header(source.id, source.name)}
<main class="container">
    <h2 class="section-title">Search: "${escHtml(query)}"</h2>
    <p style="color: var(--text-muted); margin-bottom: 30px;">${result.mangas.size} result(s) • Page $page</p>
    
    <div class="novel-grid">
        ${result.mangas.joinToString(""){novelCard(source.id,it)}}
    </div>
    
    ${if(result.mangas.isEmpty())"""
    <div class="error-box">
        <h3>No Results</h3>
        <p>No novels found for "${escHtml(query)}"</p>
        <a href="/browse/$sourceId" class="btn" style="margin-top: 20px;">Browse All</a>
    </div>
    """ else ""}
    
    <div class="pagination">
        ${if(page>1)"""<a href="/browse/$sourceId/search?q=${java.net.URLEncoder.encode(query,"UTF-8")}&page=${page-1}">« Previous</a>""" else ""}
        <span class="current">Page $page</span>
        ${if(result.hasNextPage)"""<a href="/browse/$sourceId/search?q=${java.net.URLEncoder.encode(query,"UTF-8")}&page=${page+1}">Next »</a>""" else ""}
    </div>
    
    <div class="timing">⏱️ Loaded in ${timing}ms</div>
</main>
${footer()}
</body></html>"""
    } catch (e: Exception) { errorPage("Search Error", "Failed to search: ${escHtml(e.message?:"Unknown error")}") }
}

private fun generateNovelDetailPage(sourceId: Long?, url: String): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "The requested source could not be found.")
    if (url.isBlank()) return errorPage("Invalid URL", "No novel URL was provided.")
    return try {
        val start1 = System.currentTimeMillis()
        val manga = runBlocking { source.getMangaDetails(MangaInfo(key = url, title = ""), emptyList()) }
        val detailTime = System.currentTimeMillis() - start1
        val start2 = System.currentTimeMillis()
        val chapters = runBlocking { source.getChapterList(MangaInfo(key = url, title = ""), emptyList()) }
        val chapterTime = System.currentTimeMillis() - start2
        
        """${htmlHead(manga.title)}${header(source.id, source.name)}
<main class="container">
    <div class="breadcrumb">
        <a href="/browse">Sources</a>
        <span>›</span>
        <a href="/browse/$sourceId">${escHtml(source.name)}</a>
        <span>›</span>
        <span>${escHtml(manga.title.take(50))}</span>
    </div>
    
    <div class="detail-grid">
        <div class="detail-sidebar">
            <img class="detail-cover" src="${escHtml(manga.cover)}" alt="" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 150%22><rect fill=%22%23302b63%22 width=%22100%22 height=%22150%22/><text x=%2250%22 y=%2280%22 text-anchor=%22middle%22 fill=%22%2364748b%22 font-size=%228%22>No Cover</text></svg>'">
            ${if(chapters.isNotEmpty())"""
            <a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(chapters.first().key,"UTF-8")}&novel=${java.net.URLEncoder.encode(url,"UTF-8")}" class="btn" style="justify-content: center;">
                📖 Start Reading
            </a>
            """ else ""}
        </div>
        
        <div class="detail-info">
            <h1>${escHtml(manga.title)}</h1>
            
            <div class="meta-grid">
                ${if(manga.author.isNotBlank())"""
                <div class="meta-item">
                    <div class="meta-label">Author</div>
                    <div class="meta-value">${escHtml(manga.author)}</div>
                </div>
                """ else ""}
                ${if(manga.status!=0L)"""
                <div class="meta-item">
                    <div class="meta-label">Status</div>
                    <div class="meta-value">${statusText(manga.status)}</div>
                </div>
                """ else ""}
                <div class="meta-item">
                    <div class="meta-label">Chapters</div>
                    <div class="meta-value">${chapters.size}</div>
                </div>
            </div>
            
            ${if(manga.genres.isNotEmpty())"""
            <div class="genre-tags">
                ${manga.genres.take(12).joinToString(""){"""<span class="genre-tag">${escHtml(it)}</span>"""}}
            </div>
            """ else ""}
            
            ${if(manga.description.isNotBlank())"""
            <div class="description-box">
                <h4>📝 Synopsis</h4>
                <p>${escHtml(manga.description)}</p>
            </div>
            """ else ""}
        </div>
    </div>
    
    <div class="chapter-section">
        <h2 class="section-title">Chapters (${chapters.size})</h2>
        ${if(chapters.isEmpty())"""
        <p style="color: var(--text-muted);">No chapters found</p>
        """ else """
        <ul class="chapter-list">
            ${chapters.take(150).joinToString(""){ch->"""
            <li class="chapter-item">
                <a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(ch.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(url,"UTF-8")}">
                    <span class="chapter-name">${escHtml(ch.name)}</span>
                    ${if(ch.dateUpload>0)"""<span class="chapter-date">${formatDate(ch.dateUpload)}</span>""" else ""}
                </a>
            </li>
            """}}
        </ul>
        ${if(chapters.size>150)"""<p style="color: var(--text-muted); margin-top: 16px; text-align: center;">Showing first 150 of ${chapters.size} chapters</p>""" else ""}
        """}
    </div>
    
    <div class="timing">⏱️ Details: ${detailTime}ms • Chapters: ${chapterTime}ms</div>
</main>
${footer()}
</body></html>"""
    } catch (e: Exception) { errorPage("Error Loading Novel", "Failed to load novel details: ${escHtml(e.message?:"Unknown error")}\n\n${escHtml(e.stackTraceToString().take(800))}") }
}

private fun generateChapterContentPage(sourceId: Long?, chapterUrl: String, novelUrl: String): String {
    val source = sourceId?.let { sourceManager.getSource(it) } ?: return errorPage("Source Not Found", "The requested source could not be found.")
    if (chapterUrl.isBlank()) return errorPage("Invalid URL", "No chapter URL was provided.")
    return try {
        val start = System.currentTimeMillis()
        val pages = runBlocking { source.getPageList(ChapterInfo(key = chapterUrl, name = ""), emptyList()) }
        val contentTime = System.currentTimeMillis() - start
        
        var chapters: List<ChapterInfo> = emptyList()
        if (novelUrl.isNotBlank()) { 
            try { chapters = runBlocking { source.getChapterList(MangaInfo(key = novelUrl, title = ""), emptyList()) } } 
            catch (e: Exception) {} 
        }
        
        val currentIndex = chapters.indexOfFirst { it.key == chapterUrl }
        val prevChapter = if (currentIndex > 0) chapters.getOrNull(currentIndex - 1) else null
        val nextChapter = if (currentIndex >= 0) chapters.getOrNull(currentIndex + 1) else null
        val currentChapter = chapters.getOrNull(currentIndex)
        val content = pages.mapNotNull { p -> when (p) { is Text -> p.text; else -> null } }
        
        """${htmlHead(currentChapter?.name ?: "Reading")}${header(source.id, source.name)}
<main class="container reader-container">
    <div class="breadcrumb">
        <a href="/browse">Sources</a>
        <span>›</span>
        <a href="/browse/$sourceId">${escHtml(source.name)}</a>
        ${if(novelUrl.isNotBlank())"""
        <span>›</span>
        <a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">Novel</a>
        """ else ""}
        <span>›</span>
        <span>Chapter</span>
    </div>
    
    <article>
        <header class="chapter-header">
            <p class="subtitle">Chapter</p>
            <h1>${escHtml(currentChapter?.name ?: "Reading")}</h1>
        </header>
        
        <nav class="chapter-nav">
            ${prevChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">« Previous</a>"""}?:"""<span class="disabled">« Previous</span>"""}
            ${if(novelUrl.isNotBlank())"""<a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">📚 Chapters</a>""" else """<span class="disabled">📚 Chapters</span>"""}
            ${nextChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">Next »</a>"""}?:"""<span class="disabled">Next »</span>"""}
        </nav>
        
        <div class="chapter-content">
            ${if(content.isEmpty())"""<div class="empty-content">No content found</div>""" 
              else content.joinToString("\n"){"""<p>${escHtml(it)}</p>"""}}
        </div>
        
        <nav class="chapter-nav">
            ${prevChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">« Previous</a>"""}?:"""<span class="disabled">« Previous</span>"""}
            ${if(novelUrl.isNotBlank())"""<a href="/browse/$sourceId/novel?url=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">📚 Chapters</a>""" else """<span class="disabled">📚 Chapters</span>"""}
            ${nextChapter?.let{"""<a href="/browse/$sourceId/read?url=${java.net.URLEncoder.encode(it.key,"UTF-8")}&novel=${java.net.URLEncoder.encode(novelUrl,"UTF-8")}">Next »</a>"""}?:"""<span class="disabled">Next »</span>"""}
        </nav>
    </article>
    
    <div class="timing">⏱️ Content loaded in ${contentTime}ms • ${content.size} paragraphs</div>
</main>
${footer()}
</body></html>"""
    } catch (e: Exception) { errorPage("Error Loading Chapter", "Failed to load chapter content: ${escHtml(e.message?:"Unknown error")}\n\n${escHtml(e.stackTraceToString().take(800))}") }
}

private fun errorPage(title: String, message: String) = """${htmlHead(title)}${header()}
<main class="container">
    <div class="error-box">
        <h3>❌ $title</h3>
        <p>${escHtml(message.substringBefore("\n"))}</p>
        ${if(message.contains("\n"))"""<pre>${escHtml(message.substringAfter("\n"))}</pre>""" else ""}
        <a href="/browse" class="btn" style="margin-top: 24px;">← Back to Sources</a>
    </div>
</main>
${footer()}
</body></html>"""
