require('./prepareEnvironment');
const CommandFactory = require('./commandFactory');

const commandFactory = new CommandFactory();

function handleStdin(data) {
    const command = commandFactory.getCommand(data.operation);
    command.execute();
}

process.stdin.on('data', handleStdin);