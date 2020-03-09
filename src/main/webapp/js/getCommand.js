const { OPERATION } = require('./constants');
const CleanupCommand = require('./commands/cleanupCommand');
const PingCommand = require('./commands/pingCommand');
const ValidateCommand = require('./commands/validateCommand');

module.exports = function getCommand(operation, data) {
    switch (operation) {
        case OPERATION.CLEANUP: {
            return new CleanupCommand(data);
        }
        case OPERATION.VALIDATE: {
            return new ValidateCommand(data);
        }
        case OPERATION.PING: {
            return new PingCommand(data);
        }
        default: {
            return new PingCommand(data);
        }
    }
}