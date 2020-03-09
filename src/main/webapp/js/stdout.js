const { EOT } = require('./constants');

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
        process.stdout.write(data + EOT);
    }

    sendError(err) {
        process.stderr.write(err + EOT);
    }
}

module.exports = Stdout;