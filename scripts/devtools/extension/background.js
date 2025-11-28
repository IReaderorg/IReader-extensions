// IReader Source Creator - Background Script

// Handle extension installation
chrome.runtime.onInstalled.addListener((details) => {
  if (details.reason === 'install') {
    console.log('IReader Source Creator installed');
    // Initialize storage
    chrome.storage.local.set({ sourceData: {} });
  }
});

// Handle messages from popup or content script
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'injectContentScript') {
    // Inject content script into the active tab
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]) {
        chrome.scripting.executeScript({
          target: { tabId: tabs[0].id },
          files: ['content_script.js']
        }).then(() => {
          sendResponse({ success: true });
        }).catch((error) => {
          sendResponse({ success: false, error: error.message });
        });
      }
    });
    return true; // Keep channel open for async response
  }
});

// Context menu for quick access
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'ireader-select-element',
    title: 'Select this element for IReader',
    contexts: ['all']
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === 'ireader-select-element') {
    // Open popup or start selection
    chrome.action.openPopup();
  }
});
