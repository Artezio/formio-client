const path = require('path');

const externalLibs = ['jsdom-global', 'formiojs', 'fs', 'path'];

module.exports = {
  entry: 'index.js',
  output: {
    path: path.resolve(__dirname, './/..//..//resources/formio-scripts'),
    chunkFilename: 'index.js'
  },
  externals: [
    function (context, request, callback) {
      if (externalLibs.some(libName => {
        const regExp = new RegExp('^' + libName + '$');
        return regExp.test(request);
      })) {
        return callback(null, 'commonjs ' + request);
      }
      
      callback();
    }
  ],
  mode: 'production'
}