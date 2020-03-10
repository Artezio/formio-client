const assert = require('assert');

const CleanupCommand = require('../commands/cleanupCommand');
const PingCommand = require('../commands/pingCommand');
const ValidateCommand = require('../commands/validateCommand');

const { OPERATION } = require('../constants');
const getCommand = require('../getCommand');


describe('test', () => {
    it('getCommand', () => {
        assert(getCommand(OPERATION.CLEANUP) instanceof CleanupCommand);
        assert(getCommand(OPERATION.VALIDATE) instanceof ValidateCommand);
        assert(getCommand(OPERATION.PING) instanceof PingCommand);
        assert(getCommand('text') instanceof PingCommand);
    })
})