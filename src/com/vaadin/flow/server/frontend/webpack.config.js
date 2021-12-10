/**
 * This file can be used to configure the flow plugin defaults.
 * <code>
 *   // Add a custom plugin
 *   flowDefaults.plugins.push(new MyPlugin());
 *
 *   // Update the rules to also transpile `.mjs` files
 *   if (!flowDefaults.module.rules[0].test) {
 *     throw "Unexpected structure in generated webpack config";
 *   }
 *   flowDefaults.module.rules[0].test = /\.m?js$/
 *
 *   // Include a custom JS in the entry point in addition to generated-flow-imports.js
 *   if (typeof flowDefaults.entry.index != "string") {
 *     throw "Unexpected structure in generated webpack config";
 *   }
 *   flowDefaults.entry.index = [flowDefaults.entry.index, "myCustomFile.js"];
 * </code>
 * or add new configuration in the merge block.
 * <code>
 *   module.exports = merge(flowDefaults, {
 *     mode: 'development',
 *     devtool: 'inline-source-map'
 *   });
 * </code>
 */

const TerserPlugin = require('terser-webpack-plugin')
const merge = require('webpack-merge');
const flowDefaults = require('./webpack.generated.js');
const path = require('path');

const npmPath = path.join('build', "nodejs", "node_modules");
const modulesPath = path.join('build', "node_modules");

module.exports = merge(flowDefaults,
    {
        // enhance debugging by adding meta info for the browser devtools
        // source-map most detailed at the expense of build speed.
        devtool: 'source-map',

        // NOTE: this allows us to have 'node_modules' in a DIFFERENT directory location other than somewhere in the hierarchy of the frontend dir!
        // THIS IS NOT WHERE YOU THINK IT IS! For development builds, this is relative to the project directory
        resolveLoader: {
            modules: [npmPath, modulesPath],
        },
        resolve: {
            modules: [npmPath, modulesPath],
        },
        optimization: {
            // the UglifyJS minimizer is TERRIBLE. This offers better performance and robustness
            // v5 of webpack changes the default to Terser, so once vaadin updates to v5, this can be removed.
            // https://github.com/webpack-contrib/terser-webpack-plugin/issues/15
            minimizer: [new TerserPlugin()]
        },


        // here is where all of our plugins are configured
        // plugins: [
        //     new HtmlWebpackPlugin({
        //         template: 'webComponents/src/index.html',
        //         // template: resolve(__dirname, 'webComponents/src/', 'index.html'),
        //         filename: './index.html'
        //     })
        // ],
    }
);
