const { OPERATIONS } = require('./constants');
const CleanupCommand = require('./commands/cleanupCommand');
const PingCommand = require('./commands/pingCommand');
const ValidateCommand = require('./commands/validateCommand');

module.exports = function getCommand(operation, data) {
    switch (operation) {
        case OPERATIONS.CLEANUP: {
            return new CleanupCommand(data);
        }
        case OPERATIONS.VALIDATE: {
            return new ValidateCommand(data);
        }
        case OPERATIONS.PING: {
            return new PingCommand(data);
        }
        default: {
            return new PingCommand(data);
        }
    }
}