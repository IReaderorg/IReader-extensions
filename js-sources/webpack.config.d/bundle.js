// Webpack configuration for self-contained bundle
// This ensures all dependencies are bundled and the library is exported properly

config.output = config.output || {};

// UMD (Universal Module Definition) - works in browser, Node.js, and AMD
config.output.libraryTarget = 'umd';

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
    config.optimization.minimizer = config.optimization.minimizer || [];
    
    // Configure terser to keep important names
    const TerserPlugin = require('terser-webpack-plugin');
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
}

// Ensure source maps are generated
config.devtool = 'source-map';
