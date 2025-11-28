// Prevent double injection
if (window.__ireaderContentScriptLoaded) {
    console.log("[IReader] Content script already loaded, skipping...");
} else {
    window.__ireaderContentScriptLoaded = true;
    console.log("[IReader] Content Script Loaded v6");

    // Use window object to store state to avoid redeclaration errors
    window.__ireader = window.__ireader || {
        selectionMode: false,
        currentField: null,
        overlay: null,
        banner: null,
        infoPanel: null,
        lastHighlightedElement: null
    };

    const state = window.__ireader;

    // Listen for messages from popup
    chrome.runtime.onMessage.addListener(function (request, sender, sendResponse) {
        console.log("[IReader] Received message:", request.action);
        
        if (request.action === "startSelection") {
            try {
                // Clean up any existing selection UI first
                cleanup();
                
                state.selectionMode = true;
                state.currentField = request.field;
                
                console.log("[IReader] Starting selection for:", state.currentField);
                
                // Create UI elements
                createOverlay();
                createBanner();
                createInfoPanel();
                
                // Change cursor
                document.body.style.cursor = 'crosshair';
                
                // Add event listeners
                document.addEventListener('click', handleSelectionClick, true);
                document.addEventListener('mouseover', handleMouseOver, true);
                document.addEventListener('keydown', handleKeyDown, true);
                
                console.log("[IReader] Selection mode activated");
                sendResponse({ success: true });
            } catch (err) {
                console.error("[IReader] Error starting selection:", err);
                sendResponse({ success: false, error: err.message });
            }
        } else if (request.action === "testSelector") {
            sendResponse(testSelector(request.selector));
        } else if (request.action === "getPageInfo") {
            sendResponse({
                url: window.location.href,
                title: document.title,
                baseUrl: window.location.origin
            });
        } else if (request.action === "ping") {
            sendResponse({ success: true, message: "Content script is active" });
        }
        return true;
    });

    function testSelector(selector) {
        try {
            const elements = document.querySelectorAll(selector);
            return {
                success: true,
                count: elements.length,
                preview: elements.length > 0 ? elements[0].textContent?.substring(0, 100) : null
            };
        } catch (e) {
            return { success: false, error: e.message };
        }
    }

    function createOverlay() {
        // Remove existing overlay if any
        let existing = document.getElementById('ireader-selection-overlay');
        if (existing) existing.remove();
        
        state.overlay = document.createElement('div');
        state.overlay.id = 'ireader-selection-overlay';
        state.overlay.style.cssText = `
            position: fixed !important;
            pointer-events: none !important;
            background: rgba(33, 150, 243, 0.2) !important;
            border: 4px solid #2196F3 !important;
            box-shadow: 0 0 0 2px rgba(33, 150, 243, 0.5), 0 0 20px rgba(33, 150, 243, 0.4) !important;
            z-index: 2147483647 !important;
            box-sizing: border-box !important;
            border-radius: 4px !important;
            top: 0 !important;
            left: 0 !important;
            width: 0 !important;
            height: 0 !important;
            display: block !important;
            visibility: visible !important;
            opacity: 1 !important;
        `;
        document.body.appendChild(state.overlay);
        console.log("[IReader] Overlay created");
    }

    function createBanner() {
        // Remove existing banner if any
        let existing = document.getElementById('ireader-selection-banner');
        if (existing) existing.remove();

        state.banner = document.createElement('div');
        state.banner.id = 'ireader-selection-banner';
        state.banner.style.cssText = `
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            right: 0 !important;
            background: linear-gradient(135deg, #2196F3, #1976D2) !important;
            color: white !important;
            padding: 12px 20px !important;
            z-index: 2147483647 !important;
            font-family: 'Segoe UI', Arial, sans-serif !important;
            font-size: 14px !important;
            box-shadow: 0 2px 10px rgba(0,0,0,0.3) !important;
            display: flex !important;
            justify-content: space-between !important;
            align-items: center !important;
        `;
        // Append to DOM first, then update content
        document.body.appendChild(state.banner);
        updateBannerContent();
        console.log("[IReader] Banner created");
    }

    function updateBannerContent() {
        if (!state.banner) return;
        
        const fieldLabels = {
            'title': '[1/14] Novel Title',
            'author': '[2/14] Author',
            'description': '[3/14] Description',
            'cover': '[4/14] Cover Image',
            'status': '[5/14] Status',
            'genres': '[6/14] Genres',
            'chapter-item': '[7/14] Chapter Item',
            'chapter-name': '[8/14] Chapter Name',
            'chapter-link': '[9/14] Chapter Link',
            'content': '[10/14] Chapter Content',
            'novel-item': '[11/14] Novel Item',
            'explore-title': '[12/14] Novel Title (list)',
            'explore-cover': '[13/14] Novel Cover (list)',
            'explore-link': '[14/14] Novel Link (list)'
        };

        const label = fieldLabels[state.currentField] || state.currentField;
        
        // Create left side (text)
        const leftDiv = document.createElement('div');
        leftDiv.innerHTML = `
            <strong>Select: ${label}</strong>
            <span style="opacity: 0.8; margin-left: 10px; font-size: 12px;">Click to select | ESC cancel | Up/Down navigate</span>
        `;
        
        // Create right side (buttons)
        const rightDiv = document.createElement('div');
        
        const skipBtn = document.createElement('button');
        skipBtn.textContent = 'Skip';
        skipBtn.style.cssText = `
            background: rgba(255,255,255,0.2);
            border: 1px solid rgba(255,255,255,0.5);
            color: white;
            padding: 6px 16px;
            border-radius: 4px;
            cursor: pointer;
            margin-right: 8px;
            font-size: 12px;
        `;
        skipBtn.onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            skipToNextField();
        };
        
        const cancelBtn = document.createElement('button');
        cancelBtn.textContent = 'Cancel';
        cancelBtn.style.cssText = `
            background: #f44336;
            border: none;
            color: white;
            padding: 6px 16px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 12px;
        `;
        cancelBtn.onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            cleanup();
        };
        
        rightDiv.appendChild(skipBtn);
        rightDiv.appendChild(cancelBtn);
        
        // Clear and rebuild banner content
        state.banner.innerHTML = '';
        state.banner.appendChild(leftDiv);
        state.banner.appendChild(rightDiv);
    }

    function createInfoPanel() {
        let existing = document.getElementById('ireader-info-panel');
        if (existing) existing.remove();

        state.infoPanel = document.createElement('div');
        state.infoPanel.id = 'ireader-info-panel';
        state.infoPanel.style.cssText = `
            position: fixed !important;
            bottom: 20px !important;
            right: 20px !important;
            background: rgba(0, 0, 0, 0.95) !important;
            color: white !important;
            padding: 12px 16px !important;
            z-index: 2147483647 !important;
            font-family: Consolas, Monaco, monospace !important;
            font-size: 12px !important;
            border-radius: 8px !important;
            max-width: 350px !important;
            box-shadow: 0 4px 20px rgba(0,0,0,0.5) !important;
            word-break: break-all !important;
        `;
        state.infoPanel.innerHTML = '<div style="color: #4CAF50;">Hover over elements to select</div>';
        document.body.appendChild(state.infoPanel);
        console.log("[IReader] Info panel created");
    }

    function updateInfoPanel(element) {
        if (!state.infoPanel || !element) return;
        
        const selector = generateSelector(element);
        const tagName = element.tagName.toLowerCase();
        const classes = element.className && typeof element.className === 'string' 
            ? element.className.split(/\s+/).slice(0, 3).join(' ') 
            : '';
        const text = (element.textContent || '').substring(0, 40).trim();
        
        // Get useful attributes
        const attrs = getUsefulAttributes(element);
        
        let attrsHtml = '';
        if (attrs.length > 0) {
            attrsHtml = `<div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #444;">
                <div style="color: #FF9800; margin-bottom: 4px; font-weight: bold;">Attributes:</div>`;
            attrs.forEach(attr => {
                const value = (attr.value || '').substring(0, 30);
                attrsHtml += `<div style="margin-bottom: 2px; font-size: 10px;">
                    <span style="color: #4CAF50;">${attr.name}</span>: ${value}${attr.value.length > 30 ? '...' : ''}
                </div>`;
            });
            attrsHtml += '</div>';
        }
        
        state.infoPanel.innerHTML = `
            <div style="color: #4CAF50; margin-bottom: 6px; font-weight: bold;">Element Info</div>
            <div style="margin-bottom: 4px;"><span style="color: #888;">Tag:</span> &lt;${tagName}&gt;</div>
            ${classes ? `<div style="margin-bottom: 4px;"><span style="color: #888;">Class:</span> ${classes}</div>` : ''}
            <div style="margin-bottom: 4px;"><span style="color: #888;">Selector:</span> <span style="color: #2196F3;">${selector}</span></div>
            ${text ? `<div><span style="color: #888;">Text:</span> ${text}...</div>` : ''}
            ${attrsHtml}
            <div style="margin-top: 8px; font-size: 10px; color: #888;">Click to select (will ask for attribute if needed)</div>
        `;
    }
    
    function getUsefulAttributes(element) {
        const attrs = [];
        const usefulAttrs = ['href', 'src', 'data-src', 'data-lazy-src', 'data-original', 'title', 'alt', 'data-id', 'data-slug', 'data-url'];
        
        usefulAttrs.forEach(attrName => {
            const value = element.getAttribute(attrName);
            if (value) {
                attrs.push({ name: attrName, value: value });
            }
        });
        
        return attrs;
    }
    
    function getNestedElementsInfo(element) {
        const results = [];
        
        // First, check for direct <a> child or descendant (most important for links)
        const linkElement = element.querySelector('a[href]');
        if (linkElement) {
            const href = linkElement.getAttribute('href');
            const title = linkElement.getAttribute('title');
            const linkText = (linkElement.textContent || '').trim().substring(0, 40);
            
            results.push({
                selector: 'a',
                text: linkText + (linkText.length >= 40 ? '...' : ''),
                attrs: [
                    { name: 'href', value: href || '' },
                    ...(title ? [{ name: 'title', value: title }] : [])
                ]
            });
        }
        
        // Check for images
        const imgElement = element.querySelector('img');
        if (imgElement) {
            const src = imgElement.getAttribute('src') || imgElement.getAttribute('data-src') || '';
            const alt = imgElement.getAttribute('alt') || '';
            results.push({
                selector: 'img',
                text: alt || '(image)',
                attrs: [
                    { name: 'src', value: src },
                    ...(imgElement.getAttribute('data-src') ? [{ name: 'data-src', value: imgElement.getAttribute('data-src') }] : [])
                ]
            });
        }
        
        // Look for other nested elements (including p for content)
        const otherSelectors = [
            'p',
            '.chapter-title',
            '.title', 
            '.name',
            'strong',
            'h1', 'h2', 'h3', 'h4',
            'time',
            '.chapter-name',
            '.chapter-update',
            '.content',
            '.text'
        ];
        
        otherSelectors.forEach(sel => {
            const nested = element.querySelector(sel);
            if (nested && nested !== element && nested !== linkElement) {
                const text = (nested.textContent || '').trim().substring(0, 40);
                if (text.length > 0) {
                    const info = {
                        selector: sel,
                        text: text + (text.length >= 40 ? '...' : ''),
                        attrs: []
                    };
                    
                    // Check for datetime on time elements
                    if (sel === 'time') {
                        const dt = nested.getAttribute('datetime');
                        if (dt) info.attrs.push({ name: 'datetime', value: dt });
                    }
                    
                    results.push(info);
                }
            }
        });
        
        console.log('[IReader] Nested elements found:', results);
        return results;
    }
    
    function getChildElementsInfo(element) {
        const result = {
            hasChildren: false,
            count: 0,
            tagName: '',
            childSelector: '',
            preview: '',
            hasHref: false
        };
        
        // Look for repeated child elements (a, span, li, p, etc.)
        const childTags = ['a', 'span', 'li', 'p', 'div'];
        
        for (const tag of childTags) {
            const children = element.querySelectorAll(':scope > ' + tag);
            if (children.length >= 2) {
                result.hasChildren = true;
                result.count = children.length;
                result.tagName = tag;
                result.childSelector = tag;
                
                // Get preview of first few items
                const texts = [];
                children.forEach((child, i) => {
                    if (i < 3) {
                        texts.push(child.textContent.trim().substring(0, 15));
                    }
                });
                result.preview = texts.join(', ') + (children.length > 3 ? '...' : '');
                
                // Check if children have href
                if (tag === 'a' || children[0].querySelector('a')) {
                    result.hasHref = true;
                }
                
                break;
            }
        }
        
        // Also check for nested links if not direct children
        if (!result.hasChildren) {
            const links = element.querySelectorAll('a');
            if (links.length >= 2) {
                result.hasChildren = true;
                result.count = links.length;
                result.tagName = 'a';
                result.childSelector = 'a';
                result.hasHref = true;
                
                const texts = [];
                links.forEach((link, i) => {
                    if (i < 3) {
                        texts.push(link.textContent.trim().substring(0, 15));
                    }
                });
                result.preview = texts.join(', ') + (links.length > 3 ? '...' : '');
            }
        }
        
        return result;
    }

    function handleMouseOver(e) {
        if (!state.selectionMode || !state.overlay) return;

        const target = e.target;
        
        // Skip our own UI elements
        if (!target || 
            target.id === 'ireader-selection-overlay' ||
            target.id === 'ireader-selection-banner' ||
            target.id === 'ireader-info-panel' ||
            target.id === 'ireader-overlay-label' ||
            target.closest('#ireader-selection-banner') || 
            target.closest('#ireader-info-panel')) {
            return;
        }

        state.lastHighlightedElement = target;

        // Update overlay position
        const rect = target.getBoundingClientRect();
        const scrollTop = window.scrollY || document.documentElement.scrollTop;
        const scrollLeft = window.scrollX || document.documentElement.scrollLeft;
        
        // Use fixed positioning relative to viewport
        state.overlay.style.top = rect.top + 'px';
        state.overlay.style.left = rect.left + 'px';
        state.overlay.style.width = Math.max(rect.width, 20) + 'px';
        state.overlay.style.height = Math.max(rect.height, 20) + 'px';
        
        // Update or create the label showing element tag
        updateOverlayLabel(target, rect);
        
        updateInfoPanel(target);
    }
    
    function updateOverlayLabel(element, rect) {
        let label = document.getElementById('ireader-overlay-label');
        if (!label) {
            label = document.createElement('div');
            label.id = 'ireader-overlay-label';
            label.style.cssText = `
                position: fixed !important;
                background: #2196F3 !important;
                color: white !important;
                padding: 2px 8px !important;
                font-size: 11px !important;
                font-family: Consolas, monospace !important;
                border-radius: 0 0 4px 4px !important;
                z-index: 2147483647 !important;
                pointer-events: none !important;
                white-space: nowrap !important;
            `;
            document.body.appendChild(label);
        }
        
        const tagName = element.tagName.toLowerCase();
        const className = element.className && typeof element.className === 'string' 
            ? '.' + element.className.split(/\s+/)[0] 
            : '';
        label.textContent = tagName + (className.length > 1 ? className.substring(0, 20) : '');
        label.style.top = (rect.top - 20) + 'px';
        label.style.left = rect.left + 'px';
        
        // If label would be off screen, put it below
        if (rect.top < 60) {
            label.style.top = (rect.bottom + 2) + 'px';
        }
    }

    function handleSelectionClick(e) {
        if (!state.selectionMode) return;

        const target = e.target;
        
        // Exclude our UI elements
        if (!target ||
            target.id === 'ireader-selection-overlay' ||
            target.id === 'ireader-selection-banner' ||
            target.id === 'ireader-info-panel' ||
            target.id === 'ireader-attr-picker' ||
            target.closest('#ireader-selection-banner') || 
            target.closest('#ireader-info-panel') ||
            target.closest('#ireader-attr-picker')) {
            return;
        }

        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();

        const selector = generateSelector(target);
        const attrs = getUsefulAttributes(target);
        
        console.log("[IReader] Selected:", selector, "Attrs:", attrs.length);

        // Flash green
        if (state.overlay) {
            state.overlay.style.background = 'rgba(76, 175, 80, 0.5)';
            state.overlay.style.border = '3px solid #4CAF50';
        }

        // Always show attribute picker for all elements
        showAttributePicker(target, selector, attrs);
    }
    
    function showAttributePicker(element, selector, attrs) {
        // Remove existing picker
        let existing = document.getElementById('ireader-attr-picker');
        if (existing) existing.remove();
        
        const picker = document.createElement('div');
        picker.id = 'ireader-attr-picker';
        picker.style.cssText = `
            position: fixed !important;
            top: 50% !important;
            left: 50% !important;
            transform: translate(-50%, -50%) !important;
            background: white !important;
            border-radius: 12px !important;
            box-shadow: 0 10px 40px rgba(0,0,0,0.3) !important;
            z-index: 2147483647 !important;
            padding: 20px !important;
            min-width: 300px !important;
            max-width: 400px !important;
            font-family: 'Segoe UI', Arial, sans-serif !important;
        `;
        
        let optionsHtml = '';
        const tagName = element.tagName.toLowerCase();
        
        console.log('[IReader] Building picker for:', tagName, 'selector:', selector);
        
        // For list items (li), add special "Use as item selector" option first
        if (tagName === 'li') {
            optionsHtml += `
                <button class="ireader-attr-btn" data-attr="item-selector" style="
                    display: block;
                    width: 100%;
                    padding: 10px 15px;
                    margin-bottom: 8px;
                    border: 2px solid #FF5722;
                    border-radius: 8px;
                    background: #fbe9e7;
                    cursor: pointer;
                    text-align: left;
                    font-size: 13px;
                ">
                    <div style="color: #bf360c; font-weight: 600;">Use as Item Selector (li)</div>
                    <div style="color: #666; font-size: 11px; margin-top: 4px;">For chapter-item, novel-item fields</div>
                </button>
            `;
        }
        
        // Always add text option
        const textContent = (element.textContent || '').trim().substring(0, 50);
        optionsHtml += `
            <button class="ireader-attr-btn" data-attr="text" style="
                display: block;
                width: 100%;
                padding: 10px 15px;
                margin-bottom: 8px;
                border: 2px solid #4CAF50;
                border-radius: 8px;
                background: #e8f5e9;
                cursor: pointer;
                text-align: left;
                font-size: 13px;
            ">
                <div style="color: #2e7d32; font-weight: 600;">Text Content</div>
                <div style="color: #666; font-size: 11px; margin-top: 4px;">${textContent || '(empty)'}${textContent.length >= 50 ? '...' : ''}</div>
            </button>
        `;
        
        // Add innerHTML option for content fields
        optionsHtml += `
            <button class="ireader-attr-btn" data-attr="innerHTML" style="
                display: block;
                width: 100%;
                padding: 10px 15px;
                margin-bottom: 8px;
                border: 2px solid #e0e0e0;
                border-radius: 8px;
                background: #f5f5f5;
                cursor: pointer;
                text-align: left;
                font-size: 13px;
            ">
                <div style="color: #9c27b0; font-weight: 600;">HTML Content</div>
                <div style="color: #666; font-size: 11px; margin-top: 4px;">Get inner HTML (for chapter content)</div>
            </button>
        `;
        
        // Add attribute options from this element
        if (attrs && attrs.length > 0) {
            attrs.forEach(attr => {
                const value = (attr.value || '').substring(0, 50);
                optionsHtml += `
                    <button class="ireader-attr-btn" data-attr="${attr.name}" style="
                        display: block;
                        width: 100%;
                        padding: 10px 15px;
                        margin-bottom: 8px;
                        border: 2px solid #e0e0e0;
                        border-radius: 8px;
                        background: #f5f5f5;
                        cursor: pointer;
                        text-align: left;
                        font-size: 13px;
                    ">
                        <div style="color: #2196F3; font-weight: 600;">${attr.name}</div>
                        <div style="color: #666; font-size: 11px; margin-top: 4px;">${value}${attr.value.length > 50 ? '...' : ''}</div>
                    </button>
                `;
            });
        }
        
        // Check for nested elements with useful attributes (like <a> inside <li>)
        const nestedInfo = getNestedElementsInfo(element);
        if (nestedInfo.length > 0) {
            optionsHtml += `
                <div style="margin: 12px 0 8px 0; padding-top: 12px; border-top: 2px solid #e0e0e0;">
                    <div style="font-size: 12px; color: #666; margin-bottom: 8px;">Nested Elements</div>
                </div>
            `;
            
            nestedInfo.forEach(nested => {
                // Add option for nested element's text
                optionsHtml += `
                    <button class="ireader-attr-btn" data-attr="nested-text" data-nested="${nested.selector}" style="
                        display: block;
                        width: 100%;
                        padding: 10px 15px;
                        margin-bottom: 8px;
                        border: 2px solid #03A9F4;
                        border-radius: 8px;
                        background: #e1f5fe;
                        cursor: pointer;
                        text-align: left;
                        font-size: 13px;
                    ">
                        <div style="color: #0277bd; font-weight: 600;">${nested.selector} (text)</div>
                        <div style="color: #666; font-size: 11px; margin-top: 4px;">${nested.text}</div>
                    </button>
                `;
                
                // Add options for nested element's attributes
                nested.attrs.forEach(attr => {
                    optionsHtml += `
                        <button class="ireader-attr-btn" data-attr="nested-attr" data-nested="${nested.selector}" data-nested-attr="${attr.name}" style="
                            display: block;
                            width: 100%;
                            padding: 10px 15px;
                            margin-bottom: 8px;
                            border: 2px solid #03A9F4;
                            border-radius: 8px;
                            background: #e1f5fe;
                            cursor: pointer;
                            text-align: left;
                            font-size: 13px;
                        ">
                            <div style="color: #0277bd; font-weight: 600;">${nested.selector} [${attr.name}]</div>
                            <div style="color: #666; font-size: 11px; margin-top: 4px;">${attr.value.substring(0, 50)}${attr.value.length > 50 ? '...' : ''}</div>
                        </button>
                    `;
                });
            });
        }
        
        // Check for child elements (for lists like genres, tags, chapters)
        const childInfo = getChildElementsInfo(element);
        if (childInfo.hasChildren) {
            optionsHtml += `
                <div style="margin: 12px 0 8px 0; padding-top: 12px; border-top: 2px solid #e0e0e0;">
                    <div style="font-size: 12px; color: #666; margin-bottom: 8px;">Child Elements (${childInfo.count} found)</div>
                </div>
            `;
            
            // Option to select all child elements' text
            optionsHtml += `
                <button class="ireader-attr-btn" data-attr="children-text" data-child="${childInfo.childSelector}" style="
                    display: block;
                    width: 100%;
                    padding: 10px 15px;
                    margin-bottom: 8px;
                    border: 2px solid #FF9800;
                    border-radius: 8px;
                    background: #fff3e0;
                    cursor: pointer;
                    text-align: left;
                    font-size: 13px;
                ">
                    <div style="color: #e65100; font-weight: 600;">All ${childInfo.tagName} text</div>
                    <div style="color: #666; font-size: 11px; margin-top: 4px;">Selector: ${selector} ${childInfo.childSelector}</div>
                    <div style="color: #888; font-size: 10px; margin-top: 2px;">Preview: ${childInfo.preview}</div>
                </button>
            `;
            
            // If children have href, add option for that
            if (childInfo.hasHref) {
                optionsHtml += `
                    <button class="ireader-attr-btn" data-attr="children-href" data-child="${childInfo.childSelector}" style="
                        display: block;
                        width: 100%;
                        padding: 10px 15px;
                        margin-bottom: 8px;
                        border: 2px solid #FF9800;
                        border-radius: 8px;
                        background: #fff3e0;
                        cursor: pointer;
                        text-align: left;
                        font-size: 13px;
                    ">
                        <div style="color: #e65100; font-weight: 600;">All ${childInfo.tagName} links (href)</div>
                        <div style="color: #666; font-size: 11px; margin-top: 4px;">Selector: ${selector} ${childInfo.childSelector}</div>
                    </button>
                `;
            }
        }
        
        picker.innerHTML = `
            <div style="margin-bottom: 15px;">
                <div style="font-size: 16px; font-weight: 600; color: #333;">Select what to extract</div>
                <div style="font-size: 12px; color: #666; margin-top: 4px;">Selector: ${selector}</div>
            </div>
            <div style="max-height: 300px; overflow-y: auto;">
                ${optionsHtml}
            </div>
            <button id="ireader-attr-cancel" style="
                width: 100%;
                padding: 10px;
                margin-top: 10px;
                border: none;
                border-radius: 8px;
                background: #f44336;
                color: white;
                cursor: pointer;
                font-size: 13px;
            ">Cancel</button>
        `;
        
        document.body.appendChild(picker);
        
        // Add click handlers
        picker.querySelectorAll('.ireader-attr-btn').forEach(btn => {
            btn.onclick = function(e) {
                e.preventDefault();
                e.stopPropagation();
                const attr = this.getAttribute('data-attr');
                const childSelector = this.getAttribute('data-child');
                const nestedSelector = this.getAttribute('data-nested');
                const nestedAttr = this.getAttribute('data-nested-attr');
                picker.remove();
                
                let finalSelector = selector;
                let finalAttr = attr;
                
                // Handle different selection types
                if (attr === 'item-selector') {
                    // For item selectors, we just want the selector, no attribute
                    finalAttr = null;
                } else if (attr === 'children-text') {
                    finalSelector = selector + ' ' + childSelector;
                    finalAttr = 'text';
                } else if (attr === 'children-href') {
                    finalSelector = selector + ' ' + childSelector;
                    finalAttr = 'href';
                } else if (attr === 'nested-text') {
                    finalSelector = selector + ' ' + nestedSelector;
                    finalAttr = 'text';
                } else if (attr === 'nested-attr') {
                    finalSelector = selector + ' ' + nestedSelector;
                    finalAttr = nestedAttr;
                }
                
                console.log('[IReader] Final selection:', finalSelector, 'attr:', finalAttr);
                saveSelection(finalSelector, finalAttr);
            };
            // Hover effect
            btn.onmouseenter = function() { this.style.borderColor = '#2196F3'; this.style.background = '#e3f2fd'; };
            btn.onmouseleave = function() { this.style.borderColor = '#e0e0e0'; this.style.background = '#f5f5f5'; };
        });
        
        // Cancel button handler - use addEventListener for better reliability
        const cancelBtn = picker.querySelector('#ireader-attr-cancel');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                console.log('[IReader] Cancel button clicked');
                picker.remove();
                // Reset overlay color
                if (state.overlay) {
                    state.overlay.style.background = 'rgba(33, 150, 243, 0.2)';
                    state.overlay.style.border = '4px solid #2196F3';
                }
            }, true);
            
            // Also add mousedown handler as backup
            cancelBtn.addEventListener('mousedown', function(e) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
            }, true);
        } else {
            console.error('[IReader] Cancel button not found in picker');
        }
    }
    
    function saveSelection(selector, attribute) {
        chrome.storage.local.get(['sourceData'], function(result) {
            const data = result.sourceData || {};
            if (!data.selectors) data.selectors = {};
            if (!data.attributes) data.attributes = {};
            
            data.selectors[state.currentField] = selector;
            if (attribute && attribute !== 'text') {
                data.attributes[state.currentField] = attribute;
            }
            
            chrome.storage.local.set({ sourceData: data }, function() {
                console.log("[IReader] Saved selector for", state.currentField, "attr:", attribute);
                
                // Show success message in info panel instead of auto-advancing
                showSelectionResult(selector, attribute);
            });
        });
    }
    
    function showSelectionResult(selector, attribute) {
        if (!state.infoPanel) return;
        
        let attrText = ' [text]';
        if (attribute === null || attribute === 'item-selector') {
            attrText = ' (item selector)';
        } else if (attribute && attribute !== 'text') {
            attrText = ` [${attribute}]`;
        }
        
        state.infoPanel.innerHTML = `
            <div style="color: #4CAF50; margin-bottom: 10px; font-weight: bold; font-size: 14px;">Saved!</div>
            <div style="margin-bottom: 8px;">
                <span style="color: #888;">Field:</span> 
                <span style="color: #FF9800; font-weight: 600;">${state.currentField}</span>
            </div>
            <div style="margin-bottom: 8px;">
                <span style="color: #888;">Selector:</span> 
                <span style="color: #2196F3;">${selector}</span>
            </div>
            <div style="margin-bottom: 12px;">
                <span style="color: #888;">Extract:</span> 
                <span style="color: #4CAF50;">${attrText}</span>
            </div>
            <div style="border-top: 1px solid #444; padding-top: 10px;">
                <button id="ireader-next-btn" style="
                    background: #4CAF50;
                    color: white;
                    border: none;
                    padding: 8px 16px;
                    border-radius: 4px;
                    cursor: pointer;
                    margin-right: 8px;
                    font-size: 12px;
                ">Next Field</button>
                <button id="ireader-done-btn" style="
                    background: #2196F3;
                    color: white;
                    border: none;
                    padding: 8px 16px;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 12px;
                ">Done</button>
            </div>
            <div style="margin-top: 10px; font-size: 10px; color: #666;">
                Or continue selecting to replace this field
            </div>
        `;
        
        // Reset overlay to blue
        if (state.overlay) {
            state.overlay.style.background = 'rgba(33, 150, 243, 0.2)';
            state.overlay.style.border = '4px solid #2196F3';
        }
        
        // Add button handlers
        document.getElementById('ireader-next-btn').onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            skipToNextField();
        };
        
        document.getElementById('ireader-done-btn').onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            cleanup();
            alert('Selection complete! Open the extension popup to export.');
        };
    }

    function skipToNextField() {
        const fields = [
            'title', 'author', 'description', 'cover', 'status', 'genres',
            'chapter-item', 'chapter-name', 'chapter-link', 'content',
            'novel-item', 'explore-title', 'explore-cover', 'explore-link'
        ];
        const index = fields.indexOf(state.currentField);
        
        if (index !== -1 && index < fields.length - 1) {
            state.currentField = fields[index + 1];
            updateBannerContent();
            if (state.overlay) {
                state.overlay.style.background = 'rgba(33, 150, 243, 0.3)';
                state.overlay.style.border = '3px solid #2196F3';
            }
        } else {
            cleanup();
            alert('All fields selected! Open the extension popup to export.');
        }
    }

    function handleKeyDown(e) {
        if (!state.selectionMode) return;
        
        if (e.key === 'Escape') {
            cleanup();
            return;
        }
        
        if (e.key === 'Enter' && state.lastHighlightedElement) {
            e.preventDefault();
            const fakeEvent = { 
                target: state.lastHighlightedElement, 
                preventDefault: () => {}, 
                stopPropagation: () => {}, 
                stopImmediatePropagation: () => {} 
            };
            handleSelectionClick(fakeEvent);
            return;
        }
        
        if (e.key === 'ArrowUp' && state.lastHighlightedElement?.parentElement) {
            e.preventDefault();
            const parent = state.lastHighlightedElement.parentElement;
            if (parent && parent !== document.body && parent !== document.documentElement) {
                state.lastHighlightedElement = parent;
                const rect = parent.getBoundingClientRect();
                if (state.overlay) {
                    state.overlay.style.top = rect.top + 'px';
                    state.overlay.style.left = rect.left + 'px';
                    state.overlay.style.width = rect.width + 'px';
                    state.overlay.style.height = rect.height + 'px';
                }
                updateInfoPanel(parent);
            }
            return;
        }
        
        if (e.key === 'ArrowDown' && state.lastHighlightedElement?.firstElementChild) {
            e.preventDefault();
            const child = state.lastHighlightedElement.firstElementChild;
            state.lastHighlightedElement = child;
            const rect = child.getBoundingClientRect();
            if (state.overlay) {
                state.overlay.style.top = rect.top + 'px';
                state.overlay.style.left = rect.left + 'px';
                state.overlay.style.width = rect.width + 'px';
                state.overlay.style.height = rect.height + 'px';
            }
            updateInfoPanel(child);
            return;
        }
        
        if (e.key === 'Tab') {
            e.preventDefault();
            skipToNextField();
        }
    }

    function cleanup() {
        console.log("[IReader] Cleaning up");
        
        state.selectionMode = false;
        state.currentField = null;
        state.lastHighlightedElement = null;

        ['ireader-selection-overlay', 'ireader-selection-banner', 'ireader-info-panel', 'ireader-overlay-label', 'ireader-attr-picker'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.remove();
        });
        
        state.overlay = null;
        state.banner = null;
        state.infoPanel = null;

        document.body.style.cursor = '';

        document.removeEventListener('click', handleSelectionClick, true);
        document.removeEventListener('mouseover', handleMouseOver, true);
        document.removeEventListener('keydown', handleKeyDown, true);
        
        console.log("[IReader] Cleanup complete");
    }

    function generateSelector(element) {
        // Try ID first
        if (element.id && !/^\d|[0-9a-f]{8}-|[a-z0-9]{20,}|\d{10,}/i.test(element.id)) {
            return '#' + CSS.escape(element.id);
        }

        // Try data attributes
        const dataAttrs = ['data-id', 'data-slug', 'data-novel', 'itemprop', 'name'];
        for (const attr of dataAttrs) {
            const value = element.getAttribute(attr);
            if (value && !/^\d+$|[0-9a-f]{8}-/.test(value)) {
                return `${element.tagName.toLowerCase()}[${attr}="${CSS.escape(value)}"]`;
            }
        }

        // Try class
        if (element.className && typeof element.className === 'string') {
            const classes = element.className.split(/\s+/).filter(c => 
                c.length > 0 && 
                !/^(hover:|focus:|md:|lg:|sm:|flex|grid|hidden|block|inline|absolute|relative|fixed)/.test(c) &&
                !/^[mp][trblxy]?-\d|^w-\d|^h-\d|^text-\[|^bg-\[/.test(c) &&
                !/[\/\[\]:@]/.test(c)
            );
            
            // Prefer semantic classes
            const semantic = classes.find(c => 
                /title|name|author|cover|desc|content|chapter|novel|book|card|item|list|info|detail|genre|status/i.test(c)
            );
            
            if (semantic) {
                return '.' + CSS.escape(semantic);
            }
            if (classes[0]) {
                return '.' + CSS.escape(classes[0]);
            }
        }

        // Fallback to tag
        return element.tagName.toLowerCase();
    }
}
