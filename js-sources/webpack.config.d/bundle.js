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

// Ensure compatibility with both browser and Node.js
config.output.globalObject = 'typeof self !== "undefined" ? self : this';

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

// Disable webpack devtool in bundle to prevent duplicate asset conflicts with Kotlin source maps
config.devtool = false;
