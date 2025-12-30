package ireader.testserver

/**
 * Returns the HTML UI for the test server - Modern glassmorphism design
 */
fun getIndexHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IReader Source Test Server</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
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
            --warning: #f59e0b;
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
        /* Scrollbar */
        ::-webkit-scrollbar { width: 8px; height: 8px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: var(--glass-border); border-radius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: var(--text-muted); }
        
        /* Glass effect */
        .glass {
            background: var(--glass-bg);
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
            border: 1px solid var(--glass-border);
            border-radius: 16px;
        }
        .glass-sm { border-radius: 12px; }
        
        /* Layout */
        .container { max-width: 1600px; margin: 0 auto; padding: 24px; }
        
        /* Header */
        header {
            background: var(--glass-bg);
            backdrop-filter: blur(20px);
            border-bottom: 1px solid var(--glass-border);
            position: sticky;
            top: 0;
            z-index: 100;
        }
        .header-inner {
            max-width: 1600px;
            margin: 0 auto;
            padding: 16px 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 20px;
        }
        .logo {
            display: flex;
            align-items: center;
            gap: 12px;
            font-size: 1.5rem;
            font-weight: 700;
            background: linear-gradient(135deg, var(--accent-light), #a78bfa);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .logo-icon {
            width: 40px;
            height: 40px;
            background: linear-gradient(135deg, var(--accent), #8b5cf6);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.2rem;
            -webkit-text-fill-color: white;
        }
        .header-nav { display: flex; gap: 8px; }
        .nav-btn {
            padding: 10px 20px;
            border-radius: 10px;
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            color: var(--text-secondary);
            text-decoration: none;
            font-weight: 500;
            font-size: 0.9rem;
            transition: all 0.3s ease;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .nav-btn:hover { background: var(--glass-hover); color: var(--text-primary); border-color: var(--accent); }
        .nav-btn.active { background: var(--accent); color: white; border-color: var(--accent); }
        
        /* Main Layout */
        .layout { display: grid; grid-template-columns: 320px 1fr; gap: 24px; margin-top: 24px; }
        @media (max-width: 1024px) { .layout { grid-template-columns: 1fr; } }
        
        /* Sidebar */
        .sidebar { padding: 20px; height: fit-content; position: sticky; top: 100px; }
        .sidebar-title {
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            color: var(--text-muted);
            margin-bottom: 16px;
        }
        .sidebar-tabs { display: flex; gap: 8px; margin-bottom: 16px; }
        .sidebar-tab {
            flex: 1;
            padding: 10px;
            border: none;
            border-radius: 10px;
            cursor: pointer;
            background: transparent;
            color: var(--text-secondary);
            font-size: 0.85rem;
            font-weight: 500;
            transition: all 0.2s;
        }
        .sidebar-tab:hover { background: var(--glass-hover); color: var(--text-primary); }
        .sidebar-tab.active { background: var(--accent); color: white; }
        
        /* Source List */
        .source-list { list-style: none; max-height: 450px; overflow-y: auto; }
        .source-item {
            padding: 14px 16px;
            border-radius: 12px;
            cursor: pointer;
            transition: all 0.2s;
            margin-bottom: 8px;
            border: 1px solid transparent;
        }
        .source-item:hover { background: var(--glass-hover); }
        .source-item.active { background: var(--glass-hover); border-color: var(--accent); box-shadow: 0 0 20px var(--accent-glow); }
        .source-item .name { font-weight: 600; font-size: 0.95rem; margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
        .source-item .meta { font-size: 0.8rem; color: var(--text-muted); }
        .badge {
            display: inline-flex;
            align-items: center;
            padding: 2px 8px;
            border-radius: 6px;
            font-size: 0.65rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }
        .badge-loaded { background: rgba(16, 185, 129, 0.2); color: var(--success); }
        .badge-available { background: rgba(100, 116, 139, 0.2); color: var(--text-muted); }
        
        /* Sidebar Footer */
        .sidebar-footer {
            margin-top: 20px;
            padding-top: 20px;
            border-top: 1px solid var(--glass-border);
        }
        .reload-btn {
            width: 100%;
            padding: 12px;
            border: 1px solid var(--glass-border);
            border-radius: 10px;
            background: var(--glass-bg);
            color: var(--text-primary);
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }
        .reload-btn:hover { background: var(--glass-hover); border-color: var(--accent); }
        .status-text { font-size: 0.75rem; color: var(--text-muted); margin-top: 10px; text-align: center; }
        
        /* Cards */
        .card { padding: 24px; margin-bottom: 20px; }
        .card-title {
            font-size: 1.1rem;
            font-weight: 600;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .card-title::before {
            content: '';
            width: 4px;
            height: 20px;
            background: linear-gradient(180deg, var(--accent), #8b5cf6);
            border-radius: 2px;
        }
        
        /* Search Box */
        .search-box { display: flex; gap: 12px; margin-bottom: 20px; }
        input[type="text"] {
            flex: 1;
            padding: 14px 20px;
            border: 1px solid var(--glass-border);
            border-radius: 12px;
            background: rgba(0, 0, 0, 0.2);
            color: var(--text-primary);
            font-size: 1rem;
            font-family: inherit;
            transition: all 0.2s;
        }
        input[type="text"]:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 20px var(--accent-glow); }
        input[type="text"]::placeholder { color: var(--text-muted); }
        
        /* Buttons */
        button, .btn {
            padding: 14px 28px;
            border: none;
            border-radius: 12px;
            background: linear-gradient(135deg, var(--accent), #8b5cf6);
            color: white;
            font-weight: 600;
            font-size: 0.95rem;
            cursor: pointer;
            transition: all 0.3s ease;
            font-family: inherit;
        }
        button:hover, .btn:hover { transform: translateY(-2px); box-shadow: 0 10px 30px var(--accent-glow); }
        .btn-secondary {
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            color: var(--text-primary);
        }
        .btn-secondary:hover { background: var(--glass-hover); border-color: var(--accent); box-shadow: none; transform: none; }
        .actions { display: flex; gap: 10px; flex-wrap: wrap; }
        
        /* Results Grid */
        .results-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            gap: 20px;
        }
        .manga-card {
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: 16px;
            overflow: hidden;
            cursor: pointer;
            transition: all 0.3s ease;
        }
        .manga-card:hover { transform: translateY(-5px); box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3); border-color: var(--accent); }
        .manga-card img {
            width: 100%;
            height: 280px;
            object-fit: cover;
            background: linear-gradient(135deg, var(--bg-gradient-1), var(--bg-gradient-2));
        }
        .manga-card .info { padding: 16px; }
        .manga-card .title {
            font-weight: 600;
            font-size: 0.95rem;
            margin-bottom: 6px;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .manga-card .author { font-size: 0.8rem; color: var(--text-muted); }
        
        /* Detail View */
        .detail-header { display: flex; gap: 30px; margin-bottom: 30px; }
        @media (max-width: 768px) { .detail-header { flex-direction: column; } }
        .detail-header img {
            width: 220px;
            height: 320px;
            object-fit: cover;
            border-radius: 16px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
        }
        .detail-info { flex: 1; }
        .detail-info h2 { font-size: 1.8rem; margin-bottom: 12px; }
        .detail-info .meta { color: var(--text-secondary); margin-bottom: 16px; font-size: 0.95rem; }
        .genres { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 20px; }
        .genre-tag {
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 0.8rem;
            color: var(--text-secondary);
        }
        .description {
            color: var(--text-secondary);
            line-height: 1.8;
            max-height: 180px;
            overflow-y: auto;
            padding-right: 10px;
        }
        
        /* Chapter List */
        .chapter-list { max-height: 450px; overflow-y: auto; }
        .chapter-item {
            padding: 16px 20px;
            border-bottom: 1px solid var(--glass-border);
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .chapter-item:last-child { border-bottom: none; }
        .chapter-item:hover { background: var(--glass-hover); }
        .chapter-item .name { font-weight: 500; }
        .chapter-item .date { font-size: 0.8rem; color: var(--text-muted); }
        
        /* Content View */
        .content-view {
            background: rgba(0, 0, 0, 0.2);
            padding: 40px;
            border-radius: 16px;
            max-height: 650px;
            overflow-y: auto;
            line-height: 2;
            font-size: 1.1rem;
        }
        .content-view p { margin-bottom: 1.2em; color: var(--text-secondary); }
        
        /* Test Results */
        .test-results { list-style: none; }
        .test-item {
            padding: 16px 20px;
            border-radius: 12px;
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            gap: 12px;
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
        }
        .test-item.success { border-left: 3px solid var(--success); }
        .test-item.failure { border-left: 3px solid var(--error); }
        .test-item .icon { font-size: 1.2rem; }
        .test-item .message { flex: 1; }
        .test-item .data { color: var(--text-muted); font-size: 0.85rem; }
        .test-item .timing { color: var(--text-muted); font-size: 0.85rem; font-family: monospace; }
        
        /* Status Bar */
        .status-bar {
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            padding: 14px 20px;
            border-radius: 12px;
            margin-bottom: 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .status-bar .timing { color: var(--success); font-family: monospace; }
        
        /* JSON View */
        .json-view {
            background: rgba(0, 0, 0, 0.3);
            padding: 20px;
            border-radius: 12px;
            overflow-x: auto;
            font-family: 'JetBrains Mono', 'Fira Code', monospace;
            font-size: 0.85rem;
            white-space: pre-wrap;
            max-height: 450px;
            overflow-y: auto;
            color: var(--text-secondary);
            border: 1px solid var(--glass-border);
        }
        
        /* Tabs */
        .tabs { display: flex; gap: 8px; margin-bottom: 20px; }
        .tab {
            padding: 10px 20px;
            border-radius: 10px;
            cursor: pointer;
            background: transparent;
            border: 1px solid transparent;
            color: var(--text-secondary);
            font-weight: 500;
            transition: all 0.2s;
        }
        .tab:hover { background: var(--glass-hover); color: var(--text-primary); }
        .tab.active { background: var(--accent); color: white; border-color: var(--accent); }
        
        /* Loading & Empty States */
        .loading {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 60px;
            color: var(--text-secondary);
            gap: 12px;
        }
        .spinner {
            width: 24px;
            height: 24px;
            border: 3px solid var(--glass-border);
            border-top-color: var(--accent);
            border-radius: 50%;
            animation: spin 1s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: var(--text-muted);
        }
        .empty-state p { margin-bottom: 8px; }
        
        /* Welcome View */
        .welcome-content { text-align: center; padding: 40px; }
        .welcome-content h2 {
            font-size: 2rem;
            margin-bottom: 16px;
            background: linear-gradient(135deg, var(--accent-light), #a78bfa);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .welcome-content p { color: var(--text-secondary); max-width: 500px; margin: 0 auto 30px; }
        .feature-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-top: 30px; }
        .feature-item {
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: 12px;
            padding: 24px;
            text-align: left;
        }
        .feature-item .icon { font-size: 2rem; margin-bottom: 12px; }
        .feature-item h4 { margin-bottom: 8px; }
        .feature-item p { font-size: 0.85rem; color: var(--text-muted); }
    </style>
</head>
<body>
    <header>
        <div class="header-inner">
            <div class="logo">
                <div class="logo-icon">📚</div>
                <span>IReader Test Server</span>
            </div>
            <nav class="header-nav">
                <a href="/" class="nav-btn active">🔧 API Tester</a>
                <a href="/browse" class="nav-btn">📖 Visual Browser</a>
            </nav>
        </div>
    </header>
    <div class="container">
        <div class="layout">
            <aside class="sidebar glass">
                <div class="sidebar-title">Sources</div>
                <input type="text" id="sourceSearchInput" placeholder="🔍 Filter sources..." style="width: 100%; padding: 10px 14px; margin-bottom: 12px; font-size: 0.9rem;" oninput="filterSources()">
                <div class="sidebar-tabs">
                    <button class="sidebar-tab active" onclick="showSourceTab('loaded')">Loaded</button>
                    <button class="sidebar-tab" onclick="showSourceTab('available')">Available</button>
                </div>
                <ul class="source-list" id="sourceList">
                    <li class="loading"><div class="spinner"></div> Loading...</li>
                </ul>
                <div id="availableInfo" style="display: none; padding: 12px; font-size: 0.8rem; color: var(--text-muted); background: rgba(0,0,0,0.2); border-radius: 8px; margin-top: 12px;">
                    Available sources are compiled but not loaded. Click to see how to add them.
                </div>
                <div class="sidebar-footer">
                    <button class="reload-btn" onclick="reloadSources()">
                        <span>🔄</span> Reload Sources
                    </button>
                    <div class="status-text" id="dex2jarStatus"></div>
                </div>
            </aside>
            
            <main class="main-content">
                <div id="welcomeView" class="card glass">
                    <div class="welcome-content">
                        <h2>Welcome to IReader Test Server</h2>
                        <p>Select a source from the sidebar to start testing. You can search for novels, view details, and read chapters.</p>
                        <div class="feature-grid">
                            <div class="feature-item">
                                <div class="icon">🔍</div>
                                <h4>Search & Browse</h4>
                                <p>Search novels or browse latest releases from any source</p>
                            </div>
                            <div class="feature-item">
                                <div class="icon">📖</div>
                                <h4>Read Content</h4>
                                <p>View novel details, chapters, and read content directly</p>
                            </div>
                            <div class="feature-item">
                                <div class="icon">🧪</div>
                                <h4>Test Suite</h4>
                                <p>Run automated tests to verify source functionality</p>
                            </div>
                            <div class="feature-item">
                                <div class="icon">📋</div>
                                <h4>JSON Output</h4>
                                <p>View raw JSON responses for debugging</p>
                            </div>
                        </div>
                    </div>
                </div>
                
                <div id="sourceView" style="display: none;">
                    <div class="card glass">
                        <h3 class="card-title">Search & Browse</h3>
                        <div class="search-box">
                            <input type="text" id="searchInput" placeholder="Search novels... (leave empty for latest)">
                            <button onclick="doSearch()">Search</button>
                        </div>
                        <div class="actions">
                            <button class="btn-secondary" onclick="runTestSuite()">🧪 Run Tests</button>
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
                    statusEl.innerHTML = '✅ dex2jar ready';
                    statusEl.style.color = 'var(--success)';
                } else {
                    statusEl.innerHTML = '⚠️ dex2jar not found';
                    statusEl.style.color = 'var(--error)';
                }
            } catch (e) {
                console.error('Failed to check dex2jar status:', e);
            }
        }
        
        async function reloadSources() {
            const statusEl = document.getElementById('dex2jarStatus');
            statusEl.innerHTML = '🔄 Reloading...';
            statusEl.style.color = 'var(--warning)';
            try {
                const response = await fetch('/api/reload', { method: 'POST' });
                const data = await response.json();
                statusEl.innerHTML = '✅ Loaded ' + data.loaded + ' source(s)';
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
            if (tab === 'loaded') renderSourceList();
            else renderAvailableSourceList();
        }
        
        function filterSources() {
            if (currentTab === 'loaded') renderSourceList();
            else renderAvailableSourceList();
        }
        
        function getFilteredSources(sourceList) {
            const query = (document.getElementById('sourceSearchInput').value || '').toLowerCase().trim();
            if (!query) return sourceList;
            return sourceList.filter(function(s) {
                return s.name.toLowerCase().includes(query) || 
                       s.lang.toLowerCase().includes(query) ||
                       (s.baseUrl || '').toLowerCase().includes(query) ||
                       (s.path || '').toLowerCase().includes(query);
            });
        }
        
        function renderSourceList() {
            const list = document.getElementById('sourceList');
            const filtered = getFilteredSources(sources);
            if (sources.length === 0) {
                list.innerHTML = '<li class="empty-state"><p>No sources loaded</p><p style="font-size: 0.8rem;">Add sources as dependencies</p></li>';
                return;
            }
            if (filtered.length === 0) {
                list.innerHTML = '<li class="empty-state"><p>No matching sources</p><p style="font-size: 0.8rem;">Try a different search</p></li>';
                return;
            }
            list.innerHTML = filtered.map(function(s) {
                return '<li class="source-item" onclick="selectSource(\'' + s.id + '\')" data-id="' + s.id + '">' +
                    '<div class="name">' + s.name + ' <span class="badge badge-loaded">loaded</span></div>' +
                    '<div class="meta">' + s.lang + ' • ' + (s.baseUrl || '').replace(/https?:\/\//, '').substring(0, 30) + '</div></li>';
            }).join('');
        }
        
        function renderAvailableSourceList() {
            const list = document.getElementById('sourceList');
            const filtered = getFilteredSources(availableSources);
            if (availableSources.length === 0) {
                list.innerHTML = '<li class="empty-state"><p>No compiled sources</p><p style="font-size: 0.8rem;">Run ./gradlew assembleDebug</p></li>';
                return;
            }
            if (filtered.length === 0) {
                list.innerHTML = '<li class="empty-state"><p>No matching sources</p><p style="font-size: 0.8rem;">Try a different search</p></li>';
                return;
            }
            list.innerHTML = filtered.map(function(s) {
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
            if (isLoaded) { selectSource(source.id); return; }
            
            document.getElementById('welcomeView').style.display = 'none';
            document.getElementById('sourceView').style.display = 'block';
            document.getElementById('resultsArea').innerHTML = 
                '<div class="card glass"><h3 class="card-title">📦 ' + source.name + '</h3>' +
                '<p style="margin-bottom: 20px; color: var(--text-secondary);">This source is compiled but not loaded. To test it, add it as a dependency:</p>' +
                '<pre class="json-view">// In source-test-server/build.gradle.kts\nimplementation(project(":sources:' + source.path.replace(/\\/g, ':') + ':main"))</pre>' +
                '<p style="margin-top: 20px; color: var(--text-muted);">Then restart the test server.</p>' +
                '<h4 style="margin-top: 30px; margin-bottom: 15px;">Source Info</h4>' +
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
                    resultsArea.innerHTML = '<div class="card glass"><h3 class="card-title">Error</h3><pre class="json-view">' + JSON.stringify(data, null, 2) + '</pre></div>';
                    return;
                }
                
                let html = '<div class="status-bar"><span>Found ' + data.results.length + ' results</span><span class="timing">' + data.timing + 'ms</span></div>';
                html += '<div class="card glass"><div class="tabs"><button class="tab active" onclick="showTab(this, \'grid\')">Grid View</button><button class="tab" onclick="showTab(this, \'json\')">JSON</button></div>';
                html += '<div id="grid" class="results-grid">';
                data.results.forEach(function(m) {
                    const cover = m.cover || 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 150"><rect fill="%23302b63" width="100" height="150"/><text x="50" y="80" text-anchor="middle" fill="%2364748b" font-size="10">No Cover</text></svg>';
                    html += '<div class="manga-card" onclick=\'showDetails(' + JSON.stringify(m).replace(/'/g, "\\'") + ')\'>';
                    html += '<img src="' + cover + '" onerror="this.style.background=\'linear-gradient(135deg, #302b63, #24243e)\'" alt="">';
                    html += '<div class="info"><div class="title">' + m.title + '</div><div class="author">' + (m.author || 'Unknown Author') + '</div></div></div>';
                });
                html += '</div><div id="json" style="display:none"><pre class="json-view">' + JSON.stringify(data, null, 2) + '</pre></div></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card glass"><h3 class="card-title">Error</h3><p style="color: var(--error);">' + error.message + '</p></div>';
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
                html += '<button class="btn-secondary" onclick="doSearch()" style="padding: 8px 16px;">← Back</button></div>';
                html += '<div class="card glass"><div class="detail-header">';
                html += '<img src="' + (m.cover || '') + '" onerror="this.style.background=\'linear-gradient(135deg, #302b63, #24243e)\'">';
                html += '<div class="detail-info"><h2>' + m.title + '</h2>';
                html += '<div class="meta"><strong>Author:</strong> ' + (m.author || 'Unknown') + ' | <strong>Status:</strong> ' + (m.status || 'Unknown') + '</div>';
                html += '<div class="genres">';
                (m.genres || []).forEach(function(g) { html += '<span class="genre-tag">' + g + '</span>'; });
                html += '</div><div class="description">' + (m.description || 'No description available') + '</div></div></div></div>';
                
                html += '<div class="card glass"><h3 class="card-title">Chapters (' + (chapters.chapters ? chapters.chapters.length : 0) + ')</h3><div class="chapter-list">';
                (chapters.chapters || []).slice(0, 100).forEach(function(c) {
                    html += '<div class="chapter-item" onclick=\'showContent(' + JSON.stringify(c).replace(/'/g, "\\'") + ')\'>';
                    html += '<span class="name">' + c.name + '</span>';
                    if (c.dateUpload) html += '<span class="date">' + new Date(c.dateUpload).toLocaleDateString() + '</span>';
                    html += '</div>';
                });
                if ((chapters.chapters || []).length > 100) html += '<div style="padding: 16px; color: var(--text-muted); text-align: center;">Showing first 100 of ' + chapters.chapters.length + ' chapters</div>';
                html += '</div></div>';
                html += '<div class="card glass"><h3 class="card-title">Raw JSON</h3><pre class="json-view">' + JSON.stringify({details: details, chapters: chapters}, null, 2) + '</pre></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card glass"><h3 class="card-title">Error</h3><p style="color: var(--error);">' + error.message + '</p></div>';
            }
        }
        
        async function showContent(chapter) {
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="loading"><div class="spinner"></div> Loading content...</div>';
            
            try {
                const response = await fetch('/api/sources/' + currentSourceId + '/content?url=' + encodeURIComponent(chapter.key));
                const data = await response.json();
                
                let html = '<div class="status-bar"><span>Loaded in ' + data.timing + 'ms</span>';
                html += '<button class="btn-secondary" onclick="showDetails(currentManga)" style="padding: 8px 16px;">← Back</button></div>';
                html += '<div class="card glass"><h3 class="card-title">' + chapter.name + '</h3><div class="content-view">';
                (data.content && data.content.content || []).forEach(function(p) { html += '<p>' + p + '</p>'; });
                html += '</div></div>';
                html += '<div class="card glass"><h3 class="card-title">Raw JSON</h3><pre class="json-view">' + JSON.stringify(data, null, 2) + '</pre></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card glass"><h3 class="card-title">Error</h3><p style="color: var(--error);">' + error.message + '</p></div>';
            }
        }
        
        async function runTestSuite() {
            if (!currentSourceId) return;
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="loading"><div class="spinner"></div> Running tests...</div>';
            
            try {
                const response = await fetch('/api/sources/' + currentSourceId + '/test');
                const data = await response.json();
                
                let html = '<div class="card glass"><h3 class="card-title">Test Results - ' + data.source + '</h3><ul class="test-results">';
                data.tests.forEach(function(t) {
                    html += '<li class="test-item ' + (t.success ? 'success' : 'failure') + '">';
                    html += '<span class="icon">' + (t.success ? '✅' : '❌') + '</span>';
                    html += '<span class="message">' + t.message + '</span>';
                    if (t.data) html += '<span class="data">' + t.data + '</span>';
                    html += '<span class="timing">' + t.timing + 'ms</span></li>';
                });
                html += '</ul></div>';
                resultsArea.innerHTML = html;
            } catch (error) {
                resultsArea.innerHTML = '<div class="card glass"><h3 class="card-title">Error</h3><p style="color: var(--error);">' + error.message + '</p></div>';
            }
        }
        
        async function showSourceInfo() {
            if (!currentSourceId) return;
            const source = sources.find(function(s) { return s.id === currentSourceId; });
            const resultsArea = document.getElementById('resultsArea');
            resultsArea.innerHTML = '<div class="card glass"><h3 class="card-title">Source Information</h3><pre class="json-view">' + JSON.stringify(source, null, 2) + '</pre></div>';
        }
    </script>
</body>
</html>
""".trimIndent()
}
