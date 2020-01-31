require('jsdom-global')();
global.Option = global.window.Option;
const { Formio } = require('formiojs');
const body = document.body;

module.exports = function validateSubmission(form, submission) {
    return new Promise((resolve, reject) => {
        Formio.createForm(body, form)
            .then(instance => {
                instance.once('error', error => {
                    console.log('Error done')
                    reject(error)
                });
                instance.once('submit', submit => {
                    console.log('Submit done')
                    resolve(submit)
                });
                instance.once('change', () => {
                    instance.submit()
                        .then(() => {

                        }).catch(() => {

                        });
                });
                instance.submission = submission;
            })
            .catch(err => {
                reject(err);
            })
    })
}