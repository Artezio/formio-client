const { EOT } = require('./constants');
const process = require('process');

let instance;

class Stdout {
    constructor() {
        if (instance) {
            return instance;
        }
        instance = this;
    }

    static getInstance() {
        if (instance) {
            return instance;
        }
        return new Stdout();
    }

    send(data) {
        process.stdout.write(data);
    }

    sendError(err) {
        process.stderr.write(err);
    }

    finally() {
        process.stdout.write(EOT);
        process.stderr.write(EOT);
    }
}

module.exports = Stdout;