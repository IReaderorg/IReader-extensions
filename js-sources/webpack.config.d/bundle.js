// Webpack configuration for self-contained bundle
// This ensures all dependencies are bundled and the library is exported properly

config.output = config.output || {};

// UMD (Universal Module Definition) - works in browser, Node.js, and AMD
config.output.libraryTarget = 'umd';

// Empty publicPath to avoid 'Automatic publicPath is not supported' in headless JS engines (JSC / iOS)
config.output.publicPath = '';

// Disable code splitting so the entire bundle is self-contained in one file
config.optimization = config.optimization || {};
config.optimization.splitChunks = false;

// Force all code into a single bundle (no 731.js or secondary chunks)
const webpack = require('webpack');
config.plugins = config.plugins || [];
config.plugins.push(
    new webpack.optimize.LimitChunkCountPlugin({
        maxChunks: 1
    })
);

// Global variable name when loaded in browser
config.output.library = 'IReaderSources';

// Ensure compatibility with both browser, Node.js, and headless JavaScriptCore
config.output.globalObject = 'typeof globalThis !== "undefined" ? globalThis : (typeof self !== "undefined" ? self : this)';

// Don't externalize any dependencies - bundle everything
config.externals = [];

// Optimization settings for production
if (config.mode === 'production') {
    config.optimization = config.optimization || {};
    
    // Keep function names for debugging (sources use reflection)
    config.optimization.minimize = true;
    
    try {
        const TerserPlugin = require('terser-webpack-plugin');
        config.optimization.minimizer = config.optimization.minimizer || [];
        config.optimization.minimizer.push(
            new TerserPlugin({
                terserOptions: {
                    keep_classnames: true,
                    keep_fnames: true,
                    mangle: {
                        keep_classnames: true,
                        keep_fnames: true
                    }
                }
            })
        );
    } catch (e) {
        console.warn('terser-webpack-plugin not found, continuing with default minimizer');
    }
}

// Export bridge: promote SourceBridge, SourceRegistry, and init* functions to root and globalThis
config.plugins.push(
    new webpack.BannerPlugin({
        raw: true,
        footer: true,
        banner: `
(function() {
    var globalObj = typeof globalThis !== 'undefined' ? globalThis : (typeof self !== 'undefined' ? self : this);
    var root = typeof IReaderSources !== 'undefined' ? IReaderSources : (globalObj && globalObj.IReaderSources);
    if (!root && typeof module !== 'undefined' && module.exports) {
        root = module.exports;
    }
    if (!root) return;

    function findExport(obj, targetName, depth) {
        if (!obj || typeof obj !== 'object' || depth > 6) return null;
        var kName = targetName;
        if (obj[kName]) return obj[kName];
        for (var k in obj) {
            if (Object.prototype.hasOwnProperty.call(obj, k)) {
                var found = findExport(obj[k], kName, depth + 1);
                if (found) return found;
            }
        }
        return null;
    }

    var bridge = findExport(root, 'SourceBridge', 0);
    var registry = findExport(root, 'SourceRegistry', 0);

    if (bridge) {
        root.SourceBridge = bridge;
        globalObj.SourceBridge = bridge;
    }
    if (registry) {
        root.SourceRegistry = registry;
        globalObj.SourceRegistry = registry;
    }

    function promoteInits(obj, depth) {
        if (!obj || typeof obj !== 'object' || depth > 6) return;
        for (var k in obj) {
            if (Object.prototype.hasOwnProperty.call(obj, k)) {
                if (typeof obj[k] === 'function' && k.indexOf('init') === 0) {
                    root[k] = obj[k];
                    globalObj[k] = obj[k];
                } else if (typeof obj[k] === 'object') {
                    promoteInits(obj[k], depth + 1);
                }
            }
        }
    }
    promoteInits(root, 0);

    if (root.initializeBundle && typeof root.initializeBundle === 'function') {
        try { root.initializeBundle(); } catch(e) {}
    }
})();
`
    })
);

// Disable webpack devtool in bundle to prevent duplicate asset conflicts with Kotlin source maps
config.devtool = false;
