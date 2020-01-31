const validateSubmission = require('./validateSubmission');
const cleanUpSubmission = require('./cleanUpSubmission')
let [form, data] = process.argv.slice(1).map(x => JSON.parse(x));

submission = cleanUpSubmission(form, submission);

validateSubmission(form, submission)
    .then(result => {
        try {
            result = JSON.stringify(result);
            console.info(result);
        } catch (err) {
            console.error('error1', err)
        }
    })
    .catch(err => {
        console.error('error2', err);
    })