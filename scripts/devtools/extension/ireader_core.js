/**
 * IReader Source Creator - Core Engine
 * Handles selector generation, page analysis, and pattern detection for novel sites
 */

class IReaderCore {
    constructor() {
        this.version = '3.0.0';
        this.init();
    }
    
    init() {
        console.log('[IReader] Core initialized');
    }
    
    // ═══════════════════════════════════════════════════════════════
    // PAGE TYPE DETECTION
    // ═══════════════════════════════════════════════════════════════
    
    detectPageType() {
        const url = window.location.href.toLowerCase();
        const bodyText = document.body.innerText.substring(0, 5000).toLowerCase();
        
        // Check for chapter content page
        const contentSelectors = [
            '.chapter-content', '#chapter-content', '.reading-content', 
            '.text-content', '.chp_raw', '#chapter__content', '.entry-content'
        ];
        for (const sel of contentSelectors) {
            const el = document.querySelector(sel);
            if (el && el.innerText.length > 2000) {
                return { type: 'chapter-content', confidence: 0.9 };
            }
        }
        
        // Check for novel detail page
        if (/\/novel[s]?\/|\/book[s]?\/|\/series\//i.test(url)) {
            const hasChapterList = document.querySelectorAll('a[href*="chapter"], .chapter-item, .chapter-list li').length > 3;
            if (hasChapterList) {
                return { type: 'novel-detail', confidence: 0.85 };
            }
        }
        
        // Check for novel list/explore page
        const novelCards = document.querySelectorAll('.novel-item, .book-item, .novel-card, .story-item, .page-item-detail');
        if (novelCards.length > 3) {
            return { type: 'novel-list', confidence: 0.85 };
        }
        
        // Check for search results
        if (/\/search|\?q=|\?query=|\?s=/i.test(url)) {
            return { type: 'search-results', confidence: 0.8 };
        }
        
        return { type: 'unknown', confidence: 0.3 };
    }
    
    // ═══════════════════════════════════════════════════════════════
    // SELECTOR GENERATION
    // ═══════════════════════════════════════════════════════════════
    
    generateSelector(element) {
        if (!element || element === document.body) return 'body';
        
        // Try ID first
        if (element.id && !this.isGeneratedId(element.id)) {
            return '#' + CSS.escape(element.id);
        }
        
        // Try unique class
        const classSelector = this.findUniqueClassSelector(element);
        if (classSelector) return classSelector;
        
        // Try attribute selector
        const attrSelector = this.findAttributeSelector(element);
        if (attrSelector) return attrSelector;
        
        // Build path selector
        return this.buildPathSelector(element);
    }
    
    isGeneratedId(id) {
        return /^[a-f0-9]{8,}$/i.test(id) || 
               /^(ember|react-|ng-|vue-|:r)\d*/i.test(id) ||
               /^\d+$/.test(id);
    }
    
    findUniqueClassSelector(element) {
        if (!element.classList || element.classList.length === 0) return null;
        
        const tag = element.tagName.toLowerCase();
        const validClasses = Array.from(element.classList).filter(c => 
            c.length > 1 && 
            !/^(hover|focus|active|sm:|md:|lg:|xl:)/.test(c) &&
            !/[\/\[\]:@]/.test(c)
        );
        
        for (const cls of validClasses) {
            const selector = tag + '.' + CSS.escape(cls);
            if (document.querySelectorAll(selector).length === 1) {
                return selector;
            }
        }
        
        for (const cls of validClasses) {
            const selector = '.' + CSS.escape(cls);
            if (document.querySelectorAll(selector).length === 1) {
                return selector;
            }
        }
        
        return null;
    }
    
    findAttributeSelector(element) {
        const attrs = ['data-id', 'data-type', 'data-name', 'name', 'role', 'itemprop'];
        for (const attr of attrs) {
            const value = element.getAttribute(attr);
            if (value && value.length < 50) {
                const selector = `[${attr}="${CSS.escape(value)}"]`;
                if (document.querySelectorAll(selector).length === 1) {
                    return selector;
                }
            }
        }
        return null;
    }
    
    buildPathSelector(element, maxDepth = 3) {
        const path = [];
        let current = element;
        let depth = 0;
        
        while (current && current !== document.body && depth < maxDepth) {
            let segment = current.tagName.toLowerCase();
            
            if (current.classList && current.classList.length > 0) {
                const validClass = Array.from(current.classList).find(c => 
                    c.length > 1 && !/[\/\[\]:@]/.test(c)
                );
                if (validClass) {
                    segment += '.' + CSS.escape(validClass);
                }
            }
            
            path.unshift(segment);
            current = current.parentElement;
            depth++;
        }
        
        return path.join(' > ');
    }
    
    generateRelativeSelector(parent, child) {
        if (!parent.contains(child)) return null;
        
        const tag = child.tagName.toLowerCase();
        
        // Try simple tag
        if (parent.querySelectorAll(tag).length === 1) return tag;
        
        // Try tag with class
        if (child.classList && child.classList.length > 0) {
            for (const cls of child.classList) {
                if (cls.length > 1 && !/[\/\[\]:@]/.test(cls)) {
                    const selector = tag + '.' + CSS.escape(cls);
                    if (parent.querySelectorAll(selector).length === 1) return selector;
                }
            }
            for (const cls of child.classList) {
                if (cls.length > 1 && !/[\/\[\]:@]/.test(cls)) {
                    const selector = '.' + CSS.escape(cls);
                    if (parent.querySelectorAll(selector).length === 1) return selector;
                }
            }
        }
        
        // Try with attribute
        if (child.hasAttribute('href')) {
            const selector = `${tag}[href]`;
            if (parent.querySelectorAll(selector).length === 1) return selector;
        }
        
        return tag;
    }

    
    // ═══════════════════════════════════════════════════════════════
    // DATA REGION DETECTION
    // ═══════════════════════════════════════════════════════════════
    
    findDataRegions() {
        const regions = [];
        const containers = document.querySelectorAll('div, section, ul, ol, table, main');
        
        containers.forEach(container => {
            const children = Array.from(container.children);
            if (children.length < 3) return;
            
            // Check if children have similar structure
            const similarity = this.calculateChildSimilarity(children);
            
            if (similarity > 0.7) {
                const type = this.classifyRegion(container, children);
                regions.push({
                    element: container,
                    selector: this.generateSelector(container),
                    itemCount: children.length,
                    similarity: similarity,
                    type: type,
                    itemSelector: this.generateItemSelector(container, children),
                    fields: this.extractFields(children[0])
                });
            }
        });
        
        return regions.sort((a, b) => b.itemCount - a.itemCount).slice(0, 10);
    }
    
    calculateChildSimilarity(children) {
        if (children.length < 2) return 0;
        
        let matches = 0;
        const first = children[0];
        
        for (let i = 1; i < Math.min(children.length, 5); i++) {
            const child = children[i];
            if (first.tagName === child.tagName) matches++;
            if (first.children.length === child.children.length) matches++;
            if ((first.querySelector('a') !== null) === (child.querySelector('a') !== null)) matches++;
            if ((first.querySelector('img') !== null) === (child.querySelector('img') !== null)) matches++;
        }
        
        return matches / (Math.min(children.length - 1, 4) * 4);
    }
    
    classifyRegion(container, children) {
        const sample = children[0];
        const text = sample.innerText.toLowerCase();
        const className = container.className.toLowerCase();
        
        if (/chapter|ch\.|episode/i.test(text) || className.includes('chapter')) {
            return 'chapter-list';
        }
        
        if (sample.querySelector('img') && sample.querySelectorAll('a').length > 0) {
            return 'novel-list';
        }
        
        return 'data-list';
    }
    
    generateItemSelector(container, children) {
        if (children.length === 0) return null;
        
        const sample = children[0];
        const tag = sample.tagName.toLowerCase();
        
        if (container.querySelectorAll(':scope > ' + tag).length === children.length) {
            return tag;
        }
        
        if (sample.classList && sample.classList.length > 0) {
            for (const cls of sample.classList) {
                if (cls.length > 1 && !/[\/\[\]:@]/.test(cls)) {
                    const selector = tag + '.' + CSS.escape(cls);
                    if (container.querySelectorAll(selector).length === children.length) {
                        return selector;
                    }
                }
            }
        }
        
        return tag;
    }
    
    extractFields(sampleItem) {
        const fields = [];
        
        // Find title
        const titleCandidates = sampleItem.querySelectorAll('h1, h2, h3, h4, a, .title, .name, strong');
        for (const el of titleCandidates) {
            const text = el.innerText.trim();
            if (text.length > 3 && text.length < 200) {
                fields.push({
                    type: 'title',
                    selector: this.generateRelativeSelector(sampleItem, el),
                    sample: text.substring(0, 50)
                });
                break;
            }
        }
        
        // Find link
        const link = sampleItem.querySelector('a[href]');
        if (link) {
            fields.push({
                type: 'link',
                selector: this.generateRelativeSelector(sampleItem, link),
                attribute: 'href',
                sample: link.getAttribute('href')
            });
        }
        
        // Find image
        const img = sampleItem.querySelector('img');
        if (img) {
            const src = img.getAttribute('src') || img.getAttribute('data-src');
            fields.push({
                type: 'image',
                selector: this.generateRelativeSelector(sampleItem, img),
                attribute: img.getAttribute('data-src') ? 'data-src' : 'src',
                sample: src
            });
        }
        
        return fields;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // AUTO-DETECTION
    // ═══════════════════════════════════════════════════════════════
    
    autoDetectSelectors() {
        const detected = {};
        const pageType = this.detectPageType();
        
        // Common selectors for novel sites
        const patterns = {
            'title': ['h1.novel-title', 'h1.title', 'h1', '.post-title h1', '.book-title', 'div.fic_title'],
            'author': ['.author a', '.author-name', '.author', '[itemprop="author"]', 'span.auth_name_fic'],
            'description': ['.description', '.synopsis', '.summary', '[itemprop="description"]', '.desc-text', 'div.wi_fic_desc'],
            'cover': ['.cover img', '.novel-cover img', 'figure.cover img', 'div.summary_image img', 'div.fic_image img'],
            'status': ['.status', '.novel-status', 'div.post-status', 'span.rnd_stats'],
            'genres': ['.genres a', '.genre a', '.tags a', '.categories a', 'span.wi_fic_genre span'],
            'chapter-item': ['li.wp-manga-chapter', '.chapter-list li', '.chapter-item', 'ul.list-chapter li', 'ol.toc_ol li'],
            'content': ['.chapter-content', '#chapter-content', '.reading-content', '.text-content', 'div.chp_raw', '#content'],
            'novel-item': ['.page-item-detail', '.novel-item', '.book-item', 'div.search_main_box', '.story-item']
        };
        
        for (const [field, selectors] of Object.entries(patterns)) {
            for (const selector of selectors) {
                try {
                    const el = document.querySelector(selector);
                    if (el) {
                        // Validate the element has content
                        if (field === 'cover' && !el.src && !el.getAttribute('data-src')) continue;
                        if (field === 'content' && el.innerText.length < 500) continue;
                        if (field === 'chapter-item' && document.querySelectorAll(selector).length < 3) continue;
                        
                        detected[field] = selector;
                        break;
                    }
                } catch (e) {}
            }
        }
        
        // Detect relative selectors for list items
        if (detected['chapter-item']) {
            const item = document.querySelector(detected['chapter-item']);
            if (item) {
                const link = item.querySelector('a');
                if (link) {
                    detected['chapter-name'] = 'a';
                    detected['chapter-link'] = 'a';
                }
            }
        }
        
        if (detected['novel-item']) {
            const item = document.querySelector(detected['novel-item']);
            if (item) {
                const titleEl = item.querySelector('h3 a, .title a, .post-title a, a.title');
                const coverEl = item.querySelector('img');
                const linkEl = item.querySelector('a');
                
                if (titleEl) detected['explore-title'] = this.generateRelativeSelector(item, titleEl);
                if (coverEl) {
                    detected['explore-cover'] = this.generateRelativeSelector(item, coverEl);
                    if (coverEl.getAttribute('data-src')) {
                        detected['explore-cover-attr'] = 'data-src';
                    }
                }
                if (linkEl) detected['explore-link'] = this.generateRelativeSelector(item, linkEl);
            }
        }
        
        return detected;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // KOTLIN CODE GENERATION
    // ═══════════════════════════════════════════════════════════════
    
    generateKotlinSource(config) {
        const className = (config.name || 'NewSource').replace(/[^a-zA-Z0-9]/g, '');
        const packageName = className.toLowerCase();
        
        return `package ireader.${packageName}

import ireader.core.source.HttpSource
import ireader.core.source.model.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ${className} : HttpSource() {
    
    override val name = "${config.name || 'New Source'}"
    override val baseUrl = "${config.baseUrl || ''}"
    override val lang = "${config.lang || 'en'}"
    
    // Novel List
    override fun popularMangaSelector() = "${config.selectors?.['novel-item'] || '.novel-item'}"
    
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst("${config.selectors?.['explore-title'] || 'a'}")?.text() ?: ""
            setUrlWithoutDomain(element.selectFirst("${config.selectors?.['explore-link'] || 'a'}")?.attr("href") ?: "")
            thumbnail_url = element.selectFirst("${config.selectors?.['explore-cover'] || 'img'}")?.attr("${config.attributes?.['explore-cover'] || 'src'}")
        }
    }
    
    // Novel Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst("${config.selectors?.['title'] || 'h1'}")?.text() ?: ""
            author = document.selectFirst("${config.selectors?.['author'] || '.author'}")?.text()
            description = document.selectFirst("${config.selectors?.['description'] || '.description'}")?.text()
            thumbnail_url = document.selectFirst("${config.selectors?.['cover'] || 'img'}")?.attr("${config.attributes?.['cover'] || 'src'}")
            status = parseStatus(document.selectFirst("${config.selectors?.['status'] || '.status'}")?.text())
            genre = document.select("${config.selectors?.['genres'] || '.genres a'}").joinToString { it.text() }
        }
    }
    
    private fun parseStatus(status: String?): Int {
        return when {
            status == null -> SManga.UNKNOWN
            status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
            status.contains("completed", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
    
    // Chapters
    override fun chapterListSelector() = "${config.selectors?.['chapter-item'] || '.chapter-item'}"
    
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.selectFirst("${config.selectors?.['chapter-name'] || 'a'}")?.text() ?: ""
            setUrlWithoutDomain(element.selectFirst("${config.selectors?.['chapter-link'] || 'a'}")?.attr("href") ?: "")
        }
    }
    
    // Chapter Content
    override fun pageListParse(document: Document): List<Page> {
        val content = document.selectFirst("${config.selectors?.['content'] || '.chapter-content'}") ?: return emptyList()
        return listOf(Page(0, "", content.html()))
    }
}
`;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════
    
    testSelector(selector) {
        try {
            const elements = document.querySelectorAll(selector);
            return {
                success: true,
                count: elements.length,
                preview: elements.length > 0 ? elements[0].textContent?.substring(0, 100).trim() : null
            };
        } catch (e) {
            return { success: false, error: e.message };
        }
    }
    
    getElementInfo(element) {
        const selector = this.generateSelector(element);
        const count = document.querySelectorAll(selector).length;
        
        return {
            tag: element.tagName.toLowerCase(),
            selector: selector,
            matchCount: count,
            text: element.innerText.substring(0, 100).trim(),
            attributes: this.getUsefulAttributes(element)
        };
    }
    
    getUsefulAttributes(element) {
        const attrs = [];
        const useful = ['href', 'src', 'data-src', 'data-id', 'title', 'alt', 'datetime'];
        
        for (const attr of useful) {
            const value = element.getAttribute(attr);
            if (value) {
                attrs.push({ name: attr, value: value.substring(0, 100) });
            }
        }
        
        return attrs;
    }
}

// Create global instance
window.IReaderCore = new IReaderCore();
