/**
 * IReader Source Creator - Element Selector
 * Handles visual element selection on web pages
 */

(function() {
    'use strict';
    
    if (window.__ireaderSelectorLoaded) return;
    window.__ireaderSelectorLoaded = true;
    
    const state = {
        active: false,
        field: null,
        overlay: null,
        banner: null,
        infoPanel: null,
        highlighted: null,
        savedHandlers: null
    };
    
    // Bypass website locks (right-click, selection, copy disabled)
    function bypassLocks() {
        state.savedHandlers = {
            oncontextmenu: document.oncontextmenu,
            onselectstart: document.onselectstart,
            oncopy: document.oncopy,
            onmousedown: document.onmousedown
        };
        
        // Remove inline handlers
        document.oncontextmenu = null;
        document.onselectstart = null;
        document.oncopy = null;
        document.onmousedown = null;
        
        // Remove event listeners that block interactions
        const blockingEvents = ['contextmenu', 'selectstart', 'copy', 'cut', 'paste', 'mousedown', 'mouseup', 'keydown'];
        blockingEvents.forEach(evt => {
            document.addEventListener(evt, unblockEvent, true);
        });
        
        // Override CSS that disables selection
        const style = document.createElement('style');
        style.id = 'ireader-unlock-style';
        style.textContent = `
            * {
                -webkit-user-select: auto !important;
                -moz-user-select: auto !important;
                -ms-user-select: auto !important;
                user-select: auto !important;
                pointer-events: auto !important;
            }
            html, body {
                overflow: auto !important;
            }
        `;
        document.head.appendChild(style);
        
        // Remove common overlay blockers
        document.querySelectorAll('[class*="overlay"], [class*="blocker"], [class*="modal"], [id*="overlay"], [id*="blocker"]').forEach(el => {
            if (el.style.position === 'fixed' || el.style.position === 'absolute') {
                el.dataset.ireaderHidden = el.style.display;
                el.style.display = 'none';
            }
        });
        
        console.log('[IReader] Website locks bypassed');
    }
    
    function unblockEvent(e) {
        if (state.active) {
            e.stopPropagation();
        }
    }
    
    function restoreLocks() {
        // Restore original handlers
        if (state.savedHandlers) {
            document.oncontextmenu = state.savedHandlers.oncontextmenu;
            document.onselectstart = state.savedHandlers.onselectstart;
            document.oncopy = state.savedHandlers.oncopy;
            document.onmousedown = state.savedHandlers.onmousedown;
            state.savedHandlers = null;
        }
        
        // Remove our event blockers
        const blockingEvents = ['contextmenu', 'selectstart', 'copy', 'cut', 'paste', 'mousedown', 'mouseup', 'keydown'];
        blockingEvents.forEach(evt => {
            document.removeEventListener(evt, unblockEvent, true);
        });
        
        // Remove unlock style
        const style = document.getElementById('ireader-unlock-style');
        if (style) style.remove();
        
        // Restore hidden overlays
        document.querySelectorAll('[data-ireader-hidden]').forEach(el => {
            el.style.display = el.dataset.ireaderHidden;
            delete el.dataset.ireaderHidden;
        });
        
        console.log('[IReader] Website locks restored');
    }
    
    // Listen for messages
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
        switch (request.action) {
            case 'startSelection':
                startSelection(request.field);
                sendResponse({ success: true });
                break;
                
            case 'testSelector':
                sendResponse(window.IReaderCore.testSelector(request.selector));
                break;
                
            case 'autoDetect':
                sendResponse({ 
                    success: true, 
                    selectors: window.IReaderCore.autoDetectSelectors() 
                });
                break;
                
            case 'getPageType':
                sendResponse({ 
                    success: true, 
                    data: window.IReaderCore.detectPageType() 
                });
                break;
                
            case 'findDataRegions':
                sendResponse({ 
                    success: true, 
                    regions: window.IReaderCore.findDataRegions() 
                });
                break;
                
            case 'generateKotlin':
                sendResponse({ 
                    success: true, 
                    code: window.IReaderCore.generateKotlinSource(request.config) 
                });
                break;
                
            case 'ping':
                sendResponse({ success: true });
                break;
            
            case 'startRegionSelect':
                startRegionSelect(request.field);
                sendResponse({ success: true });
                break;
            
            case 'getRegionHtml':
                sendResponse(getRegionHtml(request.selector));
                break;
        }
        return true;
    });
    
    // Get compressed HTML of a region for AI analysis
    function getRegionHtml(selector) {
        try {
            const el = document.querySelector(selector);
            if (!el) return { success: false, error: 'Element not found' };
            
            // Clone and compress the HTML
            const clone = el.cloneNode(true);
            const html = compressHtml(clone);
            
            return {
                success: true,
                html: html,
                selector: selector,
                tag: el.tagName.toLowerCase(),
                classes: el.className
            };
        } catch (e) {
            return { success: false, error: e.message };
        }
    }
    
    // Compress HTML by truncating repetitive elements
    function compressHtml(el) {
        // Remove scripts, styles, comments
        el.querySelectorAll('script, style, noscript, iframe').forEach(e => e.remove());
        
        // Truncate repetitive children
        const childGroups = {};
        Array.from(el.children).forEach(child => {
            const key = child.tagName + (child.className ? '.' + child.className.split(' ')[0] : '');
            if (!childGroups[key]) childGroups[key] = [];
            childGroups[key].push(child);
        });
        
        // Keep only first 3 of each type, replace rest with "..."
        for (const [key, children] of Object.entries(childGroups)) {
            if (children.length > 3) {
                for (let i = 3; i < children.length; i++) {
                    if (i === 3) {
                        const placeholder = document.createElement('span');
                        placeholder.textContent = `... (${children.length - 3} more ${key})`;
                        children[i].replaceWith(placeholder);
                    } else {
                        children[i].remove();
                    }
                }
            }
        }
        
        // Recursively compress children
        Array.from(el.children).forEach(child => {
            if (child.children.length > 0) {
                compressHtml(child);
            }
        });
        
        // Truncate long text nodes
        const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
        while (walker.nextNode()) {
            const text = walker.currentNode.textContent.trim();
            if (text.length > 100) {
                walker.currentNode.textContent = text.substring(0, 100) + '...';
            }
        }
        
        // Get outer HTML and limit size
        let html = el.outerHTML;
        if (html.length > 3000) {
            html = html.substring(0, 3000) + '... [truncated]';
        }
        
        return html;
    }
    
    // Region selection mode - user selects a container, then we analyze it
    function startRegionSelect(field) {
        cleanup();
        
        state.active = true;
        state.field = field;
        state.isRegionSelect = true;
        
        // Bypass website locks first
        bypassLocks();
        
        createOverlay();
        createRegionBanner();
        createInfoPanel();
        
        document.body.style.cursor = 'crosshair';
        
        document.addEventListener('mouseover', handleMouseOver, true);
        document.addEventListener('click', handleRegionClick, true);
        document.addEventListener('keydown', handleKeyDown, true);
    }
    
    function createRegionBanner() {
        state.banner = document.createElement('div');
        state.banner.id = 'ireader-banner';
        state.banner.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            background: linear-gradient(135deg, #8b5cf6, #6366f1);
            color: white;
            padding: 12px 20px;
            z-index: 2147483647;
            font-family: system-ui, sans-serif;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 10px rgba(0,0,0,0.3);
        `;
        
        state.banner.innerHTML = `
            <div>
                <div style="font-weight: 600; font-size: 14px;">AI Region Select: ${state.field}</div>
                <div style="font-size: 11px; opacity: 0.8;">Select the CONTAINER where ${state.field} is located. AI will find the exact selector.</div>
            </div>
            <button id="ireader-cancel" style="
                background: rgba(255,255,255,0.2);
                border: none;
                color: white;
                padding: 8px 16px;
                border-radius: 6px;
                cursor: pointer;
                font-weight: 600;
            ">Cancel</button>
        `;
        
        document.body.appendChild(state.banner);
        document.getElementById('ireader-cancel').onclick = cleanup;
    }
    
    function handleRegionClick(e) {
        if (!state.active) return;
        
        const el = e.target;
        if (!el || el.closest('#ireader-banner') || el.closest('#ireader-info')) return;
        
        e.preventDefault();
        e.stopPropagation();
        
        const selector = window.IReaderCore.generateSelector(el);
        const html = compressHtml(el.cloneNode(true));
        
        // Save region info for AI to use
        chrome.storage.local.get(['aiRegions'], (result) => {
            const regions = result.aiRegions || {};
            regions[state.field] = {
                selector: selector,
                html: html,
                tag: el.tagName.toLowerCase(),
                classes: el.className
            };
            
            chrome.storage.local.set({ aiRegions: regions }, () => {
                showNotification(`Region saved for ${state.field}. Now use AI button.`);
                cleanup();
            });
        });
    }
    
    function startSelection(field) {
        cleanup();
        
        state.active = true;
        state.field = field;
        
        // Bypass website locks first
        bypassLocks();
        
        createOverlay();
        createBanner();
        createInfoPanel();
        
        document.body.style.cursor = 'crosshair';
        
        document.addEventListener('mouseover', handleMouseOver, true);
        document.addEventListener('click', handleClick, true);
        document.addEventListener('keydown', handleKeyDown, true);
    }
    
    function cleanup() {
        // Restore website locks before cleanup
        restoreLocks();
        
        state.active = false;
        state.field = null;
        state.isRegionSelect = false;
        
        if (state.overlay) { state.overlay.remove(); state.overlay = null; }
        if (state.banner) { state.banner.remove(); state.banner = null; }
        if (state.infoPanel) { state.infoPanel.remove(); state.infoPanel = null; }
        if (state.highlighted) { 
            state.highlighted.style.outline = '';
            state.highlighted = null;
        }
        
        document.body.style.cursor = '';
        
        document.removeEventListener('mouseover', handleMouseOver, true);
        document.removeEventListener('click', handleClick, true);
        document.removeEventListener('click', handleRegionClick, true);
        document.removeEventListener('keydown', handleKeyDown, true);
    }
    
    function createOverlay() {
        state.overlay = document.createElement('div');
        state.overlay.id = 'ireader-overlay';
        state.overlay.style.cssText = `
            position: fixed;
            pointer-events: none;
            border: 3px solid #6366f1;
            background: rgba(99, 102, 241, 0.15);
            z-index: 2147483647;
            border-radius: 4px;
            transition: all 0.1s ease;
        `;
        document.body.appendChild(state.overlay);
    }
    
    function createBanner() {
        const fieldLabels = {
            'title': 'Novel Title',
            'author': 'Author',
            'description': 'Description',
            'cover': 'Cover Image',
            'status': 'Status',
            'genres': 'Genres',
            'chapter-item': 'Chapter Item',
            'chapter-name': 'Chapter Name',
            'chapter-link': 'Chapter Link',
            'content': 'Chapter Content',
            'novel-item': 'Novel Card',
            'explore-title': 'Novel Title (list)',
            'explore-cover': 'Novel Cover (list)',
            'explore-link': 'Novel Link (list)'
        };
        
        state.banner = document.createElement('div');
        state.banner.id = 'ireader-banner';
        state.banner.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            background: linear-gradient(135deg, #6366f1, #8b5cf6);
            color: white;
            padding: 12px 20px;
            z-index: 2147483647;
            font-family: system-ui, sans-serif;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 10px rgba(0,0,0,0.3);
        `;
        
        state.banner.innerHTML = `
            <div>
                <div style="font-weight: 600; font-size: 14px;">Select: ${fieldLabels[state.field] || state.field}</div>
                <div style="font-size: 11px; opacity: 0.8;">Click to select • ESC to cancel • ↑↓ navigate parent/child</div>
            </div>
            <button id="ireader-cancel" style="
                background: rgba(255,255,255,0.2);
                border: none;
                color: white;
                padding: 8px 16px;
                border-radius: 6px;
                cursor: pointer;
                font-weight: 600;
            ">Cancel</button>
        `;
        
        document.body.appendChild(state.banner);
        document.getElementById('ireader-cancel').onclick = cleanup;
    }
    
    function createInfoPanel() {
        state.infoPanel = document.createElement('div');
        state.infoPanel.id = 'ireader-info';
        state.infoPanel.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            background: #1e1e2e;
            color: #f8fafc;
            padding: 12px 16px;
            z-index: 2147483647;
            font-family: monospace;
            font-size: 12px;
            border-radius: 8px;
            max-width: 320px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.4);
        `;
        state.infoPanel.innerHTML = '<div style="color: #10b981;">Hover over elements</div>';
        document.body.appendChild(state.infoPanel);
    }

    
    function handleMouseOver(e) {
        if (!state.active) return;
        
        const el = e.target;
        if (!el || el === document.body || el.closest('#ireader-banner') || el.closest('#ireader-info')) return;
        
        // Remove previous highlight
        if (state.highlighted) {
            state.highlighted.style.outline = '';
        }
        
        // Highlight current element
        state.highlighted = el;
        
        // Update overlay position
        const rect = el.getBoundingClientRect();
        state.overlay.style.top = rect.top + 'px';
        state.overlay.style.left = rect.left + 'px';
        state.overlay.style.width = rect.width + 'px';
        state.overlay.style.height = rect.height + 'px';
        
        // Update info panel
        updateInfoPanel(el);
    }
    
    function updateInfoPanel(el) {
        const info = window.IReaderCore.getElementInfo(el);
        const attrs = info.attributes;
        
        let attrsHtml = '';
        if (attrs.length > 0) {
            attrsHtml = '<div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #333;">';
            attrs.forEach(attr => {
                attrsHtml += `<div style="font-size: 10px;"><span style="color: #10b981;">${attr.name}:</span> ${attr.value.substring(0, 40)}${attr.value.length > 40 ? '...' : ''}</div>`;
            });
            attrsHtml += '</div>';
        }
        
        const countColor = info.matchCount === 1 ? '#10b981' : (info.matchCount > 1 ? '#f59e0b' : '#ef4444');
        
        state.infoPanel.innerHTML = `
            <div style="margin-bottom: 6px;"><span style="color: #888;">Tag:</span> &lt;${info.tag}&gt;</div>
            <div style="margin-bottom: 6px; color: #6366f1; word-break: break-all;">${info.selector}</div>
            <div style="margin-bottom: 6px;"><span style="color: #888;">Matches:</span> <span style="color: ${countColor}; font-weight: bold;">${info.matchCount}</span></div>
            ${info.text ? `<div style="font-size: 10px; color: #888;">"${info.text.substring(0, 50)}..."</div>` : ''}
            ${attrsHtml}
        `;
    }
    
    function handleClick(e) {
        if (!state.active) return;
        
        const el = e.target;
        if (!el || el.closest('#ireader-banner') || el.closest('#ireader-info')) return;
        
        e.preventDefault();
        e.stopPropagation();
        
        const info = window.IReaderCore.getElementInfo(el);
        
        // Save selector
        chrome.storage.local.get(['sourceData'], (result) => {
            const data = result.sourceData || {};
            if (!data.selectors) data.selectors = {};
            if (!data.attributes) data.attributes = {};
            
            data.selectors[state.field] = info.selector;
            
            // Save attribute if relevant
            const attrs = info.attributes;
            if (state.field.includes('cover') || state.field.includes('link')) {
                const srcAttr = attrs.find(a => a.name === 'data-src' || a.name === 'src');
                const hrefAttr = attrs.find(a => a.name === 'href');
                if (srcAttr && srcAttr.name === 'data-src') {
                    data.attributes[state.field] = 'data-src';
                }
            }
            
            chrome.storage.local.set({ sourceData: data }, () => {
                showNotification(`Selected: ${info.selector}`);
                cleanup();
            });
        });
    }
    
    function handleKeyDown(e) {
        if (!state.active) return;
        
        if (e.key === 'Escape') {
            cleanup();
        } else if (e.key === 'ArrowUp' && state.highlighted && state.highlighted.parentElement) {
            e.preventDefault();
            const parent = state.highlighted.parentElement;
            if (parent !== document.body) {
                handleMouseOver({ target: parent });
            }
        } else if (e.key === 'ArrowDown' && state.highlighted && state.highlighted.firstElementChild) {
            e.preventDefault();
            handleMouseOver({ target: state.highlighted.firstElementChild });
        }
    }
    
    function showNotification(message) {
        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: #10b981;
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            font-family: system-ui, sans-serif;
            font-size: 13px;
            font-weight: 600;
            z-index: 2147483648;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
        `;
        notification.textContent = message;
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.style.opacity = '0';
            notification.style.transition = 'opacity 0.3s';
            setTimeout(() => notification.remove(), 300);
        }, 2000);
    }
    
    console.log('[IReader] Selector module loaded');
})();
