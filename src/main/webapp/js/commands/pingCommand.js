const Stdout = require('../stdout');
const { PING_MESSAGE } = require('../constants');

const stdout = Stdout.getInstance();

class PingCommand {
    execute() {
        stdout.send(PING_MESSAGE);
    }
}

module.exports = PingCommand;