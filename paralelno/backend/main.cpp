#include "Blockchain.hpp"
#include <iostream>
#include <thread>
#include <vector>
#include <atomic>
#include <mutex>
#include <map>
#include <algorithm>
#include <mpi.h>
#include <chrono>
#include <sstream>
#include <cstring>
#include "external/httplib.h"
#include "external/json.hpp"
using json = nlohmann::json;

std::atomic<bool> blockFound(false);
std::mutex outputMutex;
std::mutex blockchainMutex;
std::mutex metricsMutex;

struct MiningMetrics {
    long long timestamp;
    long long duration;
    int worldSize;
    int numThreads;
    int difficulty;
};

std::vector<MiningMetrics> miningMetrics;

struct MiningResult {
    bool found;
    long long nonce;
    std::string hash;
    int threadId;
    int processId;
};

void mineBlockThread(const Block& baseBlock, long long startNonce, long long endNonce, int threadId, int processId, MiningResult& result) {
    std::string target(baseBlock.difficulty, '0');
    Block block = baseBlock;

    for(long long n = startNonce; n < endNonce && !blockFound.load(); n++) {
        block.nonce = n;
        std::string hash = block.calculateHash();
        
        if(hash.substr(0, block.difficulty) == target) {
            blockFound.store(true);
            result.found = true;
            result.nonce = n;
            result.hash = hash;
            result.threadId = threadId;
            result.processId = processId;
            
            std::lock_guard<std::mutex> lock(outputMutex);
            std::cout << "[" << processId << ", " << threadId << "] Najden veljaven blok! Hash: " << hash << ", Nonce: " << n << std::endl;
            return;
        }
    }
    
    result.found = false;
}

Block mineBlockParallel(Block block, int worldRank, int worldSize, int numThreads) {
    auto startTime = std::chrono::high_resolution_clock::now();
    
    int originalIndex = block.index;
    std::string originalData = block.data;
    long long originalTimestamp = block.timestamp;
    std::string originalPreviousHash = block.previousHash;
    int originalDifficulty = block.difficulty;
    
    long long nonceRange = 1000000000LL;
    long long currentNonceOffset = 0;
    
    while(true) {
        blockFound.store(false);
        
        long long startNonce = currentNonceOffset + (worldRank * nonceRange);
        long long endNonce = startNonce + nonceRange;
        
        long long noncePerThread = nonceRange / numThreads;
        
        std::vector<std::thread> threads;
        std::vector<MiningResult> results(numThreads);
        
        for(int i = 0; i < numThreads; i++) {
            long long threadStart = startNonce + i * noncePerThread;
            long long threadEnd = (i == numThreads - 1) ? endNonce : threadStart + noncePerThread;
            
            threads.emplace_back(mineBlockThread, std::cref(block), threadStart, threadEnd, i, worldRank, std::ref(results[i]));
        }
        
        for(auto& t : threads) {
            t.join();
        }
        
        bool localFound = false;
        MiningResult localResult;
        
        for(int i = 0; i < numThreads; i++) {
            if(results[i].found) {
                localFound = true;
                localResult = results[i];
                break;
            }
        }
        
        int foundFlag = localFound ? 1 : 0;
        int globalFoundFlag;
        
        MPI_Allreduce(&foundFlag, &globalFoundFlag, 1, MPI_INT, MPI_MAX, MPI_COMM_WORLD);
        
        if(globalFoundFlag == 1) {
            int foundProcess = -1;
            long long foundNonce = 0;
            int foundThreadId = -1;
            
            if(localFound) {
                foundProcess = worldRank;
                foundNonce = localResult.nonce;
                foundThreadId = localResult.threadId;
            }
            
            std::vector<int> processes(worldSize);
            std::vector<long long> nonces(worldSize);
            std::vector<int> threadIds(worldSize);
            
            MPI_Gather(&foundProcess, 1, MPI_INT, processes.data(), 1, MPI_INT, 0, MPI_COMM_WORLD);
            MPI_Gather(&foundNonce, 1, MPI_LONG_LONG, nonces.data(), 1, MPI_LONG_LONG, 0, MPI_COMM_WORLD);
            MPI_Gather(&foundThreadId, 1, MPI_INT, threadIds.data(), 1, MPI_INT, 0, MPI_COMM_WORLD);
            
            int winnerProcess = -1;
            if(worldRank == 0) {
                for(int i = 0; i < worldSize; i++) {
                    if(processes[i] >= 0) {
                        winnerProcess = processes[i];
                        foundNonce = nonces[i];
                        foundThreadId = threadIds[i];
                        break;
                    }
                }
            }
            
            MPI_Bcast(&winnerProcess, 1, MPI_INT, 0, MPI_COMM_WORLD);
            MPI_Bcast(&foundNonce, 1, MPI_LONG_LONG, 0, MPI_COMM_WORLD);
            
            char hashBuffer[65];
            if(winnerProcess >= 0) {
                if(localFound && worldRank == winnerProcess) {
                    strncpy(hashBuffer, localResult.hash.c_str(), 64);
                    hashBuffer[64] = '\0';
                }
                MPI_Bcast(hashBuffer, 65, MPI_CHAR, winnerProcess, MPI_COMM_WORLD);
            } else {
                if(worldRank == 0) {
                    std::cerr << "! Noben ni nasel bloka" << std::endl;
                }
                hashBuffer[0] = '\0';
            }
            
            auto endTime = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime).count();
            
            Block newBlock = Block(originalIndex, originalData, originalTimestamp, originalPreviousHash, originalDifficulty, foundNonce, std::string(hashBuffer));
            
            if(worldRank == 0) {
                std::cout << "\n\t+ Blok uspesno izrudarjen v " << duration << " ms" << std::endl;
                std::cout << "\tProces: " << winnerProcess << ", nit: " << foundThreadId << std::endl;
            }
            
            return newBlock;
        }
        
        currentNonceOffset += worldSize * nonceRange;
        
        if(worldRank == 0 && (currentNonceOffset / (worldSize * nonceRange)) % 10 == 0) {
            std::cout << "Iskanje nadaljuje... preizkuseno obmocij: " << (currentNonceOffset / (worldSize * nonceRange)) << std::endl;
        }
    }
}

void startHttpServer(Blockchain* blockchain, int worldSize, int numThreads, int* blockIndexPtr) {
    httplib::Server svr;
    
    svr.set_default_headers({
        {"Access-Control-Allow-Origin", "*"},
        {"Access-Control-Allow-Methods", "GET, POST, OPTIONS"},
        {"Access-Control-Allow-Headers", "Content-Type"}
    });
    
    svr.Get("/chain", [blockchain](const httplib::Request&, httplib::Response& res) {
        std::lock_guard<std::mutex> lock(blockchainMutex);
        json chainArray = json::array();
        
        for(const auto& block : blockchain->chain) {
            json blockJson;
            blockJson["index"] = block.index;
            blockJson["data"] = block.data;
            blockJson["timestamp"] = block.timestamp;
            blockJson["previousHash"] = block.previousHash;
            blockJson["difficulty"] = block.difficulty;
            blockJson["nonce"] = block.nonce;
            blockJson["hash"] = block.hash;
            chainArray.push_back(blockJson);
        }
        
        res.set_content(chainArray.dump(), "application/json");
    });
    
    svr.Get("/status", [blockchain, worldSize, numThreads, blockIndexPtr](const httplib::Request&, httplib::Response& res) {
        std::lock_guard<std::mutex> lock(blockchainMutex);
        json status;
        status["chainLength"] = blockchain->chain.size();
        status["difficulty"] = blockchain->difficulty;
        status["cumulativeDifficulty"] = blockchain->cumulativeDifficulty;
        status["isValid"] = blockchain->isChainValid();
        status["worldSize"] = worldSize;
        status["numThreads"] = numThreads;
        status["totalThreads"] = worldSize * numThreads;
        status["nextBlockIndex"] = *blockIndexPtr;
        
        res.set_content(status.dump(), "application/json");
    });
    
    svr.Post("/mine", [blockchain](const httplib::Request&, httplib::Response& res) {
        json response;
        response["message"] = "Rudarjenje poteka avtomatsko v ozadju";
        response["currentBlockIndex"] = blockchain->chain.size();
        res.set_content(response.dump(), "application/json");
    });
    
    svr.Get("/metrics", [](const httplib::Request&, httplib::Response& res) {
        std::lock_guard<std::mutex> lock(metricsMutex);
        json metricsArray = json::array();
        
        for(const auto& metric : miningMetrics) {
            json metricJson;
            metricJson["timestamp"] = metric.timestamp;
            metricJson["duration"] = metric.duration;
            metricJson["worldSize"] = metric.worldSize;
            metricJson["numThreads"] = metric.numThreads;
            metricJson["totalThreads"] = metric.worldSize * metric.numThreads;
            metricJson["difficulty"] = metric.difficulty;
            metricsArray.push_back(metricJson);
        }
        
        res.set_content(metricsArray.dump(), "application/json");
    });
    
    svr.set_mount_point("/", "./public");
    
    std::cout << "HTTP laufa na http://localhost:8080" << std::endl;
    svr.listen("0.0.0.0", 8080);
}

int main(int argc, char** argv) {
    MPI_Init(&argc, &argv);

    int worldRank, worldSize;
    MPI_Comm_rank(MPI_COMM_WORLD, &worldRank);
    MPI_Comm_size(MPI_COMM_WORLD, &worldSize);

    int numThreads = std::thread::hardware_concurrency();
    if(numThreads == 0) {
        numThreads = 4;
    }
    // int numThreads = 12;

    if(worldRank == 0) {
        std::cout << "Veriga blokov" << std::endl;
        std::cout << "Zacenjam z rudarjenjem" << std::endl;
        std::cout << "Stevilo vozlisc: " << worldSize << std::endl;
        std::cout << "Stevilo niti: " << numThreads << std::endl;
        std::cout << "Skupno stevilo niti: " << worldSize * numThreads << std::endl;
    }

    Blockchain blockchain;
    
    if(worldRank == 0) {
        std::cout << "Ustvaril sem prvi blok: " << std::endl;
        std::cout << "Indeks: " << blockchain.chain[0].index << std::endl;
        std::cout << "Hash: " << blockchain.chain[0].hash << std::endl;
        std::cout << "Tezavnost: " << blockchain.difficulty << std::endl;
        std::cout << std::endl;
    }

    int blockIndex = 1;
    long long startTime = 0;
    bool difficulty5Reached = false;
    bool difficulty6Reached = false;
    
    if(worldRank == 0) {
        startTime = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
    }
    
    std::thread httpServerThread;
    if(worldRank == 0) {
        httpServerThread = std::thread(startHttpServer, &blockchain, worldSize, numThreads, &blockIndex);
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    
    while(true) {
        if(worldRank == 0) {
            std::cout << "\n\tRudarjenje bloka: " << blockIndex << std::endl;
            std::cout << "Trenutna tezavnost: " << blockchain.difficulty << std::endl;
        }

        std::stringstream ss;
        ss << "To je blok " << blockIndex;
        Block newBlock(blockIndex, blockchain.getLatestBlock().hash, ss.str(), blockchain.difficulty);

        auto miningStart = std::chrono::high_resolution_clock::now();
        Block minedBlock = mineBlockParallel(newBlock, worldRank, worldSize, numThreads);
        auto miningEnd = std::chrono::high_resolution_clock::now();
        auto miningDuration = std::chrono::duration_cast<std::chrono::milliseconds>(miningEnd - miningStart).count();

        if(worldRank == 0) {
            {
                std::lock_guard<std::mutex> lock(blockchainMutex);
                blockchain.addBlockWithForkResolution(minedBlock);
            }
            
            {
                std::lock_guard<std::mutex> lock(metricsMutex);
                MiningMetrics metric;
                metric.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
                metric.duration = miningDuration;
                metric.worldSize = worldSize;
                metric.numThreads = numThreads;
                metric.difficulty = minedBlock.difficulty;
                miningMetrics.push_back(metric);
            }
            
            std::cout << "\n\t++ Blok " << blockIndex << " dodan v verigo:" << std::endl;
            std::cout << "\tIndeks: " << minedBlock.index << std::endl;
            std::cout << "\tHash: " << minedBlock.hash << std::endl;
            std::cout << "\tZeton: " << minedBlock.nonce << std::endl;
            std::cout << "\tTezavnost: " << minedBlock.difficulty << std::endl;
            std::cout << "\tCas rudarjenja: " << miningDuration << " ms" << std::endl;
            
            {
                std::lock_guard<std::mutex> lock(blockchainMutex);
                std::cout << "\tKumulativna tezavnost: " << blockchain.cumulativeDifficulty << std::endl;
                
                if(blockchain.isChainValid()) {
                    std::cout << "+ Veriga je veljavna" << std::endl;
                } else {
                    std::cout << "- Veriga ni veljavna" << std::endl;
                }
            }
        }

        if(worldRank == 0) {
            std::lock_guard<std::mutex> lock(blockchainMutex);
            int currentDifficulty = blockchain.difficulty;
            
            if(currentDifficulty == 5 && !difficulty5Reached) {
                long long currentTime = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
                long long elapsedTime = currentTime - startTime;
                
                difficulty5Reached = true;
                std::cout << "\nČas do težavnosti 5: " << elapsedTime << " ms" << std::endl;
                // std::cout << "Število blokov: " << blockIndex << std::endl;
            }
            
            if(currentDifficulty == 6 && !difficulty6Reached) {
                long long currentTime = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::system_clock::now().time_since_epoch()).count();
                long long elapsedTime = currentTime - startTime;
                
                difficulty6Reached = true;
                std::cout << "\nČas do težavnosti 6: " << elapsedTime << " ms" << std::endl;
                // std::cout << "Število blokov: " << blockIndex << std::endl;
            }
        }
        
        MPI_Barrier(MPI_COMM_WORLD);
        
        blockIndex++;
    }
    
    if(worldRank == 0) {
        httpServerThread.join();
    }

    MPI_Finalize();
    return 0;
}
