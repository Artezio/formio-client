const Stdout = require('../stdout');
const { PING_MESSAGE } = require('../constants');
const Command = require('./command');

const stdout = Stdout.getInstance();

class PingCommand extends Command {
    execute() {
        stdout.send(PING_MESSAGE);
        return Promise.resolve();
    }
}

module.exports = PingCommand;