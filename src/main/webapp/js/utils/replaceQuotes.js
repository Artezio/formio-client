const fs = require('fs');
const path = require('path');

const pathToTargetFile = path.resolve(__dirname, '../dist/index.js');

let str = fs.readFileSync(pathToTargetFile, { encoding: 'utf8' });
str = str.replace(/"/g, "'");
fs.writeFileSync(pathToTargetFile, str);