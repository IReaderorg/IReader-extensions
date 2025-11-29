# IReader Source Creator

Browser extension for creating IReader novel sources by selecting elements from websites.

## Installation

1. Open Chrome/Edge → `chrome://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked"
4. Select the `scripts/devtools/extension` folder

## Usage

### Creating a Source

1. Navigate to a novel website
2. Click the extension icon
3. Enter source name and language
4. Go to each tab and select elements:
   - **Details**: Title, author, description, cover, status, genres
   - **Chapters**: Chapter item, name, link, content
   - **Explore**: Novel card, title, cover, link
5. Click "Export Kotlin" to generate source code

### Selecting Elements

1. Click "Select" next to any field
2. Hover over elements on the page
3. Use ↑↓ arrows to navigate parent/child
4. Click to select
5. Press ESC to cancel

### Auto-Detection

Click "Auto-Detect" to automatically find common selectors.

### Testing

Click "Test All" to verify all selectors work on the current page.

## Tabs

| Tab | Description |
|-----|-------------|
| General | Source name, language, URLs |
| Details | Novel detail page selectors |
| Chapters | Chapter list and content selectors |
| Explore | Novel list page selectors |
| Log | Activity log showing all actions |
| Settings | Gemini API key, options |

## Settings

### Gemini AI

Enter your Gemini API key to enable AI-powered features:
- **AI Analyze Page**: Uses AI to analyze page structure and suggest selectors
- **AI Generate Selectors**: AI-assisted selector generation

Get your API key from: https://makersuite.google.com/app/apikey

### Options

- **Reverse chapter order**: Reverse the chapter list order
- **Add base URL**: Add base URL to relative links

## Selector Fields

| Field | Description |
|-------|-------------|
| title | Novel title on detail page |
| author | Author name |
| description | Synopsis |
| cover | Cover image |
| status | Publication status |
| genres | Genre/tag list |
| chapter-item | Chapter list item |
| chapter-name | Chapter title (relative) |
| chapter-link | Chapter URL (relative) |
| content | Chapter content container |
| novel-item | Novel card on list pages |
| explore-title | Novel title in list |
| explore-cover | Novel cover in list |
| explore-link | Novel URL in list |

## Export Formats

- **Kotlin**: Ready-to-use IReader source class
- **JSON**: Configuration file for manual editing

## Files

- `ireader_core.js` - Selector generation and page analysis
- `ireader_selector.js` - Visual element selection
- `popup_ireader.html/js` - Extension popup
- `background.js` - Service worker
