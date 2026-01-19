CXX = mpic++
CXXFLAGS = -std=c++11 -Wall -Wextra -O2 -pthread
LDFLAGS = -lssl -lcrypto -pthread

TARGET = blockchain_miner
SOURCES = main.cpp
HEADERS = Block.hpp Blockchain.hpp

SRC_DIR = .
OBJ_DIR = obj

OBJECTS = $(OBJ_DIR)/main.o

all: $(TARGET)

$(TARGET): $(OBJECTS)
	$(CXX) $(CXXFLAGS) -o $(TARGET) $(OBJECTS) $(LDFLAGS)

$(OBJ_DIR)/main.o: $(SRC_DIR)/main.cpp $(HEADERS)
	@mkdir -p $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c $(SRC_DIR)/main.cpp -o $(OBJ_DIR)/main.o

clean:
	rm -rf $(OBJ_DIR) $(TARGET)

run: $(TARGET)
	mpirun -np 1 ./$(TARGET)

run4: $(TARGET)
	mpirun -np 4 ./$(TARGET)

run6: $(TARGET)
	mpirun -np 6 ./$(TARGET)

run8: $(TARGET)
	mpirun --use-hwthread-cpus -np 8 ./$(TARGET)

runn: $(TARGET)
	mpirun --use-hwthread-cpus -np $(NP) ./$(TARGET)

.PHONY: all clean run run4 run8 runn
