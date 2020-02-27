const path = require('path');

const externalLibs = ['jsdom-global', 'formiojs', 'fs', 'path'];

module.exports = {
  entry: {
    cleanUpAndValidate: path.resolve(__dirname, './cleanUpAndValidate.js'),
    cleanUp: path.resolve(__dirname, './cleanUp.js')
  },
  output: {
    path: path.resolve(__dirname, './/..//..//resources/formio-scripts'),
    chunkFilename: '[id].js'
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