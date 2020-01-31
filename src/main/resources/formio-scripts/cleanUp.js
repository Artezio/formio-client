const cleanUpSubmission = require('./cleanUpSubmission')
const [form, data] = process.argv.slice(1).map(x => JSON.parse(x));

const cleanData = cleanUpSubmission(form, data);
try {
    console.info('Clean Data: ', JSON.stringify(cleanData));
} catch (err) {
    console.error(err)
}