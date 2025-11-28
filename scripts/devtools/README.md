# IReader Source Creator - DevTools

A Chrome extension and Python scripts to help developers create IReader sources by visually selecting elements on novel websites.

## 🚀 Quick Start

### Windows
```batch
cd scripts\devtools
start.bat
```

### Linux/Mac
```bash
cd scripts/devtools
python launcher.py
```

## 📋 Overview

This tool simplifies the process of creating IReader sources:

1. **Chrome Extension**: Visually select elements on any novel website
2. **Export Configuration**: Save selectors as JSON
3. **Python Script**: Generate Kotlin source code using Gemini AI

## 🔧 Installation

### 1. Install the Chrome Extension

1. Open Chrome and go to `chrome://extensions/`
2. Enable "Developer mode" (toggle in top right)
3. Click "Load unpacked"
4. Select the `scripts/devtools/extension` folder
5. Pin the extension for easy access

### 2. Set up Gemini API Key

```powershell
# Windows PowerShell
$env:GEMINI_API_KEY='your-key-here'

# Or add to your profile for persistence
```

Get your API key from: https://makersuite.google.com/app/apikey

## 📖 Usage

### Step 1: Open the Target Website

Navigate to the novel website you want to create a source for:
- https://www.realmnovel.com/
- https://rewayatfans.com/
- Any other novel website

### Step 2: Configure Basic Info

1. Click the IReader extension icon
2. Enter the **Source Name** (e.g., "RealmNovel")
3. Enter the **Language** (e.g., "en", "ar")
4. Fill in the **URL patterns**:
   - Latest: `/novels?sort=latest&page={{page}}`
   - Popular: `/novels?sort=popular&page={{page}}`
   - Search: `/search?q={{query}}&page={{page}}`

### Step 3: Select Elements

Navigate to different pages and select elements:

#### On Novel List Page (Latest/Popular)
- **Novel Item**: The container for each novel card
- **Novel Title**: The title element within the card
- **Novel Cover**: The cover image
- **Novel Link**: The link to the novel detail page

#### On Novel Detail Page
- **Title**: The novel's title
- **Author**: The author name
- **Description**: The synopsis/summary
- **Cover Image**: The cover image
- **Status**: Ongoing/Completed status
- **Genres**: Genre tags/categories

#### On Chapter List (same page or separate)
- **Chapter Item**: Container for each chapter
- **Chapter Name**: The chapter title
- **Chapter Link**: Link to the chapter

#### On Chapter Content Page
- **Content**: The main content container

### Step 4: Export & Generate

1. Click **"Export Configuration"** to save the JSON file
2. Run the generator:
   ```bash
   python scripts/devtools/create_source_interactive.py your_config.json
   ```

## 🛠️ Extension Features

### Element Selection
- **Visual Highlighting**: Hover to see what will be selected
- **Info Panel**: Shows element details (tag, class, selector)
- **Smart Selectors**: Generates stable CSS selectors
- **Skip Button**: Skip fields you don't need
- **ESC to Cancel**: Press Escape to exit selection mode

### Tools
- **🧪 Test Selectors**: Verify all selectors work on the current page
- **🔍 Auto-Detect**: Automatically find common selectors
- **👁️ Preview**: Preview the configuration before export

### Selector Generation
The extension generates stable selectors by:
1. Preferring IDs (if not dynamic)
2. Using data attributes (data-id, itemprop, etc.)
3. Finding semantic class names (avoiding utility classes)
4. Falling back to tag + nth-of-type

## 📁 File Structure

```
scripts/devtools/
├── extension/
│   ├── manifest.json       # Extension configuration
│   ├── popup.html          # Extension popup UI
│   ├── popup.js            # Popup logic
│   ├── content_script.js   # Element selection logic
│   └── images/             # Extension icons
├── create_source_interactive.py  # Kotlin code generator
├── launcher.py             # Interactive launcher
├── start.bat               # Windows launcher
└── README.md               # This file
```

## 📄 Configuration Format

```json
{
  "name": "RealmNovel",
  "lang": "en",
  "baseUrl": "https://www.realmnovel.com",
  "latestUrl": "/novels?sort=latest&page={{page}}",
  "popularUrl": "/novels?sort=popular&page={{page}}",
  "searchUrl": "/search?q={{query}}&page={{page}}",
  "selectors": {
    "title": ".novel-title",
    "author": ".author-name",
    "description": ".novel-summary",
    "cover": ".novel-cover img",
    "status": ".novel-status",
    "genres": ".genre-tags a",
    "chapter-item": ".chapter-list li",
    "chapter-name": ".chapter-title",
    "chapter-link": ".chapter-link",
    "content": ".chapter-content",
    "novel-item": ".novel-card",
    "explore-title": ".novel-card .title",
    "explore-cover": ".novel-card img",
    "explore-link": ".novel-card a"
  },
  "exportedAt": "2024-01-15T10:30:00.000Z",
  "version": "1.0"
}
```

## 💡 Tips

### Selecting Elements
1. **Be Specific**: Select the most specific element containing the data
2. **Check Multiple Pages**: Verify selectors work on different novels
3. **Avoid Dynamic Classes**: Classes with numbers may change

### URL Patterns
- Use `{{page}}` for pagination
- Use `{{query}}` for search queries
- Check the website's actual URL structure

### Common Issues

**Nothing happens when clicking "Select"**
1. Reload the target webpage (F5 or Ctrl+R)
2. Go to chrome://extensions/ and click the refresh icon on the extension
3. Try again
4. Open DevTools (F12) > Console to check for errors

**"Please reload the webpage"**
- The content script needs to be injected. Reload the page after installing.

**Blue highlight box not showing**
- Make sure you're on a regular website (not chrome:// or extension pages)
- Check the browser console for JavaScript errors

**Selector not working**
- Try selecting a parent element
- Some sites use JavaScript rendering - selectors may need adjustment

**Extension not appearing**
- Ensure Developer mode is enabled
- Check for errors in chrome://extensions/

## 🔄 Workflow Example

```bash
# 1. Set API key
$env:GEMINI_API_KEY='your-key'

# 2. Run launcher
python scripts/devtools/launcher.py

# 3. Choose option 1 to open website
# 4. Select elements using the extension
# 5. Export configuration
# 6. Choose option 2 to generate source

# 7. Build the extension
./gradlew :extensions:v5:en:realmnovel:assembleDebug

# 8. Test in IReader app
```

## 🧪 Testing

After generating a source, test it:

```bash
# Run tests for the source
./gradlew :test-extensions:test --tests "*RealmNovel*"
```

## 📝 Generated Output

The generator creates:
```
sources-v5-batch/
└── en/
    └── realmnovel/
        ├── main/
        │   ├── src/ireader/realmnovel/
        │   │   └── RealmNovel.kt
        │   └── assets/
        ├── build.gradle.kts
        └── README.md
```

## 🤝 Contributing

1. Test on various novel websites
2. Report issues with specific selectors
3. Suggest improvements to the selector generation

## 📜 License

Part of the IReader Extensions project.
