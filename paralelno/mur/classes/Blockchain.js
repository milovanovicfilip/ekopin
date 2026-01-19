import { Block } from "./Block.js";

export class Blockchain{
    constructor(miner) {
      this.chain = [this.createGenesisBlock(miner)];
      this.diff = 1;
      this.blockGenerationInterval = 10;
      this.diffAdjustInterval = 20;
      this.adjust_diff();
      this.cumulativeDifficulty=0;
    }
  
    //Inicializacijski blok
    createGenesisBlock(miner){
      let block = new Block(0, "0", miner);
      //block.hash = block.calculateHash();
      return block;
    }
  
    //Zadnji blok v verigi
    getLatestBlock() {
      return this.chain[this.chain.length - 1];
    }
    
    //Dodaj blok
    async addBlock(block){
      block.previousHash = this.getLatestBlock().hash;
      block.diff = this.diff;
      let output = await block.mine();
      this.chain.push(block);
      
      this.adjust_diff();
      return output;
    }
    
    //Trenutna tezavnost v omrezju
    adjust_diff(){
      let difficulty = 0;
      for(let i in this.chain){
        if(i>0){
          difficulty += Math.pow(2, this.chain[i].diff);
        }
       
      }
      console.log(`* Kumulativna tezavnost: ${difficulty}.`);
      this.cumulativeDifficulty = difficulty;

      if(this.chain.length <= this.diffAdjustInterval)
        return this.diff;
  
      let prevAdjustmentBlock = this.chain[this.chain.length-this.diffAdjustInterval];
      let timeExpected = this.blockGenerationInterval * this.diffAdjustInterval * 1000;
      const timeTaken = this.getLatestBlock().timestamp - prevAdjustmentBlock.timestamp;
  
      if(timeTaken < (timeExpected / 2)){
        this.diff = prevAdjustmentBlock.diff+1;
        console.log(`? Tezavnost se je povecala: ${this.diff}`);
        return this.diff;
      }else if(timeTaken > (timeExpected * 2)){
        this.diff = Math.max(1, prevAdjustmentBlock.diff - 1); 
        console.log(`? Tezavnost se je zmanjsala: ${this.diff}`);
        return this.diff;
      }else{
        this.diff = prevAdjustmentBlock.diff; 
        console.log(`? Tezavnost se ni spremenila: ${this.diff}`);
        return this.diff;
      }    
    }

    //Validacija casovnih znack, ter osnovna validacija
    isBlockValid(block) {
      if(block.index === 0){
        return true;
      }

      let previous_block = this.chain[block.index-1];

      if(block.index !== previous_block.index+1){
        return false;
      }
    
      if(block.previousHash !== previous_block.hash){
        return false;
      }

      let current_block_time = new Date(block.timestamp).getTime();
      let previous_block_time = new Date(previous_block.timestamp).getTime();

      if(current_block_time > Date.now()+60000){
        console.error("- Casovna znacka trenutnega bloka je vec od minute vecja od trenutnega casa.");
        return false;
      } 

      if(current_block_time < previous_block_time-60000) {
        console.error("- Casovna znacka trenutnega bloka je vec od minute manjsa od casovne znacke prejsnjega bloka.");
        return false;
      }

      return true;
    }

    //Validacija kumulativne tezavnosti
    isChainValid() {
      

      if(this.chain.length === 1) {
        return true;
      }
      
      for(let i in this.chain){
        if(!this.isBlockValid(this.chain[i]))
          return false;
      
      }
      
      return true;
    }
}