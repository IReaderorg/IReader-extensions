# IReader Selector Helper - Complete Guide

A beginner-friendly Chrome extension to find CSS selectors for creating IReader novel sources.

---

## 📖 Table of Contents

1. [What is This?](#what-is-this)
2. [Installation](#installation)
3. [How to Use](#how-to-use)
4. [Understanding the Results](#understanding-the-results)
5. [Finding Selectors for IReader](#finding-selectors-for-ireader)
6. [Tips & Tricks](#tips--tricks)
7. [Troubleshooting](#troubleshooting)
8. [FAQ](#faq)

---

## What is This?

When creating an IReader source, you need to tell the app WHERE to find information on a website. For example:
- Where is the novel title?
- Where is the author name?
- Where is the chapter content?

This is done using **CSS selectors** - special codes that identify elements on a webpage.

**This extension helps you find those selectors without knowing any code!**

### Before vs After

**Before (the hard way):**
1. Open browser DevTools (F12)
2. Inspect elements manually
3. Figure out the right CSS selector
4. Hope it works...

**After (with this extension):**
1. Click the extension icon
2. Draw a box around what you want
3. Copy the selector
4. Done! ✅

---

## Installation

### Step 1: Download the Extension

The extension is located in your project at:
```
scripts/devtools/extension/
```

### Step 2: Open Chrome Extensions Page

1. Open Google Chrome
2. Type `chrome://extensions/` in the address bar
3. Press Enter

![Chrome Extensions URL](https://via.placeholder.com/600x100?text=Type+chrome://extensions/+in+address+bar)

### Step 3: Enable Developer Mode

Look for the "Developer mode" toggle in the top-right corner and turn it ON.

```
┌─────────────────────────────────────────────────────────┐
│  Extensions                              [Developer mode: ON] │
└─────────────────────────────────────────────────────────┘
```

### Step 4: Load the Extension

1. Click the **"Load unpacked"** button
2. Navigate to `scripts/devtools/extension/` folder
3. Click "Select Folder"

### Step 5: Pin the Extension (Recommended)

1. Click the puzzle piece icon (🧩) in Chrome toolbar
2. Find "IReader Selector Helper"
3. Click the pin icon (📌) to keep it visible

**You're done! The IReader icon should now appear in your toolbar.**

---

## How to Use

### The Basic Flow

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  1. Go to    │ -> │  2. Click    │ -> │  3. Select   │ -> │  4. Copy     │
│  a website   │    │  extension   │    │  an area     │    │  selector    │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
```

### Step-by-Step Instructions

#### Step 1: Go to a Novel Website

Open any novel website in Chrome. For example:
- A novel list page
- A novel detail page
- A chapter content page

#### Step 2: Click the Extension Icon

Click the IReader icon in your Chrome toolbar. You'll see:

```
┌─────────────────────────────────────────────────────────────────┐
│ 🎯 IReader Selector Helper                        [Cancel (ESC)]│
│ Drag to select an area, or click on an element to analyze it   │
└─────────────────────────────────────────────────────────────────┘
```

The page will dim slightly - this means selection mode is active!

#### Step 3: Select What You Want

**Option A: Drag to Select (Recommended)**
1. Click and hold your mouse button
2. Drag to draw a box around the area you want
3. Release the mouse button

```
    ┌─────────────────────────┐
    │  Novel Title Here       │  <- Draw a box around this
    │  by Author Name         │
    │  ⭐⭐⭐⭐⭐              │
    └─────────────────────────┘
```

**Option B: Click to Select**
- Just click on any element to analyze it

#### Step 4: Browse the Results

A panel appears on the right side showing all found selectors:

```
┌─────────────────────────────────────────┐
│ Found Selectors                      [X]│
├─────────────────────────────────────────┤
│ 🔍 Search selectors...                  │
├─────────────────────────────────────────┤
│ [Title] [Author] [Cover] [Chapter] ...  │
├─────────────────────────────────────────┤
│ ⚡ Quick Copy (Best Matches)            │
│ [✓ .novel-title] [✓ h1.title]          │
├─────────────────────────────────────────┤
│ ✓ .novel-title                    👁🎯📋│
│   Matches: 1  Type: class               │
├─────────────────────────────────────────┤
│ ~ h1                              👁🎯📋│
│   Matches: 3  Type: tag                 │
├─────────────────────────────────────────┤
│ Showing 15 of 15 selectors              │
│ [🎯 Select Again] [📥 Export All]       │
└─────────────────────────────────────────┘
```

#### Step 5: Copy the Selector You Need

1. Find a selector with a **green checkmark** (✓) - these are the best!
2. Click the **📋 copy button** to copy it
3. Paste it into your IReader source code

---

## Understanding the Results

### The Checkmark System

| Symbol | Color | Meaning | Should I Use It? |
|--------|-------|---------|------------------|
| ✓ | Green | Unique - only 1 element matches | **YES! Best choice** |
| ~ | Yellow | Few matches (2-5 elements) | Maybe, check preview first |
| • | Gray | Many matches (6+ elements) | Usually not ideal |

### The Green Border

Selectors with a **green left border** are recommended - they have high scores based on:
- Uniqueness
- Meaningful names
- Reliability

### Action Buttons

| Button | What it Does |
|--------|--------------|
| 👁 Preview | Shows what text/content this selector extracts |
| 🎯 Highlight | Highlights matching elements on the page (yellow outline) |
| 📋 Copy | Copies the selector to your clipboard |

### Quick Copy Section

The **"⚡ Quick Copy"** section at the top shows the best selectors. Just click one to copy it instantly!

### Field Hints

Click these buttons to filter selectors by type:

| Hint | What it Finds |
|------|---------------|
| Title | Selectors containing "title", "name", "h1", etc. |
| Author | Selectors containing "author", "writer" |
| Cover | Selectors containing "cover", "img", "image" |
| Description | Selectors containing "desc", "synopsis", "summary" |
| Chapter | Selectors containing "chapter", "chap", "episode" |
| Content | Selectors containing "content", "text", "reading" |
| Link | Selectors containing "link", "href", "a" |
| Status | Selectors containing "status", "ongoing", "complete" |
| Genre | Selectors containing "genre", "tag", "category" |

---

## Finding Selectors for IReader

When creating an IReader source, you need selectors for different pages. Here's what to look for:

### 📚 Novel List Page (Browse/Latest/Popular)

Go to a page that shows a list of novels, then select one novel card:

| What to Find | Example Selector | What it Does |
|--------------|------------------|--------------|
| Novel Card | `.novel-item` | Container for each novel |
| Title | `.novel-item .title` | Novel name in the card |
| Cover | `.novel-item img` | Cover image |
| Link | `.novel-item a` | Link to novel detail page |

### 📖 Novel Detail Page

Go to a specific novel's page:

| What to Find | Example Selector | What it Does |
|--------------|------------------|--------------|
| Title | `h1.novel-title` | The novel's title |
| Author | `.author-name` | Author's name |
| Description | `.synopsis` | Novel summary/description |
| Cover | `.cover-image img` | Cover image |
| Status | `.status` | Ongoing/Completed |
| Genres | `.genres a` | Genre tags |

### 📑 Chapter List

On the novel detail page or chapter list page:

| What to Find | Example Selector | What it Does |
|--------------|------------------|--------------|
| Chapter Item | `.chapter-list li` | Container for each chapter |
| Chapter Name | `.chapter-list li a` | Chapter title |
| Chapter Link | `.chapter-list li a` | Link to chapter |

### 📄 Chapter Content Page

Go to an actual chapter page:

| What to Find | Example Selector | What it Does |
|--------------|------------------|--------------|
| Content | `.chapter-content` | The chapter text |
| Chapter Title | `.chapter-title` | Title of current chapter |

---

## Tips & Tricks

### 🎯 Tip 1: Always Look for Green Checkmarks

Green checkmarks (✓) mean the selector matches exactly ONE element. This is what you want!

### 🔍 Tip 2: Use Preview Before Copying

Click the 👁 button to see what content the selector extracts. Make sure it's what you expect!

### 🎨 Tip 3: Use Highlight to Verify

Click the 🎯 button to highlight matching elements on the page. This helps you see exactly what will be selected.

### 📝 Tip 4: Prefer Class Selectors

Selectors starting with `.` (like `.novel-title`) are usually more reliable than tag selectors (like `h1`).

### 🔄 Tip 5: Test on Multiple Pages

A good selector should work on different novels/chapters on the same website. Test it!

### 📦 Tip 6: Select Larger Areas

If you're not finding good selectors, try selecting a larger area. The extension will find more options.

### ⌨️ Tip 7: Use Keyboard Shortcuts

- **ESC** - Cancel selection mode
- **Click extension icon again** - Restart selection

---

## Troubleshooting

### "Nothing happens when I click the extension"

**Solution:**
1. Make sure you're on a regular website (not `chrome://` pages)
2. Refresh the page (F5)
3. Try clicking the extension again

### "I don't see any selectors"

**Solution:**
1. Make sure your selection area is large enough (at least 20x20 pixels)
2. Try clicking directly on an element instead of dragging
3. The area might only contain scripts/styles - try a different area

### "The selector doesn't work in my IReader source"

**Possible reasons:**
1. **Dynamic content** - Some sites load content with JavaScript after the page loads
2. **Different pages** - The selector might not exist on all pages
3. **Site updates** - Websites change their structure sometimes

**Solutions:**
1. Try a more general selector (e.g., `div.content` instead of `div.content-wrapper-v2`)
2. Test the selector on multiple pages of the same site
3. Use the browser console to test: `document.querySelector('your-selector')`

### "The extension panel is in the way"

**Solution:**
- Click the ✕ button to close it
- Or press ESC to close everything

### "I selected the wrong area"

**Solution:**
- Click "🎯 Select Again" at the bottom of the panel
- Or press ESC and start over

---

## FAQ

### Q: Do I need to know CSS to use this?

**A:** No! The extension finds selectors for you. You just need to select what you want and copy the result.

### Q: What's a CSS selector?

**A:** It's a pattern that identifies elements on a webpage. For example:
- `.title` - finds elements with class "title"
- `#header` - finds the element with id "header"
- `h1` - finds all `<h1>` heading elements

### Q: Why are some selectors better than others?

**A:** 
- **Unique selectors** (1 match) are best because they find exactly what you want
- **Semantic selectors** (like `.novel-title`) are better than generic ones (like `div`)
- **Shorter selectors** are usually more reliable

### Q: Can I use this on any website?

**A:** Yes! It works on any website. However, some sites with heavy JavaScript may need special handling.

### Q: What's the difference between drag and click selection?

**A:**
- **Drag** - Analyzes ALL elements in the selected area
- **Click** - Analyzes the clicked element AND its children

### Q: How do I know which selector to use?

**A:**
1. Look for green checkmarks (unique matches)
2. Use Preview to verify the content
3. Use Highlight to see what's selected on the page
4. Test on multiple pages

### Q: Can I export all selectors?

**A:** Yes! Click "📥 Export All" to download a JSON file with all found selectors.

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────┐
│                 IReader Selector Helper                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ACTIVATE:  Click extension icon in toolbar                 │
│                                                              │
│  SELECT:    Drag to draw a box  OR  Click on element        │
│                                                              │
│  CANCEL:    Press ESC                                        │
│                                                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  SYMBOLS:   ✓ = Unique (BEST)                               │
│             ~ = Few matches                                  │
│             • = Many matches                                 │
│                                                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  BUTTONS:   👁 = Preview content                            │
│             🎯 = Highlight on page                          │
│             📋 = Copy selector                              │
│                                                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  WORKFLOW:  1. Go to website                                │
│             2. Click extension                               │
│             3. Select area                                   │
│             4. Find green ✓ selector                        │
│             5. Preview to verify                             │
│             6. Copy and use!                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Need More Help?

- Check the [KSP Annotations Guide](./KSP_ANNOTATIONS.md) for creating sources
- See [example sources](./example-annotated/) for reference
- Read the [Source API documentation](./source-api/) for advanced usage
