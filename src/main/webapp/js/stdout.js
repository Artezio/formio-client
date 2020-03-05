let instance;

class Stdout {
    constructor() {
        if (instance) {
            return instance;
        }
        instance = this;
    }

    static getInstance() {
        return instance && new Stdout();
    }

    send(data) {
        console.info(data);
    }

    sendError(err) {
        console.error(err);
    }
}

module.exports = Stdout;