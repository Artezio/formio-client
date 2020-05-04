const { Formio } = require('formiojs');

const body = document.body;

module.exports = function validateSubmission(form, submission) {
    return new Promise((resolve, reject) => {
        Formio.createForm(body, form)
            .then(instance => {
                instance.once('error', errors => {
                    errors = errors && errors.map(error => error.message);
                    reject(errors);
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