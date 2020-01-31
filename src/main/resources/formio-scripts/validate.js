const validateSubmission = require('./validateSubmission');
const [form, data] = process.argv.slice(1).map(x => JSON.parse(x));

validateSubmission(form, submission)
    .then(result => {
        try {
            result = JSON.stringify(result);
            console.info(result);
        } catch (err) {
            console.error(err)
        }
    })