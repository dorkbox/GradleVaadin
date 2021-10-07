/**
 * This file is SPECIFICALLY for production builds, where it overrides specific configurations for webpack
 */

const merge = require('webpack-merge');
const configSettings = require('./webpack.config.js');

module.exports = merge(configSettings,
    {
        // NOTE: this allows us to have 'node_modules' in a DIFFERENT directory location other than somewhere in the hierarchy of the frontend dir!
        // THIS IS NOT WHERE YOU THINK IT IS! For production builds, this is relative to the /build directory
        resolveLoader: {
            modules: ['node_modules'],
        },
        resolve: {
            modules: ['node_modules'],
        },
        devtool: 'none'
    }
);
