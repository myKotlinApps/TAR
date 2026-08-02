// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract BexScanner {
    address public owner;
    constructor() { owner = msg.sender; }
    modifier onlyOwner() { require(msg.sender == owner, "Not owner"); _; }
    function analyze(address target) external view returns (bool vulnerable, string memory reason) {
        uint256 size;
        assembly { size := extcodesize(target) }
        if (size == 0) return (false, "No code");
        if (size < 100) return (true, "Small contract - potential proxy");
        return (false, "OK");
    }
}
