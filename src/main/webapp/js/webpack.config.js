const path = require('path');

const externalLibs = ['jsdom-global', 'formiojs', 'fs', 'path', 'process', 'readline'];

module.exports = {
  entry: './index.js',
  output: {
    // path: path.resolve(__dirname, './/..//..//resources/formio-scripts'),
    path: path.resolve(__dirname, './dist'),
    filename: './index.js'
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
  // mode: 'production',
  // mode: 'development',
  // devtool: 'source-map'
}