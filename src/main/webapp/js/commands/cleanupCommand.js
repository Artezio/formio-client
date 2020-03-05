const cleanUpSubmission = require('../cleanUpSubmission');
const Stdout = require('../stdout');

const stdout = Stdout.getInstance();

class CleanupCommand {
    constructor({ form, data }) {
        this.data = data;
        this.form = form;
    }

    execute() {
        const submission = { data: this.data };
        const cleanSubmission = cleanUpSubmission(this.form, submission);
        const result = cleanSubmission.data;
        stdout.send(result);
    }
}

module.exports = CleanupCommand;