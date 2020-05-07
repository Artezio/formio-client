const initJsdomGlobal = require('jsdom-global');
initJsdomGlobal(undefined, {
    url: "http://localhost"
});
global.Option = global.window.Option;
global.window.matchMedia = function (media) {
    return {
        matches: false,
        media
    }
};