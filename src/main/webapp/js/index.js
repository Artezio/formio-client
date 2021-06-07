require('./prepareEnvironment');
const getCommand = require('./getCommand');
const process = require('process');
const Stdout = require('./stdout');
const { EOT } = require('./constants');

const stdout = Stdout.getInstance();

var data = '';

function onInData(_data) {
    const eotIndex = _data.indexOf(EOT)
    if(eotIndex !== -1){
        data += _data.substring(0, eotIndex);
        runProcess();
    }else {
        data += _data;
    }
}
function runProcess(){
    const obj = JSON.parse(data);
    data = '';
    const command = getCommand(obj.operation, obj);
    command.execute()
        .then(() => {
            stdout.finally();
        })
}
process.stdin.on('data', onInData).on('end', () => {
    runProcess()
});
