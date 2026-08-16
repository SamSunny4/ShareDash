/**
 * ShareDash Mobile Phone Pairing Module
 */

class PairingManagerUI {
  constructor() {
    this.modal = document.getElementById('modal-pairing');
    this.qrContainer = document.getElementById('pairing-qr-container');
    this.pinBoxes = document.getElementById('pairing-pin-boxes');
    this.webLink = document.getElementById('pairing-web-link');

    this.btnOpen = document.getElementById('btn-pair-phone');
    this.btnClose = document.getElementById('btn-close-pairing');
    this.btnDismiss = document.getElementById('btn-dismiss-pairing');

    this.initEvents();
  }

  initEvents() {
    this.btnOpen.addEventListener('click', () => this.openModal());
    this.btnClose.addEventListener('click', () => this.closeModal());
    this.btnDismiss.addEventListener('click', () => this.closeModal());

    this.modal.addEventListener('click', (e) => {
      if (e.target === this.modal) {
        this.closeModal();
      }
    });
  }

  async openModal() {
    this.modal.classList.remove('hidden');
    this.qrContainer.innerHTML = '<div class="qr-skeleton">Generating QR Code...</div>';

    try {
      const resp = await fetch('/api/v1/pair/create', { method: 'POST' });
      if (!resp.ok) throw new Error('Failed to create pairing session');

      const data = await resp.json();
      const session = data.session;

      // Render QR Code SVG
      this.qrContainer.innerHTML = session.qr_svg;

      // Render 6-digit PIN boxes
      const pinDigits = session.pin_code.split('');
      this.pinBoxes.innerHTML = pinDigits.map(d => `<span>${d}</span>`).join('');

      // Render direct web URL
      const hostUrl = `http://${session.server_endpoint}`;
      this.webLink.textContent = hostUrl;
      this.webLink.href = hostUrl;
    } catch (e) {
      this.qrContainer.innerHTML = `<div class="text-danger">Failed to generate pairing session: ${e.message}</div>`;
    }
  }

  closeModal() {
    this.modal.classList.add('hidden');
  }
}

window.PairingManagerUI = PairingManagerUI;
