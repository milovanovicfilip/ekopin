#pragma once
#include <string>
#include <chrono>
#include <openssl/sha.h>
#include <sstream>
#include <iomanip>

class Block {
public:
    int index;
    std::string data;
    long long timestamp;
    std::string previousHash;
    int difficulty;
    long long nonce;
    std::string hash;

    Block(int idx, const std::string& dat, const std::string& prevHash, int diff, long long nonc) : index(idx), data(dat), previousHash(prevHash), difficulty(diff), nonce(nonc) {
        timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
        hash = "";
    }
    
    Block(int idx, const std::string& dat, long long ts, const std::string& prevHash, int diff, long long nonc, const std::string& hashValue) : index(idx), data(dat), timestamp(ts), previousHash(prevHash), difficulty(diff), nonce(nonc), hash(hashValue) {
    }
    
    Block(int idx, const std::string& prevHash, const std::string& dat, int diff) : index(idx), data(dat), previousHash(prevHash), difficulty(diff), nonce(0) {
        timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
        hash = "";
    }

    std::string calculateHash() const {
        std::stringstream ss;
        ss << index << data << timestamp << previousHash << difficulty << nonce;
        std::string input = ss.str();

        unsigned char hashBuffer[SHA256_DIGEST_LENGTH];
        SHA256(reinterpret_cast<const unsigned char*>(input.c_str()), input.size(), hashBuffer);

        std::stringstream hashStr;
        for(int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
            hashStr << std::hex << std::setw(2) << std::setfill('0') << (int)hashBuffer[i];
        }

        return hashStr.str();
    }

    // void mine() {
    //     std::string target(difficulty, '0');
    //     nonce = 0;

    //     while(true) {
    //         hash = calculateHash();
    //         if(hash.substr(0, difficulty) == target) {
    //             return;
    //         }
    //         nonce++;
    //     }
    // }

    // bool mineRange(long long startNonce, long long endNonce) {
    //     std::string target(difficulty, '0');

    //     for(long long n = startNonce; n < endNonce; n++) {
    //         nonce = n;
    //         hash = calculateHash();

    //         if(hash.substr(0, difficulty) == target) {
    //             return true;
    //         }
    //     }

    //     return false;
    // }
};
