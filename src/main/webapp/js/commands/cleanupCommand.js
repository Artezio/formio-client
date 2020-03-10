const cleanUpSubmission = require('../cleanUpSubmission');
const Stdout = require('../stdout');
const Command = require('./command');

const stdout = Stdout.getInstance();

class CleanupCommand extends Command {
    constructor(args = {}) {
        const { form, data } = args;
        super({ form, data });
        this.data = data;
        this.form = form;
    }

    execute() {
        const submission = { data: this.data };
        const cleanSubmission = cleanUpSubmission(this.form, submission);
        let result = cleanSubmission.data;
        try {
            result = JSON.stringify(result);
            stdout.send(result);
        } catch (err) {
            stdout.sendError(err.toString());
        } finally {
            return Promise.resolve();
        }
    }
}

module.exports = CleanupCommand;