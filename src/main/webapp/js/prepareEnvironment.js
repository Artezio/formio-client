require('./initJsDomGlobal');
const registerCustomComponents = require('./registerCustomComponents');

process.stdin.setEncoding('utf8');

const resourcePath = process.argv[2];

registerCustomComponents(resourcePath);