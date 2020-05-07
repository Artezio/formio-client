const registerCustomComponents = require('./registerCustomComponents');

let previousPath = '';

module.exports = function registerCustomComponentsProxy(resourcePath) {
    if (resourcePath === previousPath) {
        return;
    }
    registerCustomComponents(resourcePath);
    previousPath = resourcePath;
}