package ireader.testserver

/**
 * Returns the HTML UI for the test server
 */
fun getIndexHtml(): String {
    // Use triple-dollar to escape JavaScript template literals
    val dollar = "$"
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IReader Source Test Server</title>
    <style>
        :root {
            --bg-primary: #1a1a2e;
            --bg-secondary: #16213e;
            --bg-card: #0f3460;
            --accent: #e94560;
            --accent-hover: #ff6b6b;
            --text-primary: #eaeaea;
            --text-secondary: #a0a0a0;
            --success: #4ade80;
            --error: #f87171;
            --border: #2d3748;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg-primary);
            color: var(--text-primary);
            min-height: 100vh;
        }
        .container { max-width: 1400px; margin: 0 auto; padding: 20px; }
        header {
            background: var(--bg-secondary);
            padding: 20px;
            border-bottom: 2px solid var(--accent);
            margin-bottom: 20px;
        }
        header h1 { font-size: 1.8rem; }
        .layout { display: grid; grid-template-columns: 300px 1fr; gap: 20px; }
        @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
        .sidebar {
            background: var(--bg-secondary);
            border-radius: 8px;
            padding: 15px;
            height: fit-content;
        }
        .sidebar h2 {
            font-size: 1rem;
            color: var(--text-secondary);
            margin-bottom: 10px;
            text-transform: uppercase;
        }
        .sidebar-tabs { display: flex; gap: 5px; margin-bottom: 10px; }
        .sidebar-tab {
            flex: 1;
            padding: 8px;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            background: var(--bg-card);
            color: var(--text-secondary);
            font-size: 0.8rem;
        }
        .sidebar-tab:hover { color: var(--text-primary); }
        .sidebar-tab.active { background: var(--accent); color: white; }
        .source-list { list-style: none; max-height: 400px; overflow-y: auto; }
        .source-item {
            padding: 12px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.2s;
            margin-bottom: 5px;
            border: 1px solid transparent;
        }
        .source-item:hover { background: var(--bg-card); }
        .source-item.active { background: var(--bg-card); border-color: var(--accent); }
        .source-item.available { opacity: 0.7; border-style: dashed; }
        .source-item.available:hover { opacity: 1; }
        .source-item .badge {
            display: inline-block;
            padding: 2px 6px;
            border-radius: 4px;
            font-size: 0.7rem;
            margin-left: 5px;
        }
        .badge-loaded { background: var(--success); color: black; }
        .badge-available { background: var(--border); color: var(--text-secondary); }
        .source-item .name { font-weight: 600; }
        .source-item .meta { font-size: 0.8rem; color: var(--text-secondary); }
        .card {
            background: var(--bg-secondary);
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
        }
        .card h3 {
            font-size: 1.1rem;
            margin-bottom: 15px;
            padding-bottom: 10px;
            border-bottom: 1px solid var(--border);
        }
        .search-box { display: flex; gap: 10px; margin-bottom: 15px; }
        input[type="text"] {
            flex: 1;
            padding: 12px 15px;
            border: 1px solid var(--border);
            border-radius: 6px;
            background: var(--bg-primary);
            color: var(--text-primary);
            font-size: 1rem;
        }
        input[type="text"]:focus { outline: none; border-color: var(--accent); }
        button {
            padding: 12px 24px;
            border: none;
            border-radius: 6px;
            background: var(--accent);
            color: white;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
        }
        button:hover { background: var(--accent-hover); }
        .btn-secondary { background: var(--bg-card); border: 1px solid var(--border); }
        .btn-secondary:hover { background: var(--border); }
        .actions { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 15px; }
        .results-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            gap: 15px;
        }
        .manga-card {
            background: var(--bg-card);
            border-radius: 8px;
            overflow: hidden;
            cursor: pointer;
            transition: transform 0.2s;
        }
        .manga-card:hover { transform: translateY(-3px); }
        .manga-card img {
            width: 100%;
            height: 250px;
            object-fit: cover;
            background: var(--bg-primary);
        }
        .manga-card .info { padding: 12px; }
        .manga-card .title { font-weight: 600; font-size: 0.95rem; margin-bottom: 5px; }
        .manga-card .author { font-size: 0.8rem; color: var(--text-secondary); }
        .detail-header { display: flex; gap: 20px; margin-bottom: 20px; }
        .detail-header img { width: 200px; height: 300px; object-fit: cover; border-radius: 8px; }
        .detail-info { flex: 1; }
        .detail-info h2 { margin-bottom: 10px; }
        .detail-info .meta { color: var(--text-secondary); margin-bottom: 10px; }
        .genres { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 15px; }
        .genre-tag { background: var(--bg-card); padding: 4px 12px; border-radius: 20px; font-size: 0.8rem; }
        .description { color: var(--text-secondary); line-height: 1.6; max-height: 150px; overflow-y: auto; }
        .chapter-list { max-height: 400px; overflow-y: auto; }
        .chapter-item {
            padding: 12px;
            border-bottom: 1px solid var(--border);
            cursor: pointer;
            transition: background 0.2s;
        }
        .chapter-item:hover { background: var(--bg-card); }
        .content-view {
            background: var(--bg-primary);
            padding: 30px;
            border-radius: 8px;
            max-height: 600px;
            overflow-y: auto;
            line-height: 1.8;
        }
        .content-view p { margin-bottom: 1em; }
        .test-results { list-style: none; }
        .test-item {
            padding: 12px;
            border-radius: 6px;
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .test-item.success { background: rgba(74, 222, 128, 0.1); border-left: 3px solid var(--success); }
        .test-item.failure { background: rgba(248, 113, 113, 0.1); border-left: 3px solid var(--error); }
        .test-item .timing { margin-left: auto; color: var(--text-secondary); }
        .loading { display: flex; align-items: center; justify-content: center; padding: 40px; color: var(--text-secondary); }
        .spinner {
            width: 30px; height: 30px;
            border: 3px solid var(--border);
            border-top-color: var(--accent);
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin-right: 10px;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .empty-state { text-align: center; padding: 40px; color: var(--text-secondary); }
        .status-bar {
            background: var(--bg-card);
            padding: 10px 15px;
            border-radius: 6px;
            margin-bottom: 15px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .status-bar .timing { color: var(--success); }
        .json-view {
            background: var(--bg-primary);
            padding: 15px;
            border-radius: 6px;
            overflow-x: auto;
            font-family: monospace;
            font-size: 0.85rem;
            white-space: pre-wrap;
            max-height: 400px;
            overflow-y: auto;
        }
        .tabs { display: flex; gap: 5px; margin-bottom: 15px; border-bottom: 1px solid var(--border); padding-bottom: 10px; }
        .tab {
            padding: 8px 16px;
            border-radius: 6px 6px 0 0;
            cursor: pointer;
            background: transparent;
            border: none;
            color: var(--text-secondary);
        }
        .tab:hover { color: var(--text-primary); }
        .tab.active { background: var(--bg-card); color: var(--accent); }
    </style>
</head>
<body>
    <header>
        <div class="container" style="display: flex; justify-content: space-between; align-items: center;">
            <h1>📚 IReader Source Test Server</h1>
            <a href="/browse" style="color: var(--accent); text-decoration: none; padding: 8px 16px; border: 1px solid var(--accent); border-radius: 6px; background: rgba(233,69,96,0.1);">📖 Visual Browser</a>
        </div>
    </header>
    <div class="container">
        <div class="layout">
            <aside class="sidebar">
                <h2>Sources</h2>
                <div class="sidebar-tabs">
                    <button class="sidebar-tab active" onclick="showSourceTab('loaded')">Loaded</button>
                    <button class="sidebar-tab" onclick="showSourceTab('available')">Available</button>
                </div>
                <ul class="source-list" id="sourceList">
                    <li class="loading"><div class="spinner"></div> Loading...</li>
                </ul>
                <div id="availableInfo" style="display: none; padding: 10px; font-size: 0.85rem; color: var(--text-secondary);">
                    <p>Available sources are compiled but not loaded. Add them as dependencies in build.gradle.kts to test them.</p>
                </div>
                <div style="padding: 10px; border-top: 1px solid var(--border); margin-top: 10px;">
                    <button class="btn-secondary" onclick="reloadSources()" style="width: 100%; margin-bottom: 8px;">🔄 Reload Sources</button>
                    <div id="dex2jarStatus" style="font-size: 0.75rem; color: var(--text-secondary);"></div>
                </div>
            </aside>
            <main class="main-content">
                <div id="welcomeView" class="card">
                    <h3>Welcome</h3>
                    <p>Select a source from the sidebar to start testing.</p>
                </div>
                <div id="sourceView" style="display: none;">
                    <div class="card">
                        <h3>🔍 Search / Browse</h3>
                        <div class="search-box">
                            <input type="text" id="searchInput" placeholder="Search novels... (leave empty for latest)">
                            <button onclick="doSearch()">Search</button>
                        </div>
                        <div class="actions">
                            <button class="btn-secondary" onclick="runTestSuite()">🧪 Run Test Suite</button>
                            <button class="btn-secondary" onclick="showSourceInfo()">ℹ️ Source Info</button>
                        </div>
                    </div>
                    <div id="resultsArea"></div>
                </div>
            </main>
        </div>
    </div>
    <script>
        let currentSourceId = null;
        let currentManga = null;
        let sources = [];
        let availableSources = [];
        let currentTab = 'loaded';
        
        document.addEventListener('DOMContentLoaded', function() {
            loadSources();
            loadAvailableSources();
            checkDex2JarStatus();
        });
        document.getElementById('searchInput').addEventListener('keypress', function(e) {
            if (e.key === 'Enter') doSearch();
        });
        
        async function checkDex2JarStatus() {
            try {
                const response = await fetch('/api/dex2jar-status');
                const data = await response.json();
                const statusEl = document.getElementById('dex2jarStatus');
                if (data.available) {
                    statusEl.innerHTML = '✅ dex2jar: ready';
                    statusEl.style.color = 'var(--success)';
                } else {
                    statusEl.innerHTML = '⚠️ dex2jar: not found';
                    statusEl.style.color = 'var(--error)';
                }
            } catch (e) {
                console.error('Failed to check dex2jar status:', e);
            }
        }
        
        async function reloadSources() {
            const statusEl = document.getElementById('dex2jarStatus');
            statusEl.innerHTML = '🔄 Reloading...';
            try {
                const response = await fetch('/api/reload', { method: 'POST' });
                const data = await response.json();
                statusEl.innerHTML = '✅ Loaded ' + data.loaded + ' new source(s)';
                statusEl.style.color = 'var(--success)';
                await loadSources();
                await loadAvailableSources();
            } catch (e) {
                statusEl.innerHTML = '❌ Reload failed';
                statusEl.style.color = 'var(--error)';
            }
        }
        
        async function loadSources() {
            try {
                const response = await fetch('/api/sources');
                const data = await response.json();
                sources = Array.isArray(data) ? data : [];
                if (currentTab === 'loaded') renderSourceList();
            } catch (error) {
                console.error('Failed to load sources:', error);
                sources = [];
                document.getElementById('sourceList').innerHTML = '<li class="empty-state">Failed to load sources</li>';
            }
        }
        
        async function loadAvailableSources() {
            try {
                const response = await fetch('/api/available-sources');
                const data = await response.json();
                availableSources = Array.isArray(data) ? data : [];
            } catch (error) {
                console.error('Failed to load available sources:', error);
                availableSources = [];
            }
        }
        
        function showSourceTab(tab) {
            currentTab = tab;
            document.querySelectorAll('.sidebar-tab').forEach(function(t) {
                t.classList.toggle('active', t.textContent.toLowerCase() === tab);
            });
            document.getElementById('availableInfo').style.display = tab === 'available' ? 'block' : 'none';
            if (tab === 'loaded') {
                renderSourceList();
            } else {
                renderAvailableSourceList();
            }
        }
        
        function renderSourceList() {
            const list = document.getElementById('sourceList');
            if (sources.length === 0) {
                list.innerHTML = '<li class="empty-state"><p>No sources loaded.</p><p style="font-size: 0.85rem; margin-top: 10px;">Add sources as dependencies in build.gradle.kts</p></li>';
                return;
            }
            list.innerHTML = sources.map(function(s) {
                return '<li class="source-item" onclick="selectSource(\'' + s.id + '\')" data-id="' + s.id + '">' +
                    '<div class="name">' + s.name + ' <span class="badge badge-loaded">loaded</span></div>' +
                    '<div class="meta">' + s.lang + ' • ' + s.baseUrl + '</div></li>';
            }).join('');
        }
        
        function renderAvailableSourceList() {
            const list = document.getElementById('sourceList');
            if (availableSources.length === 0) {
                list.innerHTML = '<li class="empty-state"><p>No compiled sources found.</p><p style="font-size: 0.85rem; margin-top: 10px;">Run ./gradlew assembleDebug first</p></li>';
                return;
            }
            list.innerHTML = availableSources.map(function(s) {
                var isLoaded = sources.some(function(ls) { return ls.id === s.id; });
                return '<li class="source-item ' + (isLoaded ? '' : 'available') + '" onclick="showAvailableSourceInfo(\'' + s.name.replace(/'/g, "\\'") + '\')">' +
                    '<div class="name">' + s.name + ' <span class="badge ' + (isLoaded ? 'badge-loaded">loaded' : 'badge-available">available') + '</span></div>' +
                    '<div class="meta">' + s.lang + ' • ' + s.path + '</div></li>';
            }).join('');
        }
        
        function showAvailableSourceInfo(name) {
            var source = availableSources.find(function(s) { return s.name === name; });
            if (!source) return;
            
            var isLoaded = sources.some(function(ls) { return ls.id === source.id; });
            if (isLoaded) {
                selectSource(source.id);
                return;
            }
            
            document.getElementById('welcomeView').style.display = 'none';
            document.getElementById('sourceView').style.display = 'block';
            document.getElementById('resultsArea').innerHTML = 
                '<div class="card"><h3>📦 ' + source.name + '</h3>' +
                '<p style="margin-bottom: 15px;">This source is compiled but not loaded. To test it, add it as a dependency:</p>' +
                '<pre class="json-view">// In source-test-server/build.gradle.kts\nimplementation(project(":sources:' + source.path.replace(/\\/g, ':') + ':main"))</pre>' +
                '<p style="margin-top: 15px; color: var(--text-secondary);">Then restart the test server.</p>' +
                '<h4 style="margin-top: 20px;">Source Info</h4>' +
                '<pre class="json-view">' + JSON.stringify(source, null, 2) + '</pre></div>';
        }
        
        function selectSource(id) {
            currentSourceId = id;
            currentManga = null;
            document.querySelectorAll('.source-item').forEach(function(el) {
                el.classList.toggle('active', el.dataset.id == id);
            });
            document.getElementById('welcomeView').style.display = 'none';
            document.getElementById('sourceView').style.display = 'block';
            document.getElementById('resultsArea').innerHTML = '';
        }
        
        async function doSearch() {
            if (!currentSourceId) return;
            const query = document.getElementById('searchInput').value;
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="loading"><div class="spinner"></div> Searching...</div>';
            
            try {
                const url = '/api/sources/' + currentSourceId + '/search?q=' + encodeURIComponent(query) + '&page=1';
                const response = await fetch(url);
                const data = await response.json();
                
                if (data.error) {
                    resultsArea.innerHTML = '<div class="card"><h3>Error</h3><pre class="json-view">' + JSON.stringify(data, null, 2) + '</pre></div>';
                    return;
                }
                
                let html = '<div class="status-bar"><span>Found ' + data.results.length + ' results</span><span class="timing">' + data.timing + 'ms</span></div>';
                html += '<div class="card"><div class="tabs"><button class="tab active" onclick="showTab(this, \'grid\')">Grid</button><button class="tab" onclick="showTab(this, \'json\')">JSON</button></div>';
                html += '<div id="grid" class="results-grid">';
                data.results.forEach(function(m) {
                    const cover = m.cover || 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 150"><rect fill="%230f3460" width="100" height="150"/></svg>';
                    html += '<div class="manga-card" onclick=\'showDetails(' + JSON.stringify(m).replace(/'/g, "\\'") + ')\'>';
                    html += '<img src="' + cover + '" onerror="this.style.display=\'none\'" alt="' + m.title + '">';
                    html += '<div class="info"><div class="title">' + m.title + '</div><div class="author">' + (m.author || 'Unknown') + '</div></div></div>';
                });
                html += '</div><div id="json" style="display:none"><pre class="json-view">' + JSON.stringify(data, null, 2) + '</pre></div></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card"><h3>Error</h3><p>' + error.message + '</p></div>';
            }
        }
        
        function showTab(btn, tabId) {
            btn.parentElement.querySelectorAll('.tab').forEach(function(t) { t.classList.remove('active'); });
            btn.classList.add('active');
            var grid = document.getElementById('grid');
            var json = document.getElementById('json');
            if (grid) grid.style.display = tabId === 'grid' ? 'grid' : 'none';
            if (json) json.style.display = tabId === 'json' ? 'block' : 'none';
        }
        
        async function showDetails(manga) {
            currentManga = manga;
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="loading"><div class="spinner"></div> Loading details...</div>';
            
            try {
                const detailsRes = await fetch('/api/sources/' + currentSourceId + '/details?url=' + encodeURIComponent(manga.key));
                const chaptersRes = await fetch('/api/sources/' + currentSourceId + '/chapters?url=' + encodeURIComponent(manga.key));
                const details = await detailsRes.json();
                const chapters = await chaptersRes.json();
                const m = details.manga || manga;
                
                let html = '<div class="status-bar"><span>Details: ' + details.timing + 'ms | Chapters: ' + chapters.timing + 'ms</span>';
                html += '<button class="btn-secondary" onclick="doSearch()" style="padding: 6px 12px;">← Back</button></div>';
                html += '<div class="card"><div class="detail-header">';
                html += '<img src="' + (m.cover || '') + '" onerror="this.style.display=\'none\'">';
                html += '<div class="detail-info"><h2>' + m.title + '</h2>';
                html += '<div class="meta"><strong>Author:</strong> ' + (m.author || 'Unknown') + ' | <strong>Status:</strong> ' + (m.status || 'Unknown') + '</div>';
                html += '<div class="genres">';
                (m.genres || []).forEach(function(g) { html += '<span class="genre-tag">' + g + '</span>'; });
                html += '</div><div class="description">' + (m.description || 'No description') + '</div></div></div></div>';
                
                html += '<div class="card"><h3>📖 Chapters (' + (chapters.chapters ? chapters.chapters.length : 0) + ')</h3><div class="chapter-list">';
                (chapters.chapters || []).slice(0, 100).forEach(function(c) {
                    html += '<div class="chapter-item" onclick=\'showContent(' + JSON.stringify(c).replace(/'/g, "\\'") + ')\'>';
                    html += '<div class="name">' + c.name + '</div></div>';
                });
                html += '</div></div>';
                html += '<div class="card"><h3>📋 Raw JSON</h3><pre class="json-view">' + JSON.stringify({details: details, chapters: chapters}, null, 2) + '</pre></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card"><h3>Error</h3><p>' + error.message + '</p></div>';
            }
        }
        
        async function showContent(chapter) {
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="loading"><div class="spinner"></div> Loading content...</div>';
            
            try {
                const response = await fetch('/api/sources/' + currentSourceId + '/content?url=' + encodeURIComponent(chapter.key));
                const data = await response.json();
                
                let html = '<div class="status-bar"><span>Loaded in ' + data.timing + 'ms</span>';
                html += '<button class="btn-secondary" onclick="showDetails(currentManga)" style="padding: 6px 12px;">← Back</button></div>';
                html += '<div class="card"><h3>' + chapter.name + '</h3><div class="content-view">';
                (data.content && data.content.content || []).forEach(function(p) { html += '<p>' + p + '</p>'; });
                html += '</div></div>';
                html += '<div class="card"><h3>📋 Raw JSON</h3><pre class="json-view">' + JSON.stringify(data, null, 2) + '</pre></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card"><h3>Error</h3><p>' + error.message + '</p></div>';
            }
        }
        
        async function runTestSuite() {
            if (!currentSourceId) return;
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="loading"><div class="spinner"></div> Running tests...</div>';
            
            try {
                const response = await fetch('/api/sources/' + currentSourceId + '/test');
                const data = await response.json();
                
                let html = '<div class="card"><h3>🧪 Test Results for ' + data.source + '</h3><ul class="test-results">';
                data.tests.forEach(function(t) {
                    html += '<li class="test-item ' + (t.success ? 'success' : 'failure') + '">';
                    html += '<span>' + (t.success ? '✅' : '❌') + '</span>';
                    html += '<span>' + t.message + '</span>';
                    if (t.data) html += '<span style="color: var(--text-secondary); margin-left: 10px;">' + t.data + '</span>';
                    html += '<span class="timing">' + t.timing + 'ms</span></li>';
                });
                html += '</ul></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card"><h3>Error</h3><p>' + error.message + '</p></div>';
            }
        }
        
        async function showSourceInfo() {
            if (!currentSourceId) return;
            const source = sources.find(function(s) { return s.id === currentSourceId; });
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="card"><h3>ℹ️ Source Information</h3><pre class="json-view">' + JSON.stringify(source, null, 2) + '</pre></div>';
        }
    </script>
</body>
</html>
""".trimIndent()
}
