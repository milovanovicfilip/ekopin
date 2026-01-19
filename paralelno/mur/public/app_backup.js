import { Block } from "../classes/Block.js";
import { Blockchain } from "../classes/Blockchain.js";

let peer_id_display = document.querySelector('#peer-id');
let connect_button = document.querySelector('#connect-button');
let connect_peer_button = document.querySelector('#connect-peer-button');
let connections_container = document.querySelector('#connections');
let blocks_display = document.querySelector('#blocks-display');
let peer_id_input = document.querySelector('#peer-id-input');
let mine_button = document.querySelector('#mine-button');
let name_input = document.querySelector('#name-input');

let peer;
let conn;
let blockchain;
let connections = {}

//Povezi gumb
connect_button.addEventListener('click', ()=>{
    //Preverjanje imena
    if(name_input.value==""){
        appendConnections('! Morete vpisati ime.');
        console.error('! Morete vpisati ime.');
        return;
    }

    //Inicijalizacija peer-a in blockchain-a
    peer = new Peer();
    blockchain = new Blockchain(name_input.value.trim());

    //Izpis konekcij
    peer.on('open', async (id)=>{
        peer_id_display.textContent = id;
        connect_button.disabled = true;
    })

    setInterval(() => {
        try{
            for(let id in connections){
                if(connections[id].open)
                    connections[id].send(JSON.stringify({type: 'cumulative_diff', diff: blockchain.cumulativeDifficulty}));
            }
        }catch(error){
            console.error(`! Prislo je do napake pri deljenju kumulativne tezavnosti.`);
        }
    }, 5000);


    peer.on('connection', async (incomingConn)=>{
        incomingConn.on('close', ()=>{
            delete connections[incomingConn.peer];
        })

        incomingConn.on('data', async (data) => {
            try{
                let message = JSON.parse(data);

                if(message.type == "cumulative_diff"){
                    if(message.diff > blockchain.cumulativeDifficulty){
                        incomingConn.send(JSON.stringify({type: 'send_chain'}));
                    }
                }
                else if(message.type == "send_chain"){

                    console.log(`* Posilja se veriga.`);
                    incomingConn.send(JSON.stringify({type: 'blockchain', chain: blockchain}));

                }
                else if(message.type == "blockchain"){

                    blocks_display.textContent += `\n* Dobljena veriga.`;
                    blockchain.chain = message.chain.chain;
                    blockchain.diff = message.chain.diff;
                    blockchain.blockGenerationInterval = message.chain.blockGenerationInterval;
                    blockchain.diffAdjustInterval = message.chain.diffAdjustInterval;
                    blockchain.cumulativeDifficulty = message.chain.cumulativeDifficulty;
                    blocks_display.textContent = '';
                    for(let p in blockchain.chain){
                        display_single_block(blockchain.chain[p]);
                    }

                }
                else if(message.type == "new_block"){

                    let block = new Block(blockchain.chain.length, message.block.previousHash, message.block.miner, message.block.diff);

                    block.data = message.block.data;
                    block.timestamp = message.block.timestamp;
                    block.nonce = message.block.nonce;
                    block.hash = message.block.hash;
                    
                    await blockchain.addBlock(block);

                    if(!blockchain.isChainValid()){
                        return;
                    }

                    display_single_block(block);
                }
    
            }catch(error){
                console.error(`! Napaka: ${error}`)
            }
        });

        incomingConn.on('error', (error)=>{
            console.error(`! Napaka: ${error}`)
        });
  })
  
})

//Povezi na Peer
connect_peer_button.addEventListener('click', ()=>{
    let destId = peer_id_input.value.trim();
    conn = peer.connect(destId);
    if(!destId){
        appendConnections('? Vpisite Peer ID za povezovanje.');
        return;
    }

    conn.on('open', () => {
        appendConnections(`* Uspesno povezan na: ${destId}`);
        connections[destId] = conn;

        conn.on('close', ()=>{
            delete connections[conn.peer];
        })


        conn.on('data', async (data) => {
            try{
                let message = JSON.parse(data);
                
                if(message.type == "cumulative_diff"){

                    if(message.diff > blockchain.cumulativeDifficulty){
                        conn.send(JSON.stringify({type: 'send_chain'}));
                    }

                }
                else if(message.type == "send_chain"){

                    console.log(`* Posilja se veriga.`);
                    conn.send(JSON.stringify({type: 'blockchain', chain: blockchain}));

                }
                else if(message.type == "blockchain"){

                    blocks_display.textContent += `\n* Dobljena veriga.`;
                    blockchain.chain = message.chain.chain;
                    blockchain.diff = message.chain.diff;
                    blockchain.blockGenerationInterval = message.chain.blockGenerationInterval;
                    blockchain.diffAdjustInterval = message.chain.diffAdjustInterval;
                    blockchain.cumulativeDifficulty = message.chain.cumulativeDifficulty;

                }
                else if(message.type == "new_block"){

                    let block = new Block(blockchain.chain.length, message.block.previousHash, message.block.miner, message.block.diff);

                    block.data = message.block.data;
                    block.timestamp = message.block.timestamp;
                    block.nonce = message.block.nonce;
                    block.hash = message.block.hash;
                    
                    await blockchain.addBlock(block);

                    if(!blockchain.isChainValid()){
                        return;
                    }

                    display_single_block(block);
                }
            }catch(error){
                console.error(`! Napaka: ${error}`)
            }
        });
    });

    conn.on('error', (error)=>{
        console.error(`! Napaka: ${error}`)
    });
})

//Rudarjenje
mine_button.addEventListener('click',async ()=>{
    let block = new Block(blockchain.chain.length, blockchain.getLatestBlock().hash, name_input.value, blockchain.diff);
    
    let output = await blockchain.addBlock(block);
    for(let p in output){
        appendConnections(output[p]);
    }
    
    blockchain.isChainValid();

    try{
        for(let id in connections){
            if(connections[id].open)
                connections[id].send(JSON.stringify({type: 'new_block', block: block}));
        }
    }catch(error){
        console.error(`! Prislo je do napake pri posiljanju bloka.`);
    }
   
    display_single_block(block);

    console.log(`\n* Tezavnost: ${blockchain.diff}`);

    setTimeout(()=>{
        mine_button.click()
    }, 5000);
})

//Izpis v connections (drugi textarea)
function appendConnections(display){
    connections_container.textContent += '\n' + display;
    peer_id_input.value = '';
    connections_container.scrollTop = connections_container.scrollHeight;
}

//Izpis v prvi textarea
function display_single_block(block){
    blocks_display.textContent += `
    \nIndex: ${block.index}\nData: ${block.data}\nTimestamp: ${new Date(block.timestamp).toLocaleString()}\nPrevious hash: ${block.previousHash}\nDifficulty: ${block.diff}\nNonce: ${block.nonce}\nMiner: ${block.miner}\nHash: ${block.hash}\n`;
    blocks_display.scrollTop = blocks_display.scrollHeight;
}

//Copy gumb
document.getElementById('copy-button').addEventListener('click', function () {
    const peer_id = document.getElementById('peer-id').innerText;

    navigator.clipboard.writeText(peer_id).catch(err => {console.error(`! error with copying ${err}`);});
});