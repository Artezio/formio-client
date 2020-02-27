const cleanUpSubmission = require('./cleanUpSubmission')
const takeFromStdin = require('./takeFromStdin');
const putToStdout = require('./putToStdout');

let [form, submission] = takeFromStdin();

const cleanData = cleanUpSubmission(form, submission);

function promise() {
    return Promise.resolve(cleanData);
}

putToStdout(promise);