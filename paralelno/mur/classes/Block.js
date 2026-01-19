export class Block{
    constructor(index, previousHash = "0", miner, diff, nonce = 0) {
      this.index = index;
      this.data = `To je blok ${this.index}`;
      this.timestamp = Date.now();
      this.previousHash = previousHash;
      this.miner = miner;
      this.diff = diff;
      this.nonce = nonce;
      this.hash = "";
    }
  
    //Funkcija za izracun hash-a
    async calculateHash() {
        const dataToHash = this.index + this.timestamp + this.data + this.previousHash + this.diff + this.nonce;

        const hashBuffer = await crypto.subtle.digest('SHA-256', (new TextEncoder).encode(dataToHash));
        const hashArray = Array.from(new Uint8Array(hashBuffer));

        this.hash = hashArray.map(byte => byte.toString(16).padStart(2,'0')).join('')
        return this.hash;
    }

    //Funkcija za rudarjenje
    async mine() {
      let output = [];
      this.nonce = 0;
      while(true){
        this.hash = await this.calculateHash();
        
        if(this.hash.substring(0, this.diff) == Array(this.diff+1).join("0")){
          output.push(`+ Veljaven blok: ${this.hash}, diff: ${this.diff}`);
          return output;
        }else{
          output.push(`- Neveljaven blok: ${this.hash}, diff: ${this.diff}`);
          this.nonce++;
        }
        
      }
    }
}
  