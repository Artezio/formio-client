const { Formio } = require('formiojs');
const fs = require('fs');
const path = require('path');
const takeFromStdin = require('./takeFromStdin');

function registerComponent(componentDetails = {}) {
    const { name, path } = componentDetails;
    const customComponent = __non_webpack_require__(path);
    // const customComponent = require(path);
    Formio.registerComponent(name, customComponent);
}

module.exports = function registerCustomComponents() {
    const [, , pathToCustomComponentsFolder] = takeFromStdin();

    if (!pathToCustomComponentsFolder) return;

    const files = fs.readdirSync(pathToCustomComponentsFolder);
    const componentsDetails = files
        .filter(paths => path.extname(paths) === '.js')
        .map(fileBasename => {
            const name = fileBasename.slice(0, -path.extname(fileBasename).length);
            return {
                name,
                path: path.resolve(pathToCustomComponentsFolder, fileBasename)
            }
        })

    componentsDetails.forEach(registerComponent);
}