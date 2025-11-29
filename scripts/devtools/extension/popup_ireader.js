/**
 * IReader Source Creator - Popup Controller
 */

let currentPageType = 'unknown';

document.addEventListener('DOMContentLoaded', async function() {
    const currentTab = await getCurrentTab();
    
    if (currentTab) {
        document.getElementById('pageUrl').textContent = currentTab.url;
        await detectPageType();
    }
    
    loadSavedData();
    loadSettings();
    loadLog();
    
    // Tab switching
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
            tab.classList.add('active');
            document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
        });
    });
    
    // Page type manual change
    document.getElementById('pageTypeSelect').addEventListener('change', (e) => {
        currentPageType = e.target.value;
        addLog(`Page type changed to: ${currentPageType}`, 'info');
    });
    
    // Select buttons
    document.querySelectorAll('[data-field]').forEach(btn => {
        btn.addEventListener('click', () => startSelection(btn.dataset.field));
    });
    
    // AI buttons for single fields
    document.querySelectorAll('[data-ai-field]').forEach(btn => {
        btn.addEventListener('click', () => aiDetectSingleField(btn.dataset.aiField));
    });
    
    // Region select buttons
    document.querySelectorAll('[data-region]').forEach(btn => {
        btn.addEventListener('click', () => startRegionSelect(btn.dataset.region));
    });
    
    // Quick actions
    document.getElementById('btnAutoDetect').addEventListener('click', autoDetect);
    document.getElementById('btnTestAll').addEventListener('click', testAllSelectors);
    document.getElementById('btnAiDetect').addEventListener('click', aiDetectForPageType);
    
    // Preview
    document.getElementById('btnRefreshPreview').addEventListener('click', refreshPreview);
    
    // Export
    document.getElementById('btnExportKotlin').addEventListener('click', exportKotlin);
    document.getElementById('btnExportJSON').addEventListener('click', exportJSON);
    
    // Settings
    document.getElementById('btnSaveApiKey').addEventListener('click', saveApiKey);
    document.getElementById('btnLoadModels').addEventListener('click', loadGeminiModels);
    document.getElementById('geminiModel').addEventListener('change', saveSelectedModel);
    document.getElementById('optReverseChapters').addEventListener('change', saveOptions);
    document.getElementById('optAddBaseUrl').addEventListener('change', saveOptions);
    document.getElementById('btnClearAll').addEventListener('click', () => {
        if (confirm('Clear all data?')) {
            chrome.storage.local.clear(() => location.reload());
        }
    });
    
    // Log
    document.getElementById('btnClearLog').addEventListener('click', clearLog);
    
    // Input auto-save
    document.querySelectorAll('input[type="text"]').forEach(input => {
        input.addEventListener('input', debounce(saveInputs, 500));
    });
    
    // Copy selector on click
    document.querySelectorAll('.field-value').forEach(el => {
        el.addEventListener('click', () => {
            if (el.textContent !== '-') {
                navigator.clipboard.writeText(el.title || el.textContent);
                addLog('Copied: ' + (el.title || el.textContent), 'info');
            }
        });
    });
    
    // Storage changes
    chrome.storage.onChanged.addListener((changes) => {
        if (changes.sourceData) updateUI(changes.sourceData.newValue);
    });
});

async function getCurrentTab() {
    const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
    return tabs[0];
}

async function detectPageType() {
    const tab = await getCurrentTab();
    if (!tab) return;
    
    try {
        const response = await chrome.tabs.sendMessage(tab.id, { action: 'getPageType' });
        if (response?.success) {
            currentPageType = response.data.type;
            document.getElementById('pageTypeSelect').value = currentPageType;
        }
    } catch (e) {
        currentPageType = 'unknown';
    }
}

function loadSavedData() {
    chrome.storage.local.get(['sourceData'], (result) => {
        if (result.sourceData) updateUI(result.sourceData);
    });
}

function updateUI(data) {
    if (data.name) document.getElementById('sourceName').value = data.name;
    if (data.lang) document.getElementById('lang').value = data.lang;
    if (data.latestUrl) document.getElementById('latestUrl').value = data.latestUrl;
    if (data.popularUrl) document.getElementById('popularUrl').value = data.popularUrl;
    if (data.searchUrl) document.getElementById('searchUrl').value = data.searchUrl;
    
    if (data.selectors) {
        for (const [field, selector] of Object.entries(data.selectors)) {
            const el = document.getElementById('val-' + field);
            if (el) {
                el.textContent = selector.length > 18 ? selector.substring(0, 18) + '...' : selector;
                el.title = selector;
            }
        }
    }
}

function saveInputs() {
    chrome.storage.local.get(['sourceData'], (result) => {
        const data = result.sourceData || {};
        data.name = document.getElementById('sourceName').value;
        data.lang = document.getElementById('lang').value;
        data.latestUrl = document.getElementById('latestUrl').value;
        data.popularUrl = document.getElementById('popularUrl').value;
        data.searchUrl = document.getElementById('searchUrl').value;
        chrome.storage.local.set({ sourceData: data });
    });
}

async function startSelection(field) {
    const tab = await getCurrentTab();
    if (!tab || tab.url.startsWith('chrome://')) {
        alert('Navigate to a website first.');
        return;
    }
    
    try {
        await chrome.tabs.sendMessage(tab.id, { action: 'startSelection', field });
        window.close();
    } catch (e) {
        alert('Reload the page and try again.');
    }
}


async function autoDetect() {
    const tab = await getCurrentTab();
    if (!tab) return;
    
    const btn = document.getElementById('btnAutoDetect');
    btn.textContent = 'Detecting...';
    btn.disabled = true;
    addLog('Auto-detecting selectors...', 'info');
    
    try {
        const response = await chrome.tabs.sendMessage(tab.id, { action: 'autoDetect' });
        if (response?.success && response.selectors) {
            chrome.storage.local.get(['sourceData'], (result) => {
                const data = result.sourceData || {};
                data.selectors = { ...data.selectors, ...response.selectors };
                chrome.storage.local.set({ sourceData: data }, () => {
                    updateUI(data);
                    addLog(`Detected ${Object.keys(response.selectors).length} selectors`, 'success');
                });
            });
        } else {
            addLog('No selectors detected', 'warn');
        }
    } catch (e) {
        addLog('Auto-detect failed', 'error');
    }
    
    btn.textContent = 'Auto-Detect';
    btn.disabled = false;
}

async function testAllSelectors() {
    const tab = await getCurrentTab();
    if (!tab) return;
    
    addLog('Testing selectors...', 'info');
    
    chrome.storage.local.get(['sourceData'], async (result) => {
        const selectors = result.sourceData?.selectors || {};
        if (Object.keys(selectors).length === 0) {
            addLog('No selectors to test', 'warn');
            return;
        }
        
        let passed = 0;
        for (const [field, selector] of Object.entries(selectors)) {
            try {
                const response = await chrome.tabs.sendMessage(tab.id, { action: 'testSelector', selector });
                const ok = response?.success && response?.count > 0;
                if (ok) passed++;
                addLog(`${ok ? '✓' : '✗'} ${field}: ${response?.count || 0}`, ok ? 'success' : 'error');
            } catch (e) {
                addLog(`✗ ${field}: error`, 'error');
            }
        }
        addLog(`Test: ${passed}/${Object.keys(selectors).length} passed`, passed === Object.keys(selectors).length ? 'success' : 'warn');
    });
}

// ═══════════════════════════════════════════════════════════════
// PREVIEW
// ═══════════════════════════════════════════════════════════════

async function refreshPreview() {
    const tab = await getCurrentTab();
    if (!tab) return;
    
    addLog('Refreshing preview...', 'info');
    
    chrome.storage.local.get(['sourceData'], async (result) => {
        const selectors = result.sourceData?.selectors || {};
        
        try {
            const [res] = await chrome.scripting.executeScript({
                target: { tabId: tab.id },
                func: (sel) => {
                    const getText = (s) => document.querySelector(s)?.textContent?.trim()?.substring(0, 200) || '';
                    const getAttr = (s, a) => document.querySelector(s)?.getAttribute(a) || '';
                    const getImg = (s) => {
                        const img = document.querySelector(s);
                        return img?.src || img?.getAttribute('data-src') || '';
                    };
                    
                    // Get chapters
                    const chapters = [];
                    if (sel['chapter-item']) {
                        const items = document.querySelectorAll(sel['chapter-item']);
                        for (let i = 0; i < Math.min(items.length, 5); i++) {
                            const item = items[i];
                            const name = sel['chapter-name'] ? item.querySelector(sel['chapter-name'])?.textContent?.trim() : item.textContent?.trim();
                            chapters.push(name?.substring(0, 50) || 'Chapter ' + (i + 1));
                        }
                    }
                    
                    return {
                        title: getText(sel['title'] || 'h1'),
                        author: getText(sel['author'] || '.author'),
                        description: getText(sel['description'] || '.description'),
                        cover: getImg(sel['cover'] || '.cover img'),
                        status: getText(sel['status'] || '.status'),
                        genres: getText(sel['genres'] || '.genres'),
                        chapters: chapters,
                        content: getText(sel['content'] || '.chapter-content')?.substring(0, 300)
                    };
                },
                args: [selectors]
            });
            
            const data = res.result;
            
            document.getElementById('previewTitle').textContent = data.title || 'Title not set';
            document.getElementById('previewAuthor').textContent = 'Author: ' + (data.author || '-');
            document.getElementById('previewStatus').textContent = data.status || 'Unknown';
            document.getElementById('previewGenres').textContent = 'Genres: ' + (data.genres || '-');
            document.getElementById('previewDesc').textContent = data.description || 'Description not set';
            
            if (data.cover) {
                document.getElementById('previewCover').innerHTML = `<img src="${data.cover}" onerror="this.parentElement.textContent='No Cover'">`;
            }
            
            if (data.chapters.length > 0) {
                document.getElementById('previewChapters').innerHTML = data.chapters.map(c => 
                    `<div class="preview-chapter-item">${c}</div>`
                ).join('');
            }
            
            document.getElementById('previewContent').textContent = data.content || 'Navigate to a chapter page';
            
            addLog('Preview updated', 'success');
        } catch (e) {
            addLog('Preview failed: ' + e.message, 'error');
        }
    });
}

// ═══════════════════════════════════════════════════════════════
// AI FUNCTIONS
// ═══════════════════════════════════════════════════════════════

async function getSelectedModel() {
    return new Promise(resolve => {
        chrome.storage.local.get(['geminiModel'], (result) => {
            resolve(result.geminiModel || null);
        });
    });
}

async function getApiKey() {
    return new Promise(resolve => {
        chrome.storage.local.get(['geminiApiKey'], (result) => {
            resolve(result.geminiApiKey || null);
        });
    });
}

async function callGemini(prompt) {
    const apiKey = await getApiKey();
    const model = await getSelectedModel();
    
    if (!apiKey) {
        addLog('No API key set', 'error');
        return null;
    }
    
    if (!model) {
        addLog('No model selected. Go to Settings > Load Models', 'error');
        return null;
    }
    
    addLog(`Using model: ${model}`, 'info');
    
    try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                contents: [{ parts: [{ text: prompt }] }],
                generationConfig: { temperature: 0, maxOutputTokens: 200 }
            })
        });
        
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.error?.message || 'API error');
        }
        
        const data = await response.json();
        return data.candidates?.[0]?.content?.parts?.[0]?.text || null;
    } catch (e) {
        addLog('Gemini error: ' + e.message, 'error');
        return null;
    }
}

// AI detect based on current page type
async function aiDetectForPageType() {
    const tab = await getCurrentTab();
    if (!tab) return;
    
    const fieldsForPageType = {
        'novel-detail': ['title', 'author', 'description', 'cover', 'status', 'genres'],
        'novel-list': ['novel-item', 'explore-title', 'explore-cover', 'explore-link'],
        'chapter-content': ['content'],
        'search-results': ['novel-item', 'explore-title', 'explore-cover', 'explore-link'],
        'unknown': ['title', 'author', 'description', 'cover']
    };
    
    const fields = fieldsForPageType[currentPageType] || fieldsForPageType['unknown'];
    addLog(`AI detecting for ${currentPageType}: ${fields.join(', ')}`, 'info');
    
    const btn = document.getElementById('btnAiDetect');
    btn.textContent = 'Detecting...';
    btn.disabled = true;
    
    try {
        const [res] = await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            func: () => {
                // Get sample of page structure
                const samples = {};
                const selectors = ['h1', 'h2', '.title', '.author', '.description', '.cover', '.status', 
                    '.genres', '.chapter', 'img', 'a', '.novel', '.book', '.content', 'p'];
                selectors.forEach(s => {
                    const el = document.querySelector(s);
                    if (el) samples[s] = el.className || el.tagName;
                });
                return { samples, classes: [...new Set([...document.querySelectorAll('[class]')].slice(0, 30).map(e => e.className.split(' ')[0]))].join(',') };
            }
        });
        
        const prompt = `Find CSS selectors for: ${fields.join(', ')}
Page classes: ${res.result.classes}
Found: ${JSON.stringify(res.result.samples)}
Return ONLY JSON: {${fields.map(f => `"${f}":"selector"`).join(',')}}`;

        const response = await callGemini(prompt);
        if (response) {
            const match = response.match(/\{[\s\S]*\}/);
            if (match) {
                const selectors = JSON.parse(match[0]);
                chrome.storage.local.get(['sourceData'], (result) => {
                    const data = result.sourceData || {};
                    data.selectors = { ...data.selectors };
                    for (const [f, s] of Object.entries(selectors)) {
                        if (s && fields.includes(f)) {
                            data.selectors[f] = s;
                            addLog(`AI found ${f}: ${s}`, 'success');
                        }
                    }
                    chrome.storage.local.set({ sourceData: data }, () => updateUI(data));
                });
            }
        }
    } catch (e) {
        addLog('AI detect failed: ' + e.message, 'error');
    }
    
    btn.textContent = 'AI Detect (Current Page)';
    btn.disabled = false;
}

// AI detect single field - uses saved region if available
async function aiDetectSingleField(field) {
    const tab = await getCurrentTab();
    if (!tab) return;
    
    // Check if user has selected a region for this field
    const regions = await new Promise(resolve => {
        chrome.storage.local.get(['aiRegions'], r => resolve(r.aiRegions || {}));
    });
    
    if (regions[field]) {
        // Use the saved region HTML
        addLog(`AI analyzing region for: ${field}`, 'info');
        const region = regions[field];
        
        const prompt = `Find CSS selector for "${field}" in this HTML:
${region.html}
Container: ${region.selector}
Return ONLY the CSS selector relative to container, nothing else. Example: "a.title" or ".chapter-name"`;

        const response = await callGemini(prompt);
        if (response) {
            let selector = response.trim().replace(/["`\n]/g, '');
            // If it's a relative selector, combine with container
            if (!selector.startsWith(region.selector)) {
                selector = region.selector + ' ' + selector;
            }
            
            if (selector && selector.length < 150) {
                chrome.storage.local.get(['sourceData'], (result) => {
                    const data = result.sourceData || {};
                    data.selectors = { ...data.selectors, [field]: selector };
                    chrome.storage.local.set({ sourceData: data }, () => {
                        updateUI(data);
                        addLog(`AI set ${field}: ${selector}`, 'success');
                    });
                });
            }
        }
        return;
    }
    
    // No region saved, use general detection
    addLog(`AI detecting: ${field} (no region set)`, 'info');
    
    try {
        const [res] = await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            func: (f) => {
                const hints = {
                    'title': 'h1, .title, .novel-title',
                    'author': '.author, .writer',
                    'description': '.description, .synopsis, .summary',
                    'cover': '.cover img, img.cover',
                    'status': '.status',
                    'genres': '.genres a, .tags a',
                    'chapter-item': '.chapter-list li, .chapter-item',
                    'content': '.chapter-content, .reading-content, #content',
                    'novel-item': '.novel-item, .book-item'
                };
                const found = [];
                (hints[f] || '').split(',').forEach(s => {
                    const el = document.querySelector(s.trim());
                    if (el) found.push(s.trim());
                });
                return found.slice(0, 3);
            },
            args: [field]
        });
        
        if (res.result.length > 0) {
            const selector = res.result[0];
            chrome.storage.local.get(['sourceData'], (result) => {
                const data = result.sourceData || {};
                data.selectors = { ...data.selectors, [field]: selector };
                chrome.storage.local.set({ sourceData: data }, () => {
                    updateUI(data);
                    addLog(`Set ${field}: ${selector}`, 'success');
                });
            });
        } else {
            addLog(`No selector found for ${field}. Try selecting a region first.`, 'warn');
        }
    } catch (e) {
        addLog(`Failed for ${field}: ${e.message}`, 'error');
    }
}

// Start region selection for AI
async function startRegionSelect(field) {
    const tab = await getCurrentTab();
    if (!tab || tab.url.startsWith('chrome://')) {
        alert('Navigate to a website first.');
        return;
    }
    
    try {
        await chrome.tabs.sendMessage(tab.id, { action: 'startRegionSelect', field });
        window.close();
    } catch (e) {
        alert('Reload the page and try again.');
    }
}


// ═══════════════════════════════════════════════════════════════
// SETTINGS
// ═══════════════════════════════════════════════════════════════

function loadSettings() {
    chrome.storage.local.get(['geminiApiKey', 'geminiModel', 'geminiModels', 'options'], (result) => {
        if (result.geminiApiKey) {
            document.getElementById('geminiApiKey').value = result.geminiApiKey;
            document.getElementById('apiKeyStatus').textContent = 'API key saved';
            document.getElementById('apiKeyStatus').style.color = '#10b981';
        }
        
        if (result.geminiModels?.length > 0) {
            populateModelSelect(result.geminiModels, result.geminiModel);
        }
        
        if (result.options) {
            document.getElementById('optReverseChapters').checked = result.options.reverseChapters || false;
            document.getElementById('optAddBaseUrl').checked = result.options.addBaseUrl !== false;
        }
    });
}

function saveApiKey() {
    const key = document.getElementById('geminiApiKey').value.trim();
    if (!key) {
        document.getElementById('apiKeyStatus').textContent = 'Enter an API key';
        document.getElementById('apiKeyStatus').style.color = '#ef4444';
        return;
    }
    
    chrome.storage.local.set({ geminiApiKey: key }, () => {
        document.getElementById('apiKeyStatus').textContent = 'Saved. Now click Load Models';
        document.getElementById('apiKeyStatus').style.color = '#10b981';
        addLog('API key saved', 'success');
    });
}

async function loadGeminiModels() {
    const key = document.getElementById('geminiApiKey').value.trim();
    if (!key) {
        document.getElementById('apiKeyStatus').textContent = 'Enter API key first';
        document.getElementById('apiKeyStatus').style.color = '#ef4444';
        return;
    }
    
    document.getElementById('apiKeyStatus').textContent = 'Loading models...';
    document.getElementById('apiKeyStatus').style.color = '#f59e0b';
    
    try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${key}`);
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.error?.message || 'Failed');
        }
        
        const data = await response.json();
        const models = data.models
            .filter(m => m.supportedGenerationMethods?.includes('generateContent'))
            .map(m => ({
                id: m.name.replace('models/', ''),
                name: m.displayName || m.name.replace('models/', ''),
                input: m.inputTokenLimit,
                output: m.outputTokenLimit
            }))
            .sort((a, b) => a.name.localeCompare(b.name));
        
        if (models.length === 0) throw new Error('No compatible models');
        
        chrome.storage.local.set({ geminiModels: models });
        populateModelSelect(models);
        
        document.getElementById('apiKeyStatus').textContent = `Found ${models.length} models`;
        document.getElementById('apiKeyStatus').style.color = '#10b981';
        addLog(`Loaded ${models.length} models`, 'success');
    } catch (e) {
        document.getElementById('apiKeyStatus').textContent = e.message;
        document.getElementById('apiKeyStatus').style.color = '#ef4444';
        addLog('Load models failed: ' + e.message, 'error');
    }
}

function populateModelSelect(models, selected = null) {
    const select = document.getElementById('geminiModel');
    select.innerHTML = models.map(m => 
        `<option value="${m.id}" ${m.id === selected ? 'selected' : ''}>${m.name}</option>`
    ).join('');
    updateModelInfo();
}

function updateModelInfo() {
    chrome.storage.local.get(['geminiModels'], (result) => {
        const models = result.geminiModels || [];
        const id = document.getElementById('geminiModel').value;
        const model = models.find(m => m.id === id);
        if (model) {
            document.getElementById('modelInfo').textContent = `In: ${model.input?.toLocaleString() || '?'}, Out: ${model.output?.toLocaleString() || '?'} tokens`;
        }
    });
}

function saveSelectedModel() {
    const model = document.getElementById('geminiModel').value;
    chrome.storage.local.set({ geminiModel: model }, () => {
        addLog(`Model: ${model}`, 'info');
    });
    updateModelInfo();
}

function saveOptions() {
    const options = {
        reverseChapters: document.getElementById('optReverseChapters').checked,
        addBaseUrl: document.getElementById('optAddBaseUrl').checked
    };
    chrome.storage.local.set({ options });
}

// ═══════════════════════════════════════════════════════════════
// LOG
// ═══════════════════════════════════════════════════════════════

function loadLog() {
    chrome.storage.local.get(['activityLog'], (result) => {
        renderLog(result.activityLog || []);
    });
}

function renderLog(logs) {
    const container = document.getElementById('logContainer');
    if (logs.length === 0) {
        container.innerHTML = '<div style="color: #666; text-align: center; padding: 20px; font-size: 10px;">No activity yet</div>';
        return;
    }
    container.innerHTML = logs.map(l => `<div class="log-entry ${l.type}"><span class="log-time">${l.time}</span><span class="log-msg">${l.message}</span></div>`).join('');
    container.scrollTop = container.scrollHeight;
}

function addLog(message, type = 'info') {
    chrome.storage.local.get(['activityLog'], (result) => {
        const logs = result.activityLog || [];
        logs.push({ time: new Date().toLocaleTimeString(), message, type });
        if (logs.length > 100) logs.shift();
        chrome.storage.local.set({ activityLog: logs }, () => renderLog(logs));
    });
}

function clearLog() {
    chrome.storage.local.set({ activityLog: [] }, () => renderLog([]));
}

// ═══════════════════════════════════════════════════════════════
// EXPORT
// ═══════════════════════════════════════════════════════════════

async function exportKotlin() {
    const tab = await getCurrentTab();
    
    chrome.storage.local.get(['sourceData'], async (result) => {
        const data = result.sourceData || {};
        if (!data.name) {
            alert('Enter source name first');
            return;
        }
        
        try {
            data.baseUrl = new URL(tab.url).origin;
        } catch (e) {}
        
        const code = generateKotlin(data);
        downloadFile(code, `${data.name.replace(/[^a-zA-Z0-9]/g, '')}.kt`, 'text/plain');
        addLog(`Exported ${data.name}.kt`, 'success');
    });
}

function generateKotlin(config) {
    const c = config.name?.replace(/[^a-zA-Z0-9]/g, '') || 'NewSource';
    const s = config.selectors || {};
    const a = config.attributes || {};
    
    return `package ireader.${c.toLowerCase()}

import ireader.core.source.HttpSource
import ireader.core.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ${c} : HttpSource() {
    override val name = "${config.name || 'New Source'}"
    override val baseUrl = "${config.baseUrl || ''}"
    override val lang = "${config.lang || 'en'}"
    
    override fun popularMangaSelector() = "${s['novel-item'] || '.novel-item'}"
    
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst("${s['explore-title'] || 'a'}")?.text() ?: ""
            setUrlWithoutDomain(element.selectFirst("${s['explore-link'] || 'a'}")?.attr("href") ?: "")
            thumbnail_url = element.selectFirst("${s['explore-cover'] || 'img'}")?.attr("${a['explore-cover'] || 'src'}")
        }
    }
    
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst("${s['title'] || 'h1'}")?.text() ?: ""
            author = document.selectFirst("${s['author'] || '.author'}")?.text()
            description = document.selectFirst("${s['description'] || '.description'}")?.text()
            thumbnail_url = document.selectFirst("${s['cover'] || 'img'}")?.attr("${a['cover'] || 'src'}")
            status = parseStatus(document.selectFirst("${s['status'] || '.status'}")?.text())
            genre = document.select("${s['genres'] || '.genres a'}").joinToString { it.text() }
        }
    }
    
    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", true) -> SManga.ONGOING
        status.contains("completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
    
    override fun chapterListSelector() = "${s['chapter-item'] || '.chapter-item'}"
    
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.selectFirst("${s['chapter-name'] || 'a'}")?.text() ?: ""
            setUrlWithoutDomain(element.selectFirst("${s['chapter-link'] || 'a'}")?.attr("href") ?: "")
        }
    }
    
    override fun pageListParse(document: Document): List<Page> {
        val content = document.selectFirst("${s['content'] || '.chapter-content'}") ?: return emptyList()
        return listOf(Page(0, "", content.html()))
    }
}`;
}

function exportJSON() {
    chrome.storage.local.get(['sourceData'], async (result) => {
        const data = result.sourceData || {};
        if (!data.name) {
            alert('Enter source name first');
            return;
        }
        
        const tab = await getCurrentTab();
        try { data.baseUrl = new URL(tab.url).origin; } catch (e) {}
        data.exportedAt = new Date().toISOString();
        
        downloadFile(JSON.stringify(data, null, 2), `${data.name.toLowerCase().replace(/\s+/g, '_')}.json`, 'application/json');
        addLog('Exported JSON', 'success');
    });
}

function downloadFile(content, filename, type) {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    chrome.downloads.download({ url, filename, saveAs: true }, () => URL.revokeObjectURL(url));
}

function debounce(func, wait) {
    let timeout;
    return (...args) => {
        clearTimeout(timeout);
        timeout = setTimeout(() => func(...args), wait);
    };
}
