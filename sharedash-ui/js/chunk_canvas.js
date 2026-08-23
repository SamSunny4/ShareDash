/**
 * ShareDash High-Performance 60 FPS Canvas Chunk Visualizer
 */

class ChunkVisualizer {
  constructor(canvasId, tooltipId) {
    this.canvas = document.getElementById(canvasId);
    this.tooltip = document.getElementById(tooltipId);
    this.ctx = this.canvas.getContext('2d');
    this.chunkStates = [];
    this.totalChunks = 0;
    this.hoveredChunk = null;

    // Palette tokens
    this.colors = {
      pending: '#243044',
      pendingBorder: '#334155',
      inFlight: '#fbbf24',
      usb: '#14b8a6',
      wifi: '#10b981',
      lan: '#0ea5e9',
      corrupt: '#ef4444',
    };

    this.initEvents();
    this.resize();
    window.addEventListener('resize', () => this.resize());
  }

  resize() {
    const rect = this.canvas.parentElement.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    this.canvas.width = rect.width * dpr;
    this.canvas.height = 180 * dpr;
    this.ctx.scale(dpr, dpr);
    this.render();
  }

  updateChunks(chunkStates) {
    this.chunkStates = chunkStates || [];
    this.totalChunks = this.chunkStates.length;
    this.render();
  }

  getChunkColor(chunk) {
    if (!chunk) return this.colors.pending;

    const state = (chunk.state || '').toUpperCase();
    if (state === 'IN_FLIGHT' || state === 'INFLIGHT') return this.colors.inFlight;
    if (state === 'CORRUPTED' || state === 'CORRUPT') return this.colors.corrupt;

    if (state === 'COMPLETED') {
      const tid = (chunk.transport_badge || chunk.transport_id || '').toLowerCase();
      if (tid.includes('usb')) return this.colors.usb;
      if (tid.includes('wifi') || tid.includes('direct')) return this.colors.wifi;
      if (tid.includes('lan')) return this.colors.lan;
      return this.colors.wifi; // default completed
    }

    return this.colors.pending;
  }

  render() {
    const rect = this.canvas.parentElement.getBoundingClientRect();
    const width = rect.width;
    const height = 180;

    this.ctx.clearRect(0, 0, width, height);

    if (this.totalChunks === 0) {
      this.ctx.fillStyle = '#64748b';
      this.ctx.font = '13px Inter, sans-serif';
      this.ctx.textAlign = 'center';
      this.ctx.fillText('No active transfer chunks to visualize', width / 2, height / 2);
      return;
    }

    const cols = Math.min(Math.max(Math.ceil(Math.sqrt(this.totalChunks * 3)), 20), 100);
    const rows = Math.ceil(this.totalChunks / cols);

    const padding = 2;
    const cellW = (width - (cols + 1) * padding) / cols;
    const cellH = Math.min((height - (rows + 1) * padding) / rows, cellW);

    for (let i = 0; i < this.totalChunks; i++) {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const x = padding + col * (cellW + padding);
      const y = padding + row * (cellH + padding);

      const chunk = this.chunkStates[i];
      const color = this.getChunkColor(chunk);

      this.ctx.fillStyle = color;
      this.ctx.fillRect(x, y, cellW, cellH);

      // Highlight hovered cell
      if (this.hoveredChunk === i) {
        this.ctx.strokeStyle = '#ffffff';
        this.ctx.lineWidth = 1.5;
        this.ctx.strokeRect(x - 1, y - 1, cellW + 2, cellH + 2);
      }
    }
  }

  initEvents() {
    this.canvas.addEventListener('mousemove', (e) => {
      const rect = this.canvas.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;

      if (this.totalChunks === 0) return;

      const cols = Math.min(Math.max(Math.ceil(Math.sqrt(this.totalChunks * 3)), 20), 100);
      const rows = Math.ceil(this.totalChunks / cols);
      const padding = 2;
      const cellW = (rect.width - (cols + 1) * padding) / cols;
      const cellH = Math.min((rect.height - (rows + 1) * padding) / rows, cellW);

      const col = Math.floor((x - padding) / (cellW + padding));
      const row = Math.floor((y - padding) / (cellH + padding));

      if (col >= 0 && col < cols && row >= 0 && row < rows) {
        const index = row * cols + col;
        if (index < this.totalChunks) {
          this.hoveredChunk = index;
          const chunk = this.chunkStates[index];
          this.showTooltip(e.clientX - rect.left, e.clientY - rect.top, chunk, index);
          this.render();
          return;
        }
      }

      this.hideTooltip();
    });

    this.canvas.addEventListener('mouseleave', () => {
      this.hideTooltip();
    });
  }

  showTooltip(x, y, chunk, index) {
    if (!this.tooltip) return;
    const state = chunk ? (chunk.state || 'PENDING') : 'PENDING';
    const transport = chunk && (chunk.transport_badge || chunk.transport_id) ? ` &bull; ${chunk.transport_badge || chunk.transport_id}` : '';

    this.tooltip.innerHTML = `<strong>Chunk #${index}</strong> &bull; ${state}${transport}`;
    this.tooltip.style.left = `${x}px`;
    this.tooltip.style.top = `${y}px`;
    this.tooltip.classList.remove('hidden');
  }

  hideTooltip() {
    this.hoveredChunk = null;
    if (this.tooltip) {
      this.tooltip.classList.add('hidden');
    }
    this.render();
  }
}

window.ChunkVisualizer = ChunkVisualizer;
