module.exports = function putToStdout(fn, ...args) {
    fn(...args)
        .then(result => {
            try {
                result = JSON.stringify(result);
                console.info(result);
            } catch (err) {
                console.error(err)
            }
        })
        .catch(err => {
            console.error(err);
        })
}