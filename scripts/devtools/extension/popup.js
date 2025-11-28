document.addEventListener('DOMContentLoaded', function () {
  // Load saved state
  chrome.storage.local.get(['sourceData'], function (result) {
    if (result.sourceData) {
      restoreState(result.sourceData);
    }
  });

  // Tab switching
  const tabBtns = document.querySelectorAll('.tab-btn');
  tabBtns.forEach(btn => {
    btn.addEventListener('click', function () {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      this.classList.add('active');
      document.getElementById(this.dataset.tab).classList.add('active');
    });
  });

  // Setup select buttons
  const selectBtns = document.querySelectorAll('.select-btn');
  selectBtns.forEach(btn => {
    btn.addEventListener('click', function () {
      const field = this.getAttribute('data-field');
      startSelection(field);
    });
  });

  // Setup delete buttons
  const deleteBtns = document.querySelectorAll('.delete-btn');
  deleteBtns.forEach(btn => {
    btn.addEventListener('click', function () {
      const field = this.getAttribute('data-field');
      clearField(field);
    });
  });

  // Input change listeners - auto-save
  const inputs = document.querySelectorAll('input[type="text"]');
  inputs.forEach(input => {
    input.addEventListener('input', debounce(saveState, 500));
  });

  // Export button
  document.getElementById('exportBtn').addEventListener('click', exportData);

  // Clear button
  document.getElementById('clearBtn').addEventListener('click', function() {
    if (confirm('Are you sure you want to clear all data?')) {
      chrome.storage.local.remove(['sourceData'], function() {
        location.reload();
      });
    }
  });

  // Test selectors button
  document.getElementById('testSelectorsBtn').addEventListener('click', testAllSelectors);

  // Auto-detect button
  document.getElementById('autoDetectBtn').addEventListener('click', autoDetectSelectors);

  // Preview button
  document.getElementById('previewBtn').addEventListener('click', showPreview);

  // Start selection flow button
  document.getElementById('selectAllBtn').addEventListener('click', function() {
    startSelection('title'); // Start with first field
  });

  // Detect API button
  const detectApiBtn = document.getElementById('detectApiBtn');
  if (detectApiBtn) {
    detectApiBtn.addEventListener('click', function() {
      alert('To detect API calls:\n\n1. Open DevTools (F12)\n2. Go to Network tab\n3. Filter by XHR/Fetch\n4. Navigate to a chapter list or load more chapters\n5. Look for API requests and copy the URL pattern\n\nCommon patterns:\n- /api/novel/{id}/chapters\n- /ajax/chapters?novel_id={id}');
    });
  }

  // Listen for storage changes (from content script)
  chrome.storage.onChanged.addListener(function(changes, namespace) {
    if (namespace === 'local' && changes.sourceData) {
      restoreState(changes.sourceData.newValue);
    }
  });
});

function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

function startSelection(field) {
  // Save current state first
  saveState();

  chrome.tabs.query({ active: true, currentWindow: true }, function (tabs) {
    if (!tabs[0]) {
      alert("No active tab found. Please open a website first.");
      return;
    }

    const tabId = tabs[0].id;
    const url = tabs[0].url;

    // Check if we can inject into this tab
    if (url.startsWith('chrome://') || url.startsWith('chrome-extension://') || url.startsWith('about:') || url.startsWith('edge://')) {
      alert("Cannot select elements on this page. Please navigate to a website.");
      return;
    }

    console.log("Starting selection for field:", field);

    // Send message to content script
    chrome.tabs.sendMessage(tabId, { action: "startSelection", field: field }, function (response) {
      if (chrome.runtime.lastError) {
        console.log("Error:", chrome.runtime.lastError.message);
        alert("Please reload the webpage (F5) and try again.\n\nThe extension needs to load on the page first.");
        return;
      }
      
      if (response && response.success) {
        console.log("Selection started successfully");
        window.close();
      } else {
        alert("Failed to start selection. Please reload the page and try again.");
      }
    });
  });
}

function updateField(field, selector) {
  chrome.storage.local.get(['sourceData'], function (result) {
    const data = result.sourceData || {};
    if (!data.selectors) data.selectors = {};
    data.selectors[field] = selector;
    chrome.storage.local.set({ sourceData: data }, function () {
      restoreState(data);
    });
  });
}

function clearField(field) {
  chrome.storage.local.get(['sourceData'], function (result) {
    const data = result.sourceData || {};
    if (data.selectors && data.selectors[field]) {
      delete data.selectors[field];
      // Also remove attribute if present
      if (data.attributes && data.attributes[field]) {
        delete data.attributes[field];
      }
      chrome.storage.local.set({ sourceData: data }, function () {
        // Update UI
        const display = document.getElementById(`${field}-selector`);
        const delBtn = document.querySelector(`.delete-btn[data-field="${field}"]`);
        const testBtn = document.querySelector(`.test-btn[data-field="${field}"]`);
        if (display) {
          display.textContent = '';
          display.classList.add('hidden');
        }
        if (delBtn) {
          delBtn.classList.add('hidden');
        }
        if (testBtn) {
          testBtn.remove();
        }
      });
    }
  });
}

function saveState() {
  chrome.storage.local.get(['sourceData'], function (result) {
    const data = result.sourceData || {};
    
    // Get values from basic inputs
    const fields = [
      'sourceName', 'lang', 'latestUrl', 'popularUrl', 'searchUrl',
      // API fields
      'chaptersApiUrl', 'chaptersJsonPath', 'chapterNameField', 'chapterUrlField',
      'contentApiUrl', 'contentJsonPath'
    ];
    
    const fieldMap = {
      'sourceName': 'name'
    };

    fields.forEach(fieldId => {
      const el = document.getElementById(fieldId);
      if (el && el.value) {
        const key = fieldMap[fieldId] || fieldId;
        data[key] = el.value;
      }
    });

    chrome.storage.local.set({ sourceData: data });
  });
}

function restoreState(data) {
  if (!data) return;

  // Field mappings (data key -> element id)
  const fieldMap = {
    'name': 'sourceName',
    'lang': 'lang',
    'latestUrl': 'latestUrl',
    'popularUrl': 'popularUrl',
    'searchUrl': 'searchUrl',
    'chaptersApiUrl': 'chaptersApiUrl',
    'chaptersJsonPath': 'chaptersJsonPath',
    'chapterNameField': 'chapterNameField',
    'chapterUrlField': 'chapterUrlField',
    'contentApiUrl': 'contentApiUrl',
    'contentJsonPath': 'contentJsonPath'
  };

  // Restore text inputs
  for (const [dataKey, elementId] of Object.entries(fieldMap)) {
    if (data[dataKey]) {
      const el = document.getElementById(elementId);
      if (el) el.value = data[dataKey];
    }
  }

  // Restore selectors
  if (data.selectors) {
    for (const [key, value] of Object.entries(data.selectors)) {
      const display = document.getElementById(`${key}-selector`);
      const delBtn = document.querySelector(`.delete-btn[data-field="${key}"]`);
      const controlActions = display ? display.parentElement : null;
      
      if (display) {
        // Show selector and attribute if present
        const attr = data.attributes && data.attributes[key];
        const displayText = attr ? `${value} [${attr}]` : value;
        display.textContent = displayText;
        display.title = displayText; // Show full selector on hover
        display.classList.remove('hidden');
        if (delBtn) delBtn.classList.remove('hidden');
        
        // Add test button if not already present
        if (controlActions && !controlActions.querySelector('.test-btn')) {
          const testBtn = document.createElement('button');
          testBtn.className = 'test-btn';
          testBtn.textContent = 'Test';
          testBtn.style.cssText = 'background: #607D8B; color: white; padding: 4px 8px; font-size: 11px;';
          testBtn.setAttribute('data-field', key);
          testBtn.addEventListener('click', function() {
            testSingleSelector(key);
          });
          // Insert before delete button
          controlActions.insertBefore(testBtn, delBtn);
        }
      }
    }
  }
  
  // Hide test buttons for fields without selectors
  document.querySelectorAll('.test-btn').forEach(btn => {
    const field = btn.getAttribute('data-field');
    if (!data.selectors || !data.selectors[field]) {
      btn.remove();
    }
  });
}

function exportData() {
  // Ensure current state is saved first
  saveState();

  setTimeout(() => {
    chrome.storage.local.get(['sourceData'], function (result) {
      const data = result.sourceData || {};

      // Validate required fields
      if (!data.name) {
        alert('Please enter a source name first.');
        return;
      }

      // Get current tab URL as base URL
      chrome.tabs.query({ active: true, currentWindow: true }, function (tabs) {
        try {
          const url = new URL(tabs[0].url);
          data.baseUrl = url.origin;
        } catch (e) {
          data.baseUrl = '';
        }

        // Add metadata
        data.exportedAt = new Date().toISOString();
        data.version = '1.0';

        // Create JSON file
        const jsonStr = JSON.stringify(data, null, 2);
        const blob = new Blob([jsonStr], { type: "application/json" });
        const urlObj = URL.createObjectURL(blob);

        const filename = `${(data.name || 'source').toLowerCase().replace(/\s+/g, '_')}_config.json`;

        chrome.downloads.download({
          url: urlObj,
          filename: filename,
          saveAs: true
        }, function(downloadId) {
          if (chrome.runtime.lastError) {
            // Fallback: copy to clipboard
            navigator.clipboard.writeText(jsonStr).then(() => {
              alert('Configuration copied to clipboard!\n\nPaste it into a .json file.');
            }).catch(() => {
              // Last resort: show in new tab
              const dataUrl = 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonStr);
              chrome.tabs.create({ url: dataUrl });
            });
          }
        });
      });
    });
  }, 100);
}

function testAllSelectors() {
  chrome.storage.local.get(['sourceData'], function(result) {
    const data = result.sourceData || {};
    const selectors = data.selectors || {};
    
    if (Object.keys(selectors).length === 0) {
      alert('No selectors to test. Please select some elements first.');
      return;
    }

    chrome.tabs.query({ active: true, currentWindow: true }, function(tabs) {
      if (!tabs[0]) return;
      
      // Check if we can test on this page
      const url = tabs[0].url;
      if (url.startsWith('chrome://') || url.startsWith('chrome-extension://') || url.startsWith('about:')) {
        alert('Cannot test selectors on this page. Please navigate to a website first.');
        return;
      }

      const resultsContainer = document.getElementById('testResultsContent');
      resultsContainer.innerHTML = '<div style="text-align: center; padding: 20px;">Testing...</div>';
      document.getElementById('testResultsModal').classList.remove('hidden');

      let results = [];
      let pending = Object.keys(selectors).length;

      for (const [field, selector] of Object.entries(selectors)) {
        chrome.tabs.sendMessage(tabs[0].id, {
          action: "testSelector",
          selector: selector
        }, function(response) {
          pending--;
          
          if (chrome.runtime.lastError || !response) {
            results.push({ field, selector, success: false, error: 'Could not test - reload page' });
          } else {
            results.push({ field, selector, ...response });
          }

          if (pending === 0) {
            displayTestResults(results);
          }
        });
      }
    });
  });
}

// Test a single selector for a specific field
function testSingleSelector(field) {
  chrome.storage.local.get(['sourceData'], function(result) {
    const data = result.sourceData || {};
    const selector = data.selectors && data.selectors[field];
    
    if (!selector) {
      alert('No selector set for this field.');
      return;
    }

    chrome.tabs.query({ active: true, currentWindow: true }, function(tabs) {
      if (!tabs[0]) return;
      
      const url = tabs[0].url;
      if (url.startsWith('chrome://') || url.startsWith('chrome-extension://') || url.startsWith('about:')) {
        alert('Cannot test on this page. Navigate to a website first.');
        return;
      }

      chrome.tabs.sendMessage(tabs[0].id, {
        action: "testSelector",
        selector: selector
      }, function(response) {
        if (chrome.runtime.lastError || !response) {
          alert(`Test failed for ${field}:\nCould not test - reload the page first.`);
        } else if (response.success && response.count > 0) {
          const preview = response.preview ? `\n\nPreview: "${response.preview.substring(0, 80)}..."` : '';
          alert(`[OK] ${field}:\nFound ${response.count} element(s)${preview}`);
        } else {
          alert(`[X] ${field}:\nNo elements found with selector:\n${selector}`);
        }
      });
    });
  });
}

function displayTestResults(results) {
  const container = document.getElementById('testResultsContent');
  container.innerHTML = results.map(r => {
    let statusClass = 'error';
    let statusIcon = '[X]';
    let statusText = r.error || 'Not found';

    if (r.success && r.count > 0) {
      if (r.count === 1) {
        statusClass = 'success';
        statusIcon = '[OK]';
        statusText = `Found 1 element`;
      } else {
        statusClass = 'warning';
        statusIcon = '[!]';
        statusText = `Found ${r.count} elements`;
      }
    }

    return `
      <div class="test-result ${statusClass}">
        <div class="field-name">${statusIcon} ${r.field}</div>
        <div class="count">${statusText}</div>
        <div style="font-size: 10px; color: #888; margin-top: 4px; word-break: break-all;">${r.selector}</div>
        ${r.preview ? `<div style="font-size: 10px; color: #666; margin-top: 4px; font-style: italic;">"${r.preview.substring(0, 50)}..."</div>` : ''}
      </div>
    `;
  }).join('');
}

function autoDetectSelectors() {
  chrome.tabs.query({ active: true, currentWindow: true }, function(tabs) {
    if (!tabs[0]) {
      alert('No active tab found.');
      return;
    }

    // Common selectors for novel sites
    const commonPatterns = {
      'title': ['.novel-title', '.book-title', 'h1.title', '.story-title', '.novel-name', 'h1'],
      'author': ['.author', '.author-name', '.writer', '[itemprop="author"]', '.novel-author'],
      'description': ['.description', '.summary', '.synopsis', '.novel-summary', '[itemprop="description"]'],
      'cover': ['.cover img', '.novel-cover img', '.book-cover img', '.thumbnail img', 'img.cover'],
      'status': ['.status', '.novel-status', '.book-status', '.state'],
      'genres': ['.genres', '.genre', '.tags', '.categories', '.novel-genres a'],
      'chapter-item': ['.chapter-item', '.chapter-list li', '.chapters li', '.chapter-row', 'ul.chapters li'],
      'chapter-name': ['.chapter-title', '.chapter-name', 'a.chapter', '.chapter-item a'],
      'chapter-link': ['.chapter-item a', '.chapter-link', 'a[href*="chapter"]'],
      'content': ['.chapter-content', '.content', '#content', '.reading-content', '.text-content'],
      'novel-item': ['.novel-item', '.book-item', '.novel-card', '.story-item', '.book-card'],
      'explore-title': ['.novel-item .title', '.book-item .name', '.novel-card h3', '.story-title'],
      'explore-cover': ['.novel-item img', '.book-item img', '.novel-card img'],
      'explore-link': ['.novel-item a', '.book-item a', '.novel-card a']
    };

    chrome.scripting.executeScript({
      target: { tabId: tabs[0].id },
      func: (patterns) => {
        const found = {};
        for (const [field, selectors] of Object.entries(patterns)) {
          for (const selector of selectors) {
            try {
              const el = document.querySelector(selector);
              if (el) {
                found[field] = selector;
                break;
              }
            } catch (e) {}
          }
        }
        return found;
      },
      args: [commonPatterns]
    }, (results) => {
      if (chrome.runtime.lastError) {
        alert('Could not auto-detect. Please reload the page and try again.');
        return;
      }

      const detected = results[0]?.result || {};
      const count = Object.keys(detected).length;

      if (count === 0) {
        alert('No common selectors detected. This site may use custom class names.\n\nPlease select elements manually.');
        return;
      }

      chrome.storage.local.get(['sourceData'], function(result) {
        const data = result.sourceData || {};
        data.selectors = { ...data.selectors, ...detected };
        chrome.storage.local.set({ sourceData: data }, function() {
          restoreState(data);
          alert(`Auto-detected ${count} selectors!\n\nPlease verify and adjust as needed.`);
        });
      });
    });
  });
}

function showPreview() {
  saveState();
  
  setTimeout(() => {
    chrome.storage.local.get(['sourceData'], function(result) {
      const data = result.sourceData || {};
      
      chrome.tabs.query({ active: true, currentWindow: true }, function(tabs) {
        try {
          const url = new URL(tabs[0].url);
          data.baseUrl = url.origin;
        } catch (e) {}

        data.exportedAt = new Date().toISOString();
        data.version = '1.0';

        document.getElementById('previewContent').textContent = JSON.stringify(data, null, 2);
        document.getElementById('previewModal').classList.remove('hidden');
      });
    });
  }, 100);
}
