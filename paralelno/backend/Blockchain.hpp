#pragma once
#include "Block.hpp"
#include <vector>
#include <cmath>
#include <algorithm>
#include <chrono>
#include <iostream>

class Blockchain {
public:
    std::vector<Block> chain;
    int difficulty;
    int blockGenerationInterval;
    int diffAdjustInterval;
    long long cumulativeDifficulty;

    Blockchain() {
        difficulty = 1;
        blockGenerationInterval = 100;
        diffAdjustInterval = 10;
        cumulativeDifficulty = 0;
        chain.push_back(createGenesisBlock());
        adjustDifficulty();
    }

    Block createGenesisBlock() {
        Block block(0, "Genesis Block", "0", difficulty, 0);
        block.hash = block.calculateHash();
        return block;
    }

    Block getLatestBlock() {
        return chain.back();
    }

    void addBlock(Block& newBlock) {
        std::string expectedPrevHash = getLatestBlock().hash;
        if(newBlock.previousHash != expectedPrevHash) {
            std::cerr << "Napaka: previousHash se ne ujema! Pričakovan: " << expectedPrevHash 
                      << ", Dobljen: " << newBlock.previousHash << std::endl;
            return;
        }
        
        chain.push_back(newBlock);
        adjustDifficulty();
    }

    void adjustDifficulty() {
        cumulativeDifficulty = 0;

        for(size_t i = 1; i < chain.size(); i++) {
            cumulativeDifficulty += (long long)std::pow(2, chain[i].difficulty);
        }

        std::cout << "* Kumulativna tezavnost: " << cumulativeDifficulty << std::endl;

        if(chain.size() <= (size_t)diffAdjustInterval) {
            return;
        }

        Block& prevAdjustmentBlock = chain[chain.size() - diffAdjustInterval];
        long long timeExpected = blockGenerationInterval * diffAdjustInterval * 1000;
        long long timeTaken = getLatestBlock().timestamp - prevAdjustmentBlock.timestamp;

        if(timeTaken < (timeExpected / 2)) {
            difficulty = prevAdjustmentBlock.difficulty + 1;
        } else if(timeTaken > (timeExpected * 2)) {
            difficulty = std::max(1, prevAdjustmentBlock.difficulty - 1);
        } else {
            difficulty = prevAdjustmentBlock.difficulty;
        }
    }

    bool isBlockValid(const Block& block) const {
        if(block.index == 0) {
            return true;
        }

        if(block.index >= (int)chain.size()) {
            return false;
        }

        const Block& previousBlock = chain[block.index - 1];

        if(block.index != previousBlock.index + 1) {
            return false;
        }

        if(block.previousHash != previousBlock.hash) {
            return false;
        }

        std::string calculatedHash = block.calculateHash();
        if(block.hash != calculatedHash) {
            std::cerr << "- Hash vrednost bloka ni veljavna." << std::endl;
            // std::cerr << "  Shranjeni hash: " << block.hash << std::endl;
            // std::cerr << "  Izračunani hash: " << calculatedHash << std::endl;
            // std::cerr << "  Vrednosti za izračun:" << std::endl;
            // std::cerr << "    Index: " << block.index << std::endl;
            // std::cerr << "    Timestamp: " << block.timestamp << std::endl;
            // std::cerr << "    Data: " << block.data << std::endl;
            // std::cerr << "    PreviousHash: " << block.previousHash << std::endl;
            // std::cerr << "    Difficulty: " << block.difficulty << std::endl;
            // std::cerr << "    Nonce: " << block.nonce << std::endl;
            return false;
        }

        long long currentTime = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
        
        if(block.timestamp > currentTime + 60000) {
            return false;
        }

        if(block.timestamp < previousBlock.timestamp - 60000) {
            return false;
        }

        std::string target(block.difficulty, '0');
        if(block.hash.substr(0, block.difficulty) != target) {
            return false;
        }

        return true;
    }

    bool isChainValid() const {
        if(chain.size() == 1) {
            return true;
        }

        for(size_t i = 0; i < chain.size(); i++) {
            if(!isBlockValid(chain[i])) {
                return false;
            }
        }

        return true;
    }

    long long getCumulativeDifficulty() const {
        long long cumDiff = 0;
        for(size_t i = 1; i < chain.size(); i++) {
            cumDiff += (long long)std::pow(2, chain[i].difficulty);
        }
        return cumDiff;
    }
   
    static const Blockchain* selectChainWithMaxCumulativeDifficulty(const std::vector<Blockchain>& chains) {
        if(chains.empty()) {
            return nullptr;
        }
        
        const Blockchain* bestChain = &chains[0];
        long long maxDifficulty = bestChain->getCumulativeDifficulty();
        
        for(size_t i = 1; i < chains.size(); i++) {
            long long difficulty = chains[i].getCumulativeDifficulty();
            if(difficulty > maxDifficulty) {
                maxDifficulty = difficulty;
                bestChain = &chains[i];
            }
        }
        
        return bestChain;
    }
    
   
    bool addBlockWithForkResolution(Block& newBlock) {
        if(newBlock.index < (int)chain.size()) {

            Blockchain alternativeChain;
            alternativeChain.difficulty = difficulty;
            alternativeChain.blockGenerationInterval = blockGenerationInterval;
            alternativeChain.diffAdjustInterval = diffAdjustInterval;
            
            for(int i = 0; i < newBlock.index; i++) {
                alternativeChain.chain.push_back(chain[i]);
            }
            
            alternativeChain.chain.push_back(newBlock);
            alternativeChain.adjustDifficulty();
            
           
            long long currentDifficulty = getCumulativeDifficulty();
            long long alternativeDifficulty = alternativeChain.getCumulativeDifficulty();
            
            if(alternativeDifficulty > currentDifficulty) {
                *this = alternativeChain;
                std::cout << "* Fork resolucija: Izbrana veriga z vecjo kumulativno tezavnostjo (" << alternativeDifficulty << " > " << currentDifficulty << ")" << std::endl;
                return true;
            } else {
                std::cout << "* Fork zaznan, vendar trenutna veriga ostaja (kum. tez: " << currentDifficulty << " >= " << alternativeDifficulty << ")" << std::endl;
                return false;
            }
        }
        
        addBlock(newBlock);
        return true;
    }
};
