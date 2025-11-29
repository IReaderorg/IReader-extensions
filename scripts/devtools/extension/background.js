/**
 * IReader Source Creator - Background Service Worker
 */

chrome.runtime.onInstalled.addListener((details) => {
    if (details.reason === 'install') {
        console.log('[IReader] Extension installed');
        chrome.storage.local.set({ sourceData: {} });
    }
    
    // Create context menu
    chrome.contextMenus.removeAll(() => {
        chrome.contextMenus.create({
            id: 'ireader-select',
            title: 'IReader: Select Element',
            contexts: ['all']
        });
    });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
    if (info.menuItemId === 'ireader-select' && tab) {
        chrome.action.openPopup();
    }
});

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === 'injectScripts') {
        chrome.tabs.query({ active: true, currentWindow: true }, async (tabs) => {
            if (tabs[0]) {
                try {
                    await chrome.scripting.executeScript({
                        target: { tabId: tabs[0].id },
                        files: ['ireader_core.js', 'ireader_selector.js']
                    });
                    sendResponse({ success: true });
                } catch (e) {
                    sendResponse({ success: false, error: e.message });
                }
            }
        });
        return true;
    }
});

console.log('[IReader] Background service worker ready');
