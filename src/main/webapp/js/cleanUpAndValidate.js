const validateSubmission = require('./validateSubmission');
const cleanUpSubmission = require('./cleanUpSubmission');
const putToStdout = require('./putToStdout');
const takeFromStdin = require('./takeFromStdin');

let [form, submission] = takeFromStdin();
submission = cleanUpSubmission(form, submission);
putToStdout(validateSubmission, form, submission);