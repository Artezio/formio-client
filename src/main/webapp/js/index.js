require('./prepareEnvironment');
const getCommand = require('./getCommand');
const process = require('process');
const Stdout = require('./stdout');

const stdout = Stdout.getInstance();

function handleStdin(data) {
    data = data.toString();
    data = JSON.parse(data);
    const command = getCommand(data.operation, data);
    command.execute()
        .then(() => stdout.finally())
}

process.stdin.on('data', handleStdin);

// data = require('./test.dev');
// const command = getCommand(data.operation, data);
// command.execute().then(() => stdout.finally())