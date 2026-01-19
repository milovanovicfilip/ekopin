const API_URL = 'http://localhost:8080';
let autoRefreshInterval = null;

async function refreshStatus() {
    try {
        const response = await fetch(`${API_URL}/status`);
        const status = await response.json();
        
        document.getElementById('chainLength').textContent = status.chainLength;
        document.getElementById('difficulty').textContent = status.difficulty;
        document.getElementById('cumulativeDifficulty').textContent = status.cumulativeDifficulty.toLocaleString();
        document.getElementById('worldSize').textContent = status.worldSize;
        document.getElementById('totalThreads').textContent = status.totalThreads;
        
        const isValidEl = document.getElementById('isValid');
        if (status.isValid) {
            isValidEl.textContent = '✓ Da';
            isValidEl.className = 'valid-badge valid';
        } else {
            isValidEl.textContent = '✗ Ne';
            isValidEl.className = 'valid-badge invalid';
        }
    } catch (error) {
        console.error('Napaka pri pridobivanju statusa:', error);
    }
}

async function refreshChain() {
    try {
        const response = await fetch(`${API_URL}/chain`);
        const chain = await response.json();
        
        const chainContainer = document.getElementById('chain');
        
        if (chain.length === 0) {
            chainContainer.innerHTML = '<p class="loading">Veriga je prazna</p>';
            return;
        }
        
        chainContainer.innerHTML = chain.map(block => `
            <div class="block">
                <div class="block-header">
                    <span class="block-index">Blok #${block.index}</span>
                    <span class="valid-badge ${block.index === 0 ? 'valid' : 'valid'}">Veljaven</span>
                </div>
                <div class="block-hash">
                    <strong>Hash:</strong> ${block.hash}
                </div>
                <div class="block-details">
                    <div class="block-detail">
                        <span class="block-detail-label">Prejšnji hash:</span>
                        <span class="block-detail-value">${block.previousHash.substring(0, 20)}...</span>
                    </div>
                    <div class="block-detail">
                        <span class="block-detail-label">Podatki:</span>
                        <span class="block-detail-value">${block.data}</span>
                    </div>
                    <div class="block-detail">
                        <span class="block-detail-label">Nonce:</span>
                        <span class="block-detail-value">${block.nonce}</span>
                    </div>
                    <div class="block-detail">
                        <span class="block-detail-label">Težavnost:</span>
                        <span class="block-detail-value">${block.difficulty}</span>
                    </div>
                    <div class="block-detail">
                        <span class="block-detail-label">Časovna značka:</span>
                        <span class="block-detail-value">${new Date(block.timestamp).toLocaleString('sl-SI')}</span>
                    </div>
                </div>
            </div>
        `).join('');
        
        chainContainer.scrollTop = chainContainer.scrollHeight;
    } catch (error) {
        console.error('Napaka pri pridobivanju verige:', error);
        document.getElementById('chain').innerHTML = '<p class="loading">Napaka pri nalaganju verige</p>';
    }
}

async function refreshAll() {
    await Promise.all([refreshStatus(), refreshChain()]);
}

function toggleAutoRefresh() {
    const btn = document.getElementById('autoRefreshBtn');
    
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
        autoRefreshInterval = null;
        btn.textContent = 'Avtomatsko osveževanje';
        btn.classList.remove('active');
    } else {
        autoRefreshInterval = setInterval(refreshAll, 2000);
        btn.textContent = 'Ustavi avtomatsko osveževanje';
        btn.classList.add('active');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('refreshBtn').addEventListener('click', refreshAll);
    document.getElementById('autoRefreshBtn').addEventListener('click', toggleAutoRefresh);
    refreshAll();
});
