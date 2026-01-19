let blocks_display = document.querySelector('#blocks-display');
let mine_button = document.querySelector('#mine-button');
let name_input = document.querySelector('#name-input');
let block_data_input = document.querySelector('#block-data-input'); // nova input polja za podatke

// Funkcija za prikaz bloka
function display_single_block(block){
    blocks_display.textContent += `
Index: ${block.index}
Data: ${block.data}
Hash: ${block.hash}
Nonce: ${block.nonce}\n`;
    blocks_display.scrollTop = blocks_display.scrollHeight;
}

// Funkcija za osvežitev verige
async function refreshChain(){
    try{
        const res = await fetch("http://localhost:8080/chain");
        const chain = await res.json();
        blocks_display.textContent = '';
        chain.forEach(display_single_block);
    }catch(err){
        console.error("Napaka pri pridobivanju verige:", err);
    }
}

// Rudarjenje novega bloka
mine_button.addEventListener('click', async () => {
    const minerName = name_input.value.trim();
    const blockData = block_data_input.value.trim() || `Blok od ${minerName}`;

    if(!minerName){
        alert("Vnesi ime minerja!");
        return;
    }

    try{
        const res = await fetch("http://localhost:8080/mine", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ data: blockData })
        });
        const block = await res.json();
        display_single_block(block);
    }catch(err){
        console.error("Napaka pri rudarjenju:", err);
    }

    // Osveži celotno verigo
    refreshChain();
});

// Osnovno osveževanje verige ob zagonu strani
refreshChain();

// Periodično osveževanje (npr. na 5s)
setInterval(refreshChain, 5000);
