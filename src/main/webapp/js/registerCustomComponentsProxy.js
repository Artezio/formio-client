const registerCustomComponents = require('./registerCustomComponents');

const pathsSet = {};

module.exports = function registerCustomComponentsProxy(resourcePath) {
    if (pathsSet[resourcePath]) {
        return;
    }
    registerCustomComponents(resourcePath);
    pathsSet[resourcePath] = true;
}