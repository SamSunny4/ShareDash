/**
 * ShareDash Windows App — Quick Share Controller & Multipath Engine Client
 */

document.addEventListener('DOMContentLoaded', () => {
  const visualizer = new ChunkVisualizer('chunk-canvas', 'canvas-tooltip');
  const pairingUI = new PairingManagerUI();

  let currentTransferId = null;
  let activeTargetDevice = null;
  let telemetryActive = false; // True once WebSocket telemetry starts, suppresses XHR progress

  // DOM Views
  const viewDiscovery = document.getElementById('view-discovery');
  const viewTransfer = document.getElementById('view-transfer');

  // DOM Elements - Header
  const myDeviceName = document.getElementById('my-device-name');
  const btnOpenDownloads = document.getElementById('btn-open-received-folder') || document.getElementById('btn-open-downloads');

  // DOM Elements - Bridges
  const bridgeRecommendationText = document.getElementById('bridge-recommendation-text');
  const bridgeCardUsb = document.getElementById('bridge-card-usb');
  const usbBridgeStatusTag = document.getElementById('usb-bridge-status-tag');
  const usbBridgeDesc = document.getElementById('usb-bridge-desc');
  const lanBridgeDesc = document.getElementById('lan-bridge-desc');

  // DOM Elements - Radar
  const orbitingDevicesContainer = document.getElementById('orbiting-devices-container');
  const btnTriggerFilePicker = document.getElementById('btn-trigger-file-picker');
  const btnTriggerFolderPicker = document.getElementById('btn-trigger-folder-picker');
  const inputSelectFiles = document.getElementById('input-select-files');
  const inputSelectFolder = document.getElementById('input-select-folder');
  const dropzoneOverlay = document.getElementById('dropzone-overlay');
  const dropzoneTargetText = document.getElementById('dropzone-target-text');

  // DOM Elements - Active Transfer View
  const transferTargetName = document.getElementById('transfer-target-name');
  const heroTransferMeta = document.getElementById('hero-transfer-meta');
  const heroStatusTag = document.getElementById('hero-status-tag');
  const heroAggregateSpeed = document.getElementById('hero-aggregate-speed');
  const heroAggregateGbps = document.getElementById('hero-aggregate-gbps');
  const heroProgressBytes = document.getElementById('hero-progress-bytes');
  const heroEta = document.getElementById('hero-eta');
  const heroProgressFill = document.getElementById('hero-progress-fill');
  const chunkCountBadge = document.getElementById('chunk-count-badge');
  const btnCancelTransfer = document.getElementById('btn-cancel-transfer');
  const transferCompletedBar = document.getElementById('transfer-completed-bar');
  const btnBackToRadar = document.getElementById('btn-back-to-radar');
  const btnOpenFileLocation = document.getElementById('btn-open-file-location');

  // Transport Breakdown Elements
  const usbSpeed = document.getElementById('usb-speed');
  const usbRtt = document.getElementById('usb-rtt');
  const usbBar = document.getElementById('usb-bar');
  const usbStats = document.getElementById('usb-stats');

  const wifiSpeed = document.getElementById('wifi-speed');
  const wifiRtt = document.getElementById('wifi-rtt');
  const wifiBar = document.getElementById('wifi-bar');
  const wifiStats = document.getElementById('wifi-stats');

  const lanSpeed = document.getElementById('lan-speed');
  const lanRtt = document.getElementById('lan-rtt');
  const lanBar = document.getElementById('lan-bar');
  const lanStats = document.getElementById('lan-stats');

  // Footer History
  const btnToggleHistory = document.getElementById('btn-toggle-history');
  const footerHistoryDrawer = document.getElementById('footer-history-drawer');
  const historyListContainer = document.getElementById('history-list-container');

  // 1. Fetch Local Device Info
  async function loadDeviceInfo() {
    try {
      const resp = await fetch('/api/v1/info');
      if (resp.ok) {
        const info = await resp.json();
        myDeviceName.textContent = info.device_name;
      }
    } catch (e) {
      console.warn('Failed to load device info:', e);
    }
  }

  // 2. Fetch Active Connection Bridges
  async function loadConnectionBridges() {
    try {
      const resp = await fetch('/api/v1/bridges');
      if (resp.ok) {
        const data = await resp.json();

        // Update USB Bridge
        if (data.usb.connected) {
          bridgeCardUsb.classList.add('active');
          usbBridgeStatusTag.textContent = '3.2 Gbps Connected';
          usbBridgeStatusTag.className = 'bcard-tag active';
          usbBridgeDesc.textContent = data.usb.device_model || 'Android USB Fast-Path';

          // Instantly auto-connect and move to connected screen upon USB detection!
          if (!isSecurelyConnected) {
            const usbDevName = data.usb.device_model || 'Android Phone (USB 3.2 Cable)';
            activeTargetDevice = { id: 'usb-fastpath', name: usbDevName };
            enterConnectedScreen(usbDevName);
          }
        } else {
          bridgeCardUsb.classList.remove('active');
          usbBridgeStatusTag.textContent = 'Plug-in Ready';
          usbBridgeStatusTag.className = 'bcard-tag';
          usbBridgeDesc.textContent = 'Plug in USB-C for 3.2 Gbps Turbo Boost';
        }

        // Update LAN & Recommendation
        lanBridgeDesc.textContent = `Current IP: ${data.lan.local_ip}`;
        bridgeRecommendationText.textContent = data.recommended_action;
      }
    } catch (e) {
      console.warn('Failed to load bridges:', e);
    }
  }

  let discoveredPeersMap = {};
  let isSecurelyConnected = false;
  let currentPairingPin = "000000";

  // 3. Fetch Discovered Nearby Devices (Radar View)
  async function loadDiscoveredPeers() {
    try {
      const resp = await fetch('/api/v1/peers');
      if (resp.ok) {
        const peers = await resp.json();
        renderRadarDevices(peers);
      }
    } catch (e) {
      console.warn('Failed to load peers:', e);
    }
  }

  function renderRadarDevices(peers) {
    const promptTitle = document.querySelector('#stage-discovery-prompt h2');
    const promptDesc = document.querySelector('#stage-discovery-prompt p');

    if (!peers || peers.length === 0) {
      orbitingDevicesContainer.innerHTML = '';
      if (promptTitle) promptTitle.textContent = 'Looking for nearby devices...';
      if (promptDesc) promptDesc.innerHTML = 'Make sure ShareDash is open on your Android phone or PC. <strong>Click a device above to establish a secure connection.</strong>';
      return;
    }

    if (promptTitle) promptTitle.textContent = `Found ${peers.length} Nearby Device${peers.length > 1 ? 's' : ''}`;
    if (promptDesc) promptDesc.innerHTML = 'Click your device on the radar to establish an ultra-fast encrypted transfer.';

    const positions = ['node-pos-1', 'node-pos-2', 'node-pos-3', 'node-pos-4'];

    discoveredPeersMap = {};
    peers.forEach(p => discoveredPeersMap[p.device_id] = p);

    orbitingDevicesContainer.innerHTML = peers.map((p, idx) => {
      const posClass = positions[idx % positions.length];
      const isPhone = p.os_name.toLowerCase().includes('android') || p.friendly_name.toLowerCase().includes('phone') || p.friendly_name.toLowerCase().includes('galaxy') || p.friendly_name.toLowerCase().includes('pixel') || p.friendly_name.toLowerCase().includes('a56') || p.friendly_name.toLowerCase().includes('s24');
      const iconSvg = isPhone ? `
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect>
          <line x1="12" y1="18" x2="12.01" y2="18"></line>
        </svg>
      ` : `
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
          <line x1="8" y1="21" x2="16" y2="21"></line>
          <line x1="12" y1="17" x2="12" y2="21"></line>
        </svg>
      `;

      const bridgesSummary = p.is_compatible === false
        ? `⚠️ Incompatible (v${p.app_version || '?'})`
        : (p.supported_transports || ['Wi-Fi']).join(' • ');

      return `
        <div class="device-node ${isPhone ? 'phone' : 'laptop'} ${posClass} ${p.is_compatible === false ? 'incompatible' : ''}" data-device-id="${p.device_id}" data-device-name="${p.friendly_name}" title="${p.is_compatible === false ? 'Incompatible Version' : 'Click to share with ' + p.friendly_name}">
          <div class="device-avatar ${isPhone ? 'phone' : 'laptop'}">
            ${iconSvg}
            <span class="device-online-dot ${p.is_compatible === false ? 'warning' : ''}"></span>
          </div>
          <div class="device-node-info">
            <span class="dnode-name">${p.friendly_name}</span>
            <span class="dnode-bridges">${bridgesSummary}</span>
          </div>
        </div>
      `;
    }).join('');

    // Attach click listeners to device nodes
    document.querySelectorAll('.device-node').forEach(node => {
      node.addEventListener('click', () => {
        const name = node.getAttribute('data-device-name');
        const id = node.getAttribute('data-device-id');
        selectDeviceForShare(id, name);
      });
    });
  }

  // Secure Connection Modals & Elements
  const stageDiscoveryPrompt = document.getElementById('stage-discovery-prompt');
  const stagePairingHandshake = document.getElementById('stage-pairing-handshake');
  const stageSecureConnected = document.getElementById('stage-secure-connected');
  const handshakeTargetName = document.getElementById('handshake-target-name');
  const connectedDeviceName = document.getElementById('connected-device-name');
  const btnConfirmHandshake = document.getElementById('btn-confirm-handshake');
  const btnCancelHandshake = document.getElementById('btn-cancel-handshake');
  const btnDisconnectDevice = document.getElementById('btn-disconnect-device');

  // Modal Overlays
  const modalIncomingPair = document.getElementById('modal-incoming-pair');
  const incomingPairDeviceName = document.getElementById('incoming-pair-device-name');
  const btnAcceptIncomingPair = document.getElementById('btn-accept-incoming-pair');
  const btnRejectIncomingPair = document.getElementById('btn-reject-incoming-pair');

  const modalConnecting = document.getElementById('modal-connecting');
  const connectingDeviceName = document.getElementById('connecting-device-name');
  const connectingSubtitleText = document.getElementById('connecting-subtitle-text');
  const btnCancelConnect = document.getElementById('btn-cancel-connect');

  let currentPendingIncoming = null;

  // 1. PC Initiates Outgoing Connection to Device on Radar
  function selectDeviceForShare(deviceId, deviceName) {
    const peer = discoveredPeersMap[deviceId];
    if (peer && peer.is_compatible === false) {
      alert(`⚠️ Incompatible App Version\n\n${deviceName} is running v${peer.app_version || 'unknown'}, which is incompatible with this ShareDash version (v0.1.0).\n\nPlease update both apps to the latest version to connect.`);
      return;
    }

    activeTargetDevice = { id: deviceId, name: deviceName };
    const pin = Math.floor(100000 + Math.random() * 900000).toString();
    currentPairingPin = pin;

    // Fill PIN in outgoing connecting modal
    for (let i = 0; i < 6; i++) {
      const el = document.getElementById(`conn-p${i + 1}`);
      if (el) el.textContent = pin[i];
    }

    if (connectingDeviceName) connectingDeviceName.textContent = `Connecting to ${deviceName}...`;
    if (connectingSubtitleText) connectingSubtitleText.textContent = `Verify matching PIN (${pin.substring(0,3)} ${pin.substring(3)}) on ${deviceName}`;

    // Show Outgoing Connecting Modal with pulsing rings
    if (modalConnecting) modalConnecting.classList.remove('hidden');

    const hsStepSyn = document.getElementById('hs-step-syn');
    const hsStepSynack = document.getElementById('hs-step-synack');
    const hsStepAck = document.getElementById('hs-step-ack');
    if (hsStepSyn) hsStepSyn.className = 'hs-step active';
    if (hsStepSynack) hsStepSynack.className = 'hs-step';
    if (hsStepAck) hsStepAck.className = 'hs-step';

    const targetIp = peer ? (peer.remote_addr ? peer.remote_addr.split(':')[0] : '127.0.0.1') : '127.0.0.1';
    const targetPort = peer ? peer.server_port : 54321;

    fetch('/api/v1/pair/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        target_ip: targetIp,
        target_port: targetPort,
        pin_code: pin,
        device_name: deviceName,
      }),
    }).then(() => {
      // Advance stepper: SYN sent → waiting for SYN-ACK from phone
      if (hsStepSyn) hsStepSyn.className = 'hs-step active done';
      if (hsStepSynack) hsStepSynack.className = 'hs-step active';
    }).catch(e => console.warn('Pair connect error:', e));
  }

  // Cancel Outgoing Connection
  if (btnCancelConnect) {
    btnCancelConnect.addEventListener('click', async () => {
      if (modalConnecting) modalConnecting.classList.add('hidden');
      activeTargetDevice = null;
      try {
        await fetch('/api/v1/pair/respond', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'REJECT' }),
        });
      } catch (_) {}
    });
  }

  // Transition Helpers for Dedicated Connected Screen
  function enterConnectedScreen(deviceName) {
    isSecurelyConnected = true;
    const radarContainer = document.getElementById('radar-container');
    if (radarContainer) radarContainer.classList.add('hidden');
    if (stageDiscoveryPrompt) stageDiscoveryPrompt.classList.add('hidden');
    if (stagePairingHandshake) stagePairingHandshake.classList.add('hidden');
    if (stageSecureConnected) stageSecureConnected.classList.remove('hidden');
    if (connectedDeviceName) connectedDeviceName.textContent = deviceName;
    if (dropzoneTargetText) dropzoneTargetText.textContent = `Ready to share with ${deviceName}`;
    if (viewDiscovery) viewDiscovery.classList.add('connected-mode');

    // Fetch and display the actual transport mode used
    fetch('/api/v1/transports/detect')
      .then(r => r.json())
      .then(data => {
        if (!data.best_transport) return;
        const el = document.getElementById('connected-transport-label');
        if (el) {
          // Choose an icon based on transport type
          let icon = '🏠';
          if (data.best_transport.toLowerCase().includes('usb')) icon = '🔌';
          else if (data.best_transport.toLowerCase().includes('direct') || data.best_transport.toLowerCase().includes('hotspot')) icon = '📶';
          else if (data.best_transport.toLowerCase().includes('quic') || data.best_transport.toLowerCase().includes('internet')) icon = '🌐';
          el.textContent = `${icon} ${data.best_transport}`;
          el.title = data.recommendation;
        }
      })
      .catch(() => {});
  }

  function exitConnectedScreen() {
    isSecurelyConnected = false;
    activeTargetDevice = null;
    const radarContainer = document.getElementById('radar-container');
    if (radarContainer) radarContainer.classList.remove('hidden');
    if (stageSecureConnected) stageSecureConnected.classList.add('hidden');
    if (stagePairingHandshake) stagePairingHandshake.classList.add('hidden');
    if (stageDiscoveryPrompt) stageDiscoveryPrompt.classList.remove('hidden');
    if (viewDiscovery) viewDiscovery.classList.remove('connected-mode');
  }

  // 2. PC Receives Incoming Connection Consent Modal
  if (btnAcceptIncomingPair) {
    btnAcceptIncomingPair.addEventListener('click', async () => {
      if (modalIncomingPair) modalIncomingPair.classList.add('hidden');

      const deviceName = currentPendingIncoming ? currentPendingIncoming.initiator_name : 'Nearby Phone';
      activeTargetDevice = {
        id: currentPendingIncoming ? currentPendingIncoming.initiator_device_id : 'paired-peer',
        name: deviceName
      };

      try {
        await fetch('/api/v1/pair/respond', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'ACCEPT' }),
        });
        await fetch('/api/v1/pair/confirm', { method: 'POST' });
      } catch (_) {}

      enterConnectedScreen(deviceName);
    });
  }

  if (btnRejectIncomingPair) {
    btnRejectIncomingPair.addEventListener('click', async () => {
      if (modalIncomingPair) modalIncomingPair.classList.add('hidden');
      currentPendingIncoming = null;
      try {
        await fetch('/api/v1/pair/respond', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'REJECT' }),
        });
      } catch (_) {}
    });
  }

  // Disconnect Button
  if (btnDisconnectDevice) {
    btnDisconnectDevice.addEventListener('click', async () => {
      exitConnectedScreen();
      try {
        await fetch('/api/v1/pair/respond', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'REJECT' }),
        });
      } catch (_) {}
    });
  }

  // Polling for Pairing State (Incoming requests & Acceptance from phone)
  async function pollPairingState() {
    try {
      const resp = await fetch('/api/v1/pair/status');
      if (!resp.ok) return;
      const data = await resp.json();

      // Case A: Incoming Connection Request from Phone -> Show Consent Dialog
      if (data.pending_request && data.pending_request.status === 'PENDING') {
        const req = data.pending_request;
        currentPendingIncoming = req;

        if (incomingPairDeviceName) {
          incomingPairDeviceName.textContent = `${req.initiator_name} (${req.initiator_ip})`;
        }

        const pinStr = (req.pin_code || '000000').padStart(6, '0');
        for (let i = 0; i < 6; i++) {
          const el = document.getElementById(`inc-p${i + 1}`);
          if (el) el.textContent = pinStr[i];
        }

        // Hide any outgoing connecting modal that might be open
        if (modalConnecting && !modalConnecting.classList.contains('hidden')) {
          modalConnecting.classList.add('hidden');
        }

        if (modalIncomingPair && modalIncomingPair.classList.contains('hidden')) {
          modalIncomingPair.classList.remove('hidden');
        }
      } else if (!data.pending_request || data.pending_request.status !== 'PENDING') {
        if (modalIncomingPair && !modalIncomingPair.classList.contains('hidden')) {
          modalIncomingPair.classList.add('hidden');
        }
      }

      // Case B: Remote Phone accepted our outgoing request -> Complete handshake
      if (data.is_paired && data.paired_device_name && !isSecurelyConnected) {
        activeTargetDevice = { id: 'paired-peer', name: data.paired_device_name };

        // Complete stepper animation: SYN-ACK received -> ACK
        const hsStepSynack = document.getElementById('hs-step-synack');
        const hsStepAck = document.getElementById('hs-step-ack');
        if (hsStepSynack) hsStepSynack.className = 'hs-step active done';
        if (hsStepAck) hsStepAck.className = 'hs-step active done';

        setTimeout(() => {
          if (modalConnecting) modalConnecting.classList.add('hidden');
          enterConnectedScreen(data.paired_device_name);
        }, 400);
      } else if (!data.is_paired && isSecurelyConnected && !data.pending_request) {
        // Peer disconnected remotely -> return to discovery screen
        exitConnectedScreen();
      }
    } catch (_) {}
  }

  setInterval(pollPairingState, 500);

  // 4. File Picker & Transfer Initiation (Only After Secure Connection)
  btnTriggerFilePicker.addEventListener('click', () => {
    if (!isSecurelyConnected) {
      alert('Please click a device on the radar and establish a secure connection first.');
      return;
    }
    inputSelectFiles.click();
  });

  btnTriggerFolderPicker.addEventListener('click', () => {
    if (!isSecurelyConnected) {
      alert('Please click a device on the radar and establish a secure connection first.');
      return;
    }
    inputSelectFolder.click();
  });

  inputSelectFiles.addEventListener('change', (e) => {
    if (e.target.files.length > 0) {
      handleFilesSelected(Array.from(e.target.files));
    }
  });

  inputSelectFolder.addEventListener('change', (e) => {
    if (e.target.files.length > 0) {
      handleFilesSelected(Array.from(e.target.files));
    }
  });

  async function handleFilesSelected(files) {
    if (!files || files.length === 0) return;

    const fileCount = files.length;
    let totalSizeBytes = 0;
    files.forEach(f => totalSizeBytes += f.size);
    const totalMb = (totalSizeBytes / (1024 * 1024)).toFixed(1);
    const primaryName = files[0].name;

    // Switch view to Active Multipath Transfer
    viewDiscovery.classList.add('hidden');
    viewTransfer.classList.remove('hidden');
    transferCompletedBar.classList.add('hidden');

    transferTargetName.textContent = activeTargetDevice ? activeTargetDevice.name : 'Local Transfer & Storage';
    heroTransferMeta.textContent = fileCount === 1 ? `${primaryName} (${totalMb} MB)` : `${primaryName} + ${fileCount - 1} other files (${totalMb} MB)`;
    heroStatusTag.textContent = 'Transferring...';
    btnCancelTransfer.classList.remove('hidden');

    const formData = new FormData();
    files.forEach(f => formData.append('files', f, f.name));

    const startTime = performance.now();
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/v1/transfers/send');

    // XHR progress is only used as a fallback before real multipath telemetry arrives
    telemetryActive = false;

    xhr.upload.onprogress = (e) => {
      // Once WebSocket telemetry is active, skip XHR-based UI updates to prevent flickering
      if (telemetryActive) return;

      if (e.lengthComputable) {
        const elapsedSec = Math.max((performance.now() - startTime) / 1000, 0.05);
        const mbps = (e.loaded * 8) / (elapsedSec * 1000 * 1000);
        const percent = Math.min((e.loaded / e.total) * 100, 100);
        const loadedMb = (e.loaded / (1024 * 1024)).toFixed(1);
        const totalMbStr = (e.total / (1024 * 1024)).toFixed(1);

        const remainingBytes = e.total - e.loaded;
        const etaSec = mbps > 0 ? Math.round((remainingBytes * 8) / (mbps * 1000 * 1000)) : 0;

        heroAggregateSpeed.textContent = (mbps / 8).toFixed(1);
        heroAggregateGbps.textContent = `~${(mbps / 1000).toFixed(2)} Gbps`;
        heroProgressBytes.textContent = `${loadedMb} MB / ${totalMbStr} MB (${percent.toFixed(0)}%)`;
        heroEta.textContent = percent >= 100 ? 'Finalizing...' : `ETA: ${etaSec}s`;
        heroProgressFill.style.width = `${percent}%`;

        // Update real active LAN/USB gauges
        lanSpeed.textContent = (mbps / 8).toFixed(1);
        lanRtt.textContent = '<1 ms';
        lanBar.style.width = `${percent}%`;
        lanStats.textContent = `${loadedMb} MB sent`;
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        heroStatusTag.textContent = 'COMPLETED';
        heroStatusTag.className = 'transfer-state-tag completed';
        heroProgressFill.style.width = '100%';
        transferCompletedBar.classList.remove('hidden');
        btnCancelTransfer.classList.add('hidden');
        loadHistory();
      } else {
        heroStatusTag.textContent = 'FAILED';
        heroStatusTag.className = 'transfer-state-tag failed';
        alert(`Transfer error: ${xhr.statusText}`);
      }
    };

    xhr.onerror = () => {
      heroStatusTag.textContent = 'FAILED';
      heroStatusTag.className = 'transfer-state-tag failed';
    };

    xhr.send(formData);
  }

  // Drag-and-Drop Global Listeners
  window.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropzoneOverlay.classList.remove('hidden');
  });

  window.addEventListener('dragleave', (e) => {
    if (e.relatedTarget === null || e.clientX <= 0 || e.clientY <= 0) {
      dropzoneOverlay.classList.add('hidden');
    }
  });

  window.addEventListener('drop', (e) => {
    e.preventDefault();
    dropzoneOverlay.classList.add('hidden');
    if (e.dataTransfer.files.length > 0) {
      handleFilesSelected(Array.from(e.dataTransfer.files));
    }
  });

  // 5. Telemetry Updates
  function onTelemetryUpdate(telem) {
    // Mark telemetry as active so XHR progress updates are suppressed
    telemetryActive = true;
    currentTransferId = telem.transfer_id;

    // Update Speedometers
    const aggMbps = telem.aggregate.aggregate_mbps;
    heroAggregateSpeed.textContent = aggMbps.toFixed(1);
    heroAggregateGbps.textContent = `~${((aggMbps * 8) / 1000).toFixed(2)} Gbps`;

    const totalMb = (telem.aggregate.total_bytes_expected / (1024 * 1024)).toFixed(1);
    const completedMb = (telem.aggregate.total_bytes_transferred / (1024 * 1024)).toFixed(1);
    heroProgressBytes.textContent = `${completedMb} MB / ${totalMb} MB (${telem.aggregate.progress_pct.toFixed(1)}%)`;
    heroEta.textContent = telem.status === 'COMPLETED' ? 'Completed!' : `ETA: ${telem.aggregate.eta_seconds}s`;
    heroProgressFill.style.width = `${telem.aggregate.progress_pct}%`;

    heroStatusTag.textContent = telem.status;
    heroStatusTag.className = `transfer-state-tag ${telem.status.toLowerCase()}`;

    if (telem.status === 'COMPLETED') {
      transferCompletedBar.classList.remove('hidden');
      btnCancelTransfer.classList.add('hidden');
      loadHistory();
    }

    chunkCountBadge.textContent = `${telem.chunk_states.length} Chunks`;

    // Update Per-Bridge Breakdown Cards
    const transports = telem.transports || [];
    let maxThroughput = Math.max(...transports.map(t => t.current_mbps), 10.0);

    transports.forEach(t => {
      const name = (t.transport_id || '').toLowerCase();
      const speed = t.current_mbps.toFixed(1);
      const rtt = `${t.rtt_ms.toFixed(1)} ms`;
      const barPct = Math.min((t.current_mbps / maxThroughput) * 100, 100);

      if (name.includes('usb')) {
        usbSpeed.textContent = speed;
        usbRtt.textContent = rtt;
        usbBar.style.width = `${barPct}%`;
        usbStats.textContent = `${t.completed_chunks} chunks completed`;
      } else if (name.includes('wifi') || name.includes('direct')) {
        wifiSpeed.textContent = speed;
        wifiRtt.textContent = rtt;
        wifiBar.style.width = `${barPct}%`;
        wifiStats.textContent = `${t.completed_chunks} chunks completed`;
      } else if (name.includes('lan')) {
        lanSpeed.textContent = speed;
        lanRtt.textContent = rtt;
        lanBar.style.width = `${barPct}%`;
        lanStats.textContent = `${t.completed_chunks} chunks completed`;
      }
    });

    // Update Chunk Canvas Visualizer
    visualizer.updateChunks(telem.chunk_states);
  }

  // Navigation & Completion Actions
  btnBackToRadar.addEventListener('click', () => {
    viewTransfer.classList.add('hidden');
    viewDiscovery.classList.remove('hidden');
  });

  if (btnOpenFileLocation) {
    btnOpenFileLocation.addEventListener('click', triggerOpenReceivedFolder);
  }

  if (btnOpenDownloads) {
    btnOpenDownloads.addEventListener('click', triggerOpenReceivedFolder);
  }

  const btnStartPcHotspot = document.getElementById('btn-start-pc-hotspot');
  const btnOpenPcHotspot = document.getElementById('btn-open-pc-hotspot');

  async function triggerOpenPcHotspot() {
    try {
      await fetch('/api/v1/hotspot/open-settings', { method: 'POST' });
    } catch (_) {}
  }

  if (btnStartPcHotspot) {
    btnStartPcHotspot.addEventListener('click', triggerOpenPcHotspot);
  }
  if (btnOpenPcHotspot) {
    btnOpenPcHotspot.addEventListener('click', triggerOpenPcHotspot);
  }

  btnCancelTransfer.addEventListener('click', async () => {
    if (!currentTransferId) return;
    try {
      await fetch(`/api/v1/transfers/${currentTransferId}/cancel`, { method: 'POST' });
      viewTransfer.classList.add('hidden');
      viewDiscovery.classList.remove('hidden');
    } catch (e) {
      console.warn('Failed to cancel transfer:', e);
    }
  });

  // History Drawer Toggle
  btnToggleHistory.addEventListener('click', () => {
    footerHistoryDrawer.classList.toggle('hidden');
  });

  async function loadHistory() {
    try {
      const resp = await fetch('/api/v1/transfers');
      if (resp.ok) {
        const list = await resp.json();
        if (list && list.length > 0) {
          historyListContainer.innerHTML = list.map(t => `
            <div class="history-item">
              <div>
                <strong>${t.title}</strong>
                <div class="peer-sub">${(t.total_bytes / (1024 * 1024)).toFixed(1)} MB &bull; ${t.completed_chunks}/${t.total_chunks} chunks &bull; ${t.status}</div>
              </div>
              <span class="transfer-state-tag ${t.status.toLowerCase()}">${t.status}</span>
            </div>
          `).join('');
        }
      }
    } catch (e) {
      console.warn('Failed to load history:', e);
    }
  }

  // Initialize Telemetry WebSocket Client
  const telemetryClient = new TelemetryClient(onTelemetryUpdate);

  // Initial Load
  loadDeviceInfo();
  loadConnectionBridges();
  loadDiscoveredPeers();
  loadHistory();

  // Periodic polling for bridges and peers
  setInterval(loadConnectionBridges, 3000);
  setInterval(loadDiscoveredPeers, 1500);
});
