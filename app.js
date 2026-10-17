/**
 * ShareDash — Interactive Application Logic & Canvas Visualizer
 * Pure Vanilla JavaScript (Zero Dependencies)
 */

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initNavbar();
  initSpeedCalculator();
  initArchitectureTabs();
  initTerminalTabs();
  initFaqAccordion();
  initCopyButtons();
  initGitHubStars();
  initMultipathCanvas();
});

/* ==========================================================================
   1. Theme Management (Dark / Light Mode)
   ========================================================================== */
function initTheme() {
  const toggleBtn = document.getElementById('themeToggle');
  const htmlEl = document.documentElement;
  
  // Check persisted theme or system preference
  const savedTheme = localStorage.getItem('sharedash_theme');
  if (savedTheme) {
    htmlEl.setAttribute('data-theme', savedTheme);
  } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
    htmlEl.setAttribute('data-theme', 'light');
  }

  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => {
      const current = htmlEl.getAttribute('data-theme') || 'dark';
      const target = current === 'dark' ? 'light' : 'dark';
      htmlEl.setAttribute('data-theme', target);
      localStorage.setItem('sharedash_theme', target);
    });
  }
}

/* ==========================================================================
   2. Navbar Scroll State & Mobile Menu Toggle
   ========================================================================== */
function initNavbar() {
  const navbarWrapper = document.querySelector('.navbar-wrapper');
  const mobileMenuBtn = document.getElementById('mobileMenuBtn');
  const navLinks = document.getElementById('navLinks');
  const navLinkItems = document.querySelectorAll('.nav-links a');

  // Sticky scroll class
  window.addEventListener('scroll', () => {
    if (window.scrollY > 40) {
      navbarWrapper?.classList.add('scrolled');
    } else {
      navbarWrapper?.classList.remove('scrolled');
    }

    // Scroll spy for active section
    let currentSection = '';
    const sections = document.querySelectorAll('section[id]');
    const scrollPos = window.scrollY + 120;

    sections.forEach(sec => {
      const top = sec.offsetTop;
      const height = sec.offsetHeight;
      if (scrollPos >= top && scrollPos < top + height) {
        currentSection = sec.getAttribute('id');
      }
    });

    navLinkItems.forEach(link => {
      link.classList.remove('active');
      if (link.getAttribute('href') === `#${currentSection}`) {
        link.classList.add('active');
      }
    });
  });

  // Mobile drawer toggle
  if (mobileMenuBtn && navLinks) {
    mobileMenuBtn.addEventListener('click', () => {
      navLinks.classList.toggle('open');
    });

    // Close when clicking any link
    navLinkItems.forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('open');
      });
    });
  }
}

/* ==========================================================================
   3. Real-Time Interactive Transfer Speed Calculator
   ========================================================================== */
function initSpeedCalculator() {
  const slider = document.getElementById('fileSizeSlider');
  const sizeDisplay = document.getElementById('calcSizeDisplay');
  const timeSavedBanner = document.getElementById('timeSavedVal');

  // ETAs & Bar elements
  const etaShareDashMultipath = document.getElementById('etaShareDashMultipath');
  const etaShareDashUsb = document.getElementById('etaShareDashUsb');
  const etaQuickShare = document.getElementById('etaQuickShare');
  const etaAirDrop = document.getElementById('etaAirDrop');
  const etaLocalSend = document.getElementById('etaLocalSend');
  const etaBluetooth = document.getElementById('etaBluetooth');

  const barShareDashMultipath = document.getElementById('barShareDashMultipath');
  const barShareDashUsb = document.getElementById('barShareDashUsb');
  const barQuickShare = document.getElementById('barQuickShare');
  const barAirDrop = document.getElementById('barAirDrop');
  const barLocalSend = document.getElementById('barLocalSend');
  const barBluetooth = document.getElementById('barBluetooth');

  // Hardware benchmark speeds (MB/s)
  const SPEEDS = {
    multipath: 78.7,
    usbOnly: 107.2,
    quickShare: 42.0,
    airDrop: 38.0,
    localSend: 32.0,
    bluetooth: 2.0
  };

  function formatTime(seconds) {
    if (seconds < 60) {
      return `${seconds.toFixed(1)}s`;
    }
    const mins = Math.floor(seconds / 60);
    const remSecs = (seconds % 60).toFixed(0);
    return `${mins}m ${remSecs}s`;
  }

  function updateCalculations() {
    if (!slider) return;
    const sizeGB = parseFloat(slider.value);
    const sizeMB = sizeGB * 1024;

    // Display formatted size
    if (sizeGB === 2.8) {
      sizeDisplay.textContent = '2.8 GB (Movie File Benchmark)';
    } else if (sizeGB < 1) {
      sizeDisplay.textContent = `${(sizeGB * 1000).toFixed(0)} MB`;
    } else {
      sizeDisplay.textContent = `${sizeGB.toFixed(1)} GB`;
    }

    // Compute durations
    const tMultipath = sizeMB / SPEEDS.multipath;
    const tUsb = sizeMB / SPEEDS.usbOnly;
    const tQuickShare = sizeMB / SPEEDS.quickShare;
    const tAirDrop = sizeMB / SPEEDS.airDrop;
    const tLocalSend = sizeMB / SPEEDS.localSend;
    const tBluetooth = sizeMB / SPEEDS.bluetooth;

    // Update text
    if (etaShareDashMultipath) etaShareDashMultipath.textContent = formatTime(tMultipath);
    if (etaShareDashUsb) etaShareDashUsb.textContent = formatTime(tUsb);
    if (etaQuickShare) etaQuickShare.textContent = formatTime(tQuickShare);
    if (etaAirDrop) etaAirDrop.textContent = formatTime(tAirDrop);
    if (etaLocalSend) etaLocalSend.textContent = formatTime(tLocalSend);
    if (etaBluetooth) etaBluetooth.textContent = formatTime(tBluetooth);

    // Compute time saved vs Quick Share
    const savedSeconds = tQuickShare - tMultipath;
    if (timeSavedBanner) {
      timeSavedBanner.textContent = formatTime(savedSeconds);
    }

    // Relative bar widths (normalized against fastest)
    const baseSpeed = SPEEDS.multipath;
    if (barShareDashMultipath) barShareDashMultipath.style.width = '100%';
    if (barShareDashUsb) barShareDashUsb.style.width = `${Math.min(100, (SPEEDS.multipath / SPEEDS.usbOnly) * 100)}%`;
    if (barQuickShare) barQuickShare.style.width = `${(SPEEDS.quickShare / baseSpeed) * 100}%`;
    if (barAirDrop) barAirDrop.style.width = `${(SPEEDS.airDrop / baseSpeed) * 100}%`;
    if (barLocalSend) barLocalSend.style.width = `${(SPEEDS.localSend / baseSpeed) * 100}%`;
    if (barBluetooth) barBluetooth.style.width = `${Math.max(3, (SPEEDS.bluetooth / baseSpeed) * 100)}%`;
  }

  if (slider) {
    slider.addEventListener('input', updateCalculations);
    updateCalculations();
  }
}

/* ==========================================================================
   4. Architecture Tabs
   ========================================================================== */
function initArchitectureTabs() {
  const tabBtns = document.querySelectorAll('.arch-tab-btn');
  const tabContents = document.querySelectorAll('.arch-tab-content');

  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = `tab-${btn.getAttribute('data-tab')}`;
      tabBtns.forEach(b => b.classList.remove('active'));
      tabContents.forEach(c => c.classList.remove('active'));

      btn.classList.add('active');
      const targetContent = document.getElementById(targetId);
      if (targetContent) targetContent.classList.add('active');
    });
  });
}

/* ==========================================================================
   5. Terminal CLI Tabs
   ========================================================================== */
function initTerminalTabs() {
  const cliBtns = document.querySelectorAll('.cli-tab-btn');
  const cliScreens = document.querySelectorAll('.cli-screen');

  cliBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = `cli-${btn.getAttribute('data-cli')}`;
      cliBtns.forEach(b => b.classList.remove('active'));
      cliScreens.forEach(s => s.classList.remove('active'));

      btn.classList.add('active');
      const targetScreen = document.getElementById(targetId);
      if (targetScreen) targetScreen.classList.add('active');
    });
  });
}

/* ==========================================================================
   6. FAQ Accordion
   ========================================================================== */
function initFaqAccordion() {
  const faqItems = document.querySelectorAll('.faq-item');

  faqItems.forEach(item => {
    const questionBtn = item.querySelector('.faq-question');
    if (questionBtn) {
      questionBtn.addEventListener('click', () => {
        const isOpen = item.classList.contains('open');
        // Close other items
        faqItems.forEach(other => {
          if (other !== item) other.classList.remove('open');
        });
        item.classList.toggle('open', !isOpen);
      });
    }
  });
}

/* ==========================================================================
   7. Copy to Clipboard
   ========================================================================== */
function initCopyButtons() {
  const copyBtns = document.querySelectorAll('.copy-cmd-btn');

  copyBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const textToCopy = btn.getAttribute('data-copy');
      if (textToCopy) {
        navigator.clipboard.writeText(textToCopy).then(() => {
          const original = btn.textContent;
          btn.textContent = '✔';
          btn.style.color = '#10b981';
          setTimeout(() => {
            btn.textContent = original;
            btn.style.color = '';
          }, 2000);
        });
      }
    });
  });
}

/* ==========================================================================
   8. Fetch GitHub Stars with Fallback
   ========================================================================== */
function initGitHubStars() {
  const starsBadge = document.getElementById('ghStarsCount');
  if (!starsBadge) return;

  fetch('https://api.github.com/repos/SamSunny4/ShareDash')
    .then(res => {
      if (!res.ok) throw new Error('API Rate limit or offline');
      return res.json();
    })
    .then(data => {
      if (data && typeof data.stargazers_count === 'number') {
        starsBadge.textContent = `⭐ ${data.stargazers_count}`;
      }
    })
    .catch(() => {
      // Graceful fallback
      starsBadge.textContent = '⭐ MIT';
    });
}

/* ==========================================================================
   9. High-Performance Multipath Canvas Visualizer
   ========================================================================== */
function initMultipathCanvas() {
  const canvas = document.getElementById('multipathCanvas');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const usbValEl = document.getElementById('usbRateVal');
  const wifiValEl = document.getElementById('wifiRateVal');
  const totalValEl = document.getElementById('totalRateVal');
  const fpsEl = document.getElementById('liveFpsMeter');

  let width = canvas.width;
  let height = canvas.height;

  // Particle Streams
  const usbParticles = [];
  const wifiParticles = [];
  const blePulses = [];

  const USB_COLOR = '#00f2fe';
  const WIFI_COLOR = '#10b981';
  const BLE_COLOR = '#60a5fa';

  // Spawn initial particles
  for (let i = 0; i < 28; i++) {
    usbParticles.push({
      progress: Math.random(),
      speed: 0.006 + Math.random() * 0.004,
      size: 2.5 + Math.random() * 2,
      opacity: 0.6 + Math.random() * 0.4
    });
  }

  for (let i = 0; i < 36; i++) {
    wifiParticles.push({
      progress: Math.random(),
      speed: 0.007 + Math.random() * 0.005,
      size: 2.5 + Math.random() * 2,
      opacity: 0.6 + Math.random() * 0.4
    });
  }

  let bleTimer = 0;
  let lastFrameTime = performance.now();
  let frameCount = 0;
  let fpsTimer = 0;

  function render(now) {
    const dt = (now - lastFrameTime) / 1000;
    lastFrameTime = now;

    // FPS calculation
    frameCount++;
    fpsTimer += dt;
    if (fpsTimer >= 1.0) {
      if (fpsEl) fpsEl.textContent = `${frameCount} FPS`;
      frameCount = 0;
      fpsTimer = 0;

      // Small realistic throughput micro-fluctuations
      const jitterUsb = 30.8 + Math.random() * 0.5;
      const jitterWifi = 47.4 + Math.random() * 0.6;
      const jitterTotal = (jitterUsb + jitterWifi).toFixed(1);

      if (usbValEl) usbValEl.textContent = `${jitterUsb.toFixed(1)} MB/s`;
      if (wifiValEl) wifiValEl.textContent = `${jitterWifi.toFixed(1)} MB/s`;
      if (totalValEl) totalValEl.textContent = `${jitterTotal} MB/s`;
    }

    ctx.clearRect(0, 0, width, height);

    // Node Positions
    const pcX = 85;
    const pcY = height / 2;
    const andX = width - 85;
    const andY = height / 2;

    const usbPipeY = height / 2 - 60;
    const wifiPipeY = height / 2 + 60;
    const bleY = height / 2;

    // 1. Draw Hardware Channel Tracks
    ctx.lineWidth = 4;

    // USB Track (Cyan)
    ctx.strokeStyle = 'rgba(6, 182, 212, 0.2)';
    ctx.beginPath();
    ctx.moveTo(pcX + 35, pcY);
    ctx.bezierCurveTo(pcX + 110, usbPipeY, andX - 110, usbPipeY, andX - 35, andY);
    ctx.stroke();

    // Wi-Fi Track (Emerald)
    ctx.strokeStyle = 'rgba(16, 185, 129, 0.2)';
    ctx.beginPath();
    ctx.moveTo(pcX + 35, pcY);
    ctx.bezierCurveTo(pcX + 110, wifiPipeY, andX - 110, wifiPipeY, andX - 35, andY);
    ctx.stroke();

    // BLE Dashed Track (Blue)
    ctx.setLineDash([5, 6]);
    ctx.strokeStyle = 'rgba(59, 130, 246, 0.25)';
    ctx.beginPath();
    ctx.moveTo(pcX + 35, bleY);
    ctx.lineTo(andX - 35, bleY);
    ctx.stroke();
    ctx.setLineDash([]);

    // 2. Animate and Draw USB Particles (Bezier Curve Interpolation)
    usbParticles.forEach(p => {
      p.progress += p.speed;
      if (p.progress > 1) p.progress = 0;

      const t = p.progress;
      // Cubic Bezier interpolation: B(t) = (1-t)^3*P0 + 3(1-t)^2*t*P1 + 3(1-t)*t^2*P2 + t^3*P3
      const p0 = { x: pcX + 35, y: pcY };
      const p1 = { x: pcX + 110, y: usbPipeY };
      const p2 = { x: andX - 110, y: usbPipeY };
      const p3 = { x: andX - 35, y: andY };

      const cx = Math.pow(1 - t, 3) * p0.x + 3 * Math.pow(1 - t, 2) * t * p1.x + 3 * (1 - t) * Math.pow(t, 2) * p2.x + Math.pow(t, 3) * p3.x;
      const cy = Math.pow(1 - t, 3) * p0.y + 3 * Math.pow(1 - t, 2) * t * p1.y + 3 * (1 - t) * Math.pow(t, 2) * p2.y + Math.pow(t, 3) * p3.y;

      ctx.beginPath();
      ctx.arc(cx, cy, p.size, 0, Math.PI * 2);
      ctx.fillStyle = USB_COLOR;
      ctx.shadowColor = USB_COLOR;
      ctx.shadowBlur = 10;
      ctx.fill();
      ctx.shadowBlur = 0;
    });

    // 3. Animate and Draw Wi-Fi Particles
    wifiParticles.forEach(p => {
      p.progress += p.speed;
      if (p.progress > 1) p.progress = 0;

      const t = p.progress;
      const p0 = { x: pcX + 35, y: pcY };
      const p1 = { x: pcX + 110, y: wifiPipeY };
      const p2 = { x: andX - 110, y: wifiPipeY };
      const p3 = { x: andX - 35, y: andY };

      const cx = Math.pow(1 - t, 3) * p0.x + 3 * Math.pow(1 - t, 2) * t * p1.x + 3 * (1 - t) * Math.pow(t, 2) * p2.x + Math.pow(t, 3) * p3.x;
      const cy = Math.pow(1 - t, 3) * p0.y + 3 * Math.pow(1 - t, 2) * t * p1.y + 3 * (1 - t) * Math.pow(t, 2) * p2.y + Math.pow(t, 3) * p3.y;

      ctx.beginPath();
      ctx.arc(cx, cy, p.size, 0, Math.PI * 2);
      ctx.fillStyle = WIFI_COLOR;
      ctx.shadowColor = WIFI_COLOR;
      ctx.shadowBlur = 10;
      ctx.fill();
      ctx.shadowBlur = 0;
    });

    // 4. BLE Radio Pulses
    bleTimer += dt;
    if (bleTimer > 1.2) {
      blePulses.push({ x: pcX + 45, radius: 2, alpha: 0.9 });
      bleTimer = 0;
    }

    for (let i = blePulses.length - 1; i >= 0; i--) {
      const pulse = blePulses[i];
      pulse.x += 160 * dt;
      pulse.radius += 10 * dt;
      pulse.alpha -= 0.65 * dt;

      if (pulse.alpha <= 0 || pulse.x >= andX - 45) {
        blePulses.splice(i, 1);
        continue;
      }

      ctx.beginPath();
      ctx.arc(pulse.x, bleY, pulse.radius, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(96, 165, 250, ${pulse.alpha})`;
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    // 5. Draw Channel Labels at Curve Apex
    ctx.font = '600 11px JetBrains Mono, monospace';
    ctx.textAlign = 'center';

    // USB Label
    ctx.fillStyle = USB_COLOR;
    ctx.fillText('⚡ USB 3.x FAST-PATH', width / 2, usbPipeY - 12);

    // Wi-Fi Label
    ctx.fillStyle = WIFI_COLOR;
    ctx.fillText('📶 PHONE 5GHz SOFTAP', width / 2, wifiPipeY + 22);

    // BLE Label
    ctx.fillStyle = BLE_COLOR;
    ctx.fillText('🔵 BLE GATT CONTROL', width / 2, bleY - 8);

    // 6. Draw Windows PC Node
    drawDeviceNode(ctx, pcX, pcY, '💻 Windows PC', 'Rust Engine', '#0078D6');

    // 7. Draw Android Device Node
    drawDeviceNode(ctx, andX, andY, '📱 Android', 'Compose NIO', '#10B981');

    requestAnimationFrame(render);
  }

  function drawDeviceNode(context, x, y, title, subtitle, accentColor) {
    // Outer glow ring
    context.beginPath();
    context.arc(x, y, 42, 0, Math.PI * 2);
    context.fillStyle = '#0f172a';
    context.strokeStyle = accentColor;
    context.lineWidth = 2.5;
    context.shadowColor = accentColor;
    context.shadowBlur = 15;
    context.fill();
    context.stroke();
    context.shadowBlur = 0;

    // Inner icon ring
    context.beginPath();
    context.arc(x, y, 32, 0, Math.PI * 2);
    context.fillStyle = '#1e293b';
    context.fill();

    // Node Text
    context.font = '700 12px Outfit, sans-serif';
    context.fillStyle = '#f8fafc';
    context.textAlign = 'center';
    context.fillText(title, x, y + 62);

    context.font = '500 10px Plus Jakarta Sans, sans-serif';
    context.fillStyle = '#94a3b8';
    context.fillText(subtitle, x, y + 76);
  }

  requestAnimationFrame(render);
}
