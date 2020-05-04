const cleanUpSubmission = require('../cleanUpSubmission');
const Stdout = require('../stdout');
const validateSubmission = require('../validateSubmission');
const Command = require('./command');
const registerCustomComponents = require('../registerCustomComponentsProxy');

const stdout = Stdout.getInstance();

class ValidateCommand extends Command {
    constructor(args = {}) {
        const { form, data, resourcePath } = args;
        super({ form, data });
        this.data = data;
        this.form = form;
        this.resourcePath = resourcePath;
    }

    execute() {
        const submission = { data: this.data };
        const cleanSubmission = cleanUpSubmission(this.form, submission);
        registerCustomComponents(this.resourcePath);
        return validateSubmission(this.form, cleanSubmission)
            .then(result => {
                try {
                    result = JSON.stringify(result);
                    stdout.send(result);
                } catch (err) {
                    stdout.sendError(err.toString());
                }
            })
            .catch(error => {
                try {
                    error = JSON.stringify(error);
                    stdout.sendError(error);
                } catch (err) {
                    stdout.sendError(err.toString());
                }
            })
    }
}

module.exports = ValidateCommand;