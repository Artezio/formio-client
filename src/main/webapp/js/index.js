require('./prepareEnvironment');
const getCommand = require('./getCommand');
const process = require('process');
// var readline = require('readline');

// var rl = readline.createInterface({
//     input: process.stdin,
//     output: process.stdout,
// });

// rl.on('line', handleStdin);

function handleStdin(data) {
    data = data.toString();
    data = JSON.parse(data);
    const command = getCommand(data.operation, data);
    command.execute();
}

process.stdin.on('data', handleStdin);

// data = require('./testData.dev');
// const command = getCommand(data.operation, data);
// command.execute();