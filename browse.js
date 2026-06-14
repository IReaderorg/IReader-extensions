const puppeteer = require('puppeteer');

async function browseSite(url, selector = null) {
    const browser = await puppeteer.launch({ 
        headless: true,
        args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    const page = await browser.newPage();
    
    try {
        await page.goto(url, { waitUntil: 'networkidle2', timeout: 30000 });
        
        // Get page title
        const title = await page.title();
        console.log('Title:', title);
        
        // Get all class names on the page
        const classes = await page.evaluate(() => {
            const elements = document.querySelectorAll('*');
            const classSet = new Set();
            elements.forEach(el => {
                if (el.className && typeof el.className === 'string') {
                    el.className.split(' ').forEach(c => {
                        if (c.trim()) classSet.add(c.trim());
                    });
                }
            });
            return Array.from(classSet).sort();
        });
        console.log('\nClasses found:', classes.length);
        console.log(classes.slice(0, 50).join(', '));
        
        // If selector provided, check if it exists
        if (selector) {
            const count = await page.evaluate((sel) => {
                return document.querySelectorAll(sel).length;
            }, selector);
            console.log(`\nSelector "${selector}": ${count} elements found`);
            
            // Get first few elements' text
            if (count > 0) {
                const texts = await page.evaluate((sel) => {
                    const elements = document.querySelectorAll(sel);
                    return Array.from(elements).slice(0, 5).map(el => el.textContent?.trim().substring(0, 100));
                }, selector);
                console.log('Sample texts:', texts);
            }
        }
        
        // Get full HTML structure (first 5000 chars)
        const html = await page.content();
        console.log('\nHTML length:', html.length);
        
    } catch (error) {
        console.error('Error:', error.message);
    } finally {
        await browser.close();
    }
}

// Run with command line args
const url = process.argv[2] || 'https://novelbin.com/sort/latest';
const selector = process.argv[3];

browseSite(url, selector);
