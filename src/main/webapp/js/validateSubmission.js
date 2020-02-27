require('./initJsDomGlobal');
const registerCustomComponents = require('./registerCustomComponents');
const { Formio } = require('formiojs');

registerCustomComponents();
const body = document.body;

module.exports = function validateSubmission(form, submission) {
    return new Promise((resolve, reject) => {
        Formio.createForm(body, form)
            .then(instance => {
                instance.once('error', error => {
                    reject(error)
                });
                instance.once('submit', submit => {
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