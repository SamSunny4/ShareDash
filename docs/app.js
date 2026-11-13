/**
 * ShareDash — Application Logic
 * Vanilla JavaScript, zero dependencies
 */

document.addEventListener('DOMContentLoaded', () => {
  initNavbar();
  initSpeedCalculator();
  initArchitectureTabs();
  initTerminalTabs();
  initFaqAccordion();
  initCopyButtons();
  initGitHubStars();
  initMultipathCanvas();
  initScrollReveal();
});

/* ==========================================================================
   1. Navbar — Scroll State & Mobile Menu
   ========================================================================== */
function initNavbar() {
  const nav = document.querySelector('.nav');
  const mobileMenuBtn = document.getElementById('mobileMenuBtn');
  const navLinks = document.getElementById('navLinks');
  const navLinkItems = document.querySelectorAll('.nav__links a');

  // Scroll spy
  let ticking = false;
  window.addEventListener('scroll', () => {
    if (!ticking) {
      requestAnimationFrame(() => {
        const scrollY = window.scrollY;

        // Compact nav on scroll
        if (scrollY > 40) {
          nav?.classList.add('scrolled');
        } else {
          nav?.classList.remove('scrolled');
        }

        // Active section highlight
        const sections = document.querySelectorAll('section[id]');
        const scrollPos = scrollY + 100;
        let currentSection = '';

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

        ticking = false;
      });
      ticking = true;
    }
  });

  // Mobile menu toggle
  if (mobileMenuBtn && navLinks) {
    mobileMenuBtn.addEventListener('click', () => {
      navLinks.classList.toggle('open');
      const isOpen = navLinks.classList.contains('open');
      mobileMenuBtn.setAttribute('aria-expanded', isOpen);
    });

    navLinkItems.forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('open');
        mobileMenuBtn.setAttribute('aria-expanded', 'false');
      });
    });
  }
}

/* ==========================================================================
   2. Speed Calculator
   ========================================================================== */
function initSpeedCalculator() {
  const slider = document.getElementById('fileSizeSlider');
  const sizeDisplay = document.getElementById('calcSizeDisplay');
  const timeSavedVal = document.getElementById('timeSavedVal');

  const etaEls = {
    multipath: document.getElementById('etaShareDashMultipath'),
    quickShare: document.getElementById('etaQuickShare'),
    airDrop: document.getElementById('etaAirDrop'),
    localSend: document.getElementById('etaLocalSend'),
    bluetooth: document.getElementById('etaBluetooth'),
  };

  const barEls = {
    multipath: document.getElementById('barShareDashMultipath'),
    quickShare: document.getElementById('barQuickShare'),
    airDrop: document.getElementById('barAirDrop'),
    localSend: document.getElementById('barLocalSend'),
    bluetooth: document.getElementById('barBluetooth'),
  };

  const SPEEDS = {
    multipath: 100.0,
    quickShare: 42.0,
    airDrop: 38.0,
    localSend: 32.0,
    bluetooth: 2.0,
  };

  function formatTime(seconds) {
    if (seconds < 60) return `${seconds.toFixed(1)}s`;
    const mins = Math.floor(seconds / 60);
    const secs = (seconds % 60).toFixed(0);
    return `${mins}m ${secs}s`;
  }

  function update() {
    if (!slider) return;
    const sizeGB = parseFloat(slider.value);
    const sizeMB = sizeGB * 1024;

    // Display
    if (sizeDisplay) {
      if (sizeGB < 1) {
        sizeDisplay.textContent = `${(sizeGB * 1000).toFixed(0)} MB`;
      } else {
        sizeDisplay.textContent = `${sizeGB.toFixed(1)} GB`;
      }
    }

    // Durations
    const times = {};
    for (const [key, speed] of Object.entries(SPEEDS)) {
      times[key] = sizeMB / speed;
    }

    // ETAs
    for (const [key, el] of Object.entries(etaEls)) {
      if (el) el.textContent = formatTime(times[key]);
    }

    // Time saved vs Quick Share
    if (timeSavedVal) {
      timeSavedVal.textContent = formatTime(times.quickShare - times.multipath);
    }

    // Bars: Lower is better (Transfer Duration).
    // Normalize against LocalSend at 75% width so Bluetooth (taking 23+ minutes) maxes at 100%.
    // Shorter bar = less waiting time = faster transfer!
    const benchmarkNorm = times.localSend / 0.75;
    if (barEls.multipath) barEls.multipath.style.width = `${Math.max(6, Math.min(100, (times.multipath / benchmarkNorm) * 100))}%`;
    if (barEls.quickShare) barEls.quickShare.style.width = `${Math.min(100, (times.quickShare / benchmarkNorm) * 100)}%`;
    if (barEls.airDrop) barEls.airDrop.style.width = `${Math.min(100, (times.airDrop / benchmarkNorm) * 100)}%`;
    if (barEls.localSend) barEls.localSend.style.width = `${Math.min(100, (times.localSend / benchmarkNorm) * 100)}%`;
    if (barEls.bluetooth) barEls.bluetooth.style.width = '100%';
  }

  if (slider) {
    slider.addEventListener('input', update);
    update();
  }
}

/* ==========================================================================
   3. Architecture Tabs
   ========================================================================== */
function initArchitectureTabs() {
  const tabs = document.querySelectorAll('.arch__tab');
  const contents = document.querySelectorAll('.arch__content');

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetId = `tab-${tab.getAttribute('data-tab')}`;
      tabs.forEach(t => t.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));
      tab.classList.add('active');
      const target = document.getElementById(targetId);
      if (target) target.classList.add('active');
    });
  });
}

/* ==========================================================================
   4. Terminal CLI Tabs
   ========================================================================== */
function initTerminalTabs() {
  const tabs = document.querySelectorAll('.terminal__tab');
  const views = document.querySelectorAll('.cli-view');

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetId = `cli-${tab.getAttribute('data-cli')}`;
      tabs.forEach(t => t.classList.remove('active'));
      views.forEach(v => v.classList.remove('active'));
      tab.classList.add('active');
      const target = document.getElementById(targetId);
      if (target) target.classList.add('active');
    });
  });
}

/* ==========================================================================
   5. FAQ Accordion
   ========================================================================== */
function initFaqAccordion() {
  const items = document.querySelectorAll('.faq__item');

  items.forEach(item => {
    const btn = item.querySelector('.faq__question');
    if (btn) {
      btn.addEventListener('click', () => {
        const isOpen = item.classList.contains('open');
        items.forEach(other => {
          if (other !== item) {
            other.classList.remove('open');
            other.querySelector('.faq__question')?.setAttribute('aria-expanded', 'false');
          }
        });
        item.classList.toggle('open', !isOpen);
        btn.setAttribute('aria-expanded', !isOpen);
      });
    }
  });
}

/* ==========================================================================
   6. Copy to Clipboard
   ========================================================================== */
function initCopyButtons() {
  document.querySelectorAll('.code-copy__btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const text = btn.getAttribute('data-copy');
      if (!text) return;

      const setSuccess = () => {
        const svg = btn.querySelector('svg');
        if (svg) {
          const original = svg.innerHTML;
          svg.innerHTML = '<polyline points="20 6 9 17 4 12" fill="none" stroke="#35C995" stroke-width="2"></polyline>';
          setTimeout(() => { svg.innerHTML = original; }, 2000);
        }
      };

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(setSuccess).catch(() => {
          fallbackCopy(text, setSuccess);
        });
      } else {
        fallbackCopy(text, setSuccess);
      }
    });
  });
}

function fallbackCopy(text, onSuccess) {
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  try {
    const successful = document.execCommand('copy');
    if (successful && onSuccess) onSuccess();
  } catch (err) {
    console.error('Fallback copy failed: ', err);
  }
  document.body.removeChild(textarea);
}

/* ==========================================================================
   7. GitHub Stars
   ========================================================================== */
function initGitHubStars() {
  const badge = document.getElementById('ghStarsCount');
  if (!badge) return;

  fetch('https://api.github.com/repos/SamSunny4/ShareDash')
    .then(res => {
      if (!res.ok) throw new Error('API limit');
      return res.json();
    })
    .then(data => {
      if (data && typeof data.stargazers_count === 'number') {
        badge.textContent = `${data.stargazers_count}`;
      }
    })
    .catch(() => {
      badge.textContent = 'MIT';
    });
}

/* ==========================================================================
   8. Scroll Reveal
   ========================================================================== */
function initScrollReveal() {
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (prefersReducedMotion) return;

  const elements = document.querySelectorAll('.reveal');
  if (!elements.length) return;

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, {
    threshold: 0.1,
    rootMargin: '0px 0px -40px 0px'
  });

  elements.forEach(el => observer.observe(el));
}

/* ==========================================================================
   9. Multipath Canvas Visualizer
   ========================================================================== */
function initMultipathCanvas() {
  const canvas = document.getElementById('multipathCanvas');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const usbValEl = document.getElementById('usbRateVal');
  const wifiValEl = document.getElementById('wifiRateVal');
  const totalValEl = document.getElementById('totalRateVal');

  let width = canvas.width;
  let height = canvas.height;

  const BLUE = '#4399F5';
  const GREEN = '#35C995';
  const SKY = '#60a5fa';

  // Particles
  const usbParticles = [];
  const wifiParticles = [];

  for (let i = 0; i < 22; i++) {
    usbParticles.push({
      progress: Math.random(),
      speed: 0.005 + Math.random() * 0.003,
      size: 2 + Math.random() * 1.5,
      opacity: 0.5 + Math.random() * 0.5
    });
  }

  for (let i = 0; i < 28; i++) {
    wifiParticles.push({
      progress: Math.random(),
      speed: 0.006 + Math.random() * 0.004,
      size: 2 + Math.random() * 1.5,
      opacity: 0.5 + Math.random() * 0.5
    });
  }

  let lastTime = performance.now();
  let jitterTimer = 0;

  // Check reduced motion
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (prefersReducedMotion) {
    // Draw a static frame
    drawStaticFrame();
    return;
  }

  function drawStaticFrame() {
    ctx.clearRect(0, 0, width, height);
    const pcX = 70, pcY = height / 2;
    const andX = width - 70, andY = height / 2;
    const usbY = height / 2 - 50;
    const wifiY = height / 2 + 50;

    // Tracks
    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(67, 153, 245, 0.3)';
    ctx.beginPath();
    ctx.moveTo(pcX + 30, pcY);
    ctx.bezierCurveTo(pcX + 100, usbY, andX - 100, usbY, andX - 30, andY);
    ctx.stroke();

    ctx.strokeStyle = 'rgba(53, 201, 149, 0.3)';
    ctx.beginPath();
    ctx.moveTo(pcX + 30, pcY);
    ctx.bezierCurveTo(pcX + 100, wifiY, andX - 100, wifiY, andX - 30, andY);
    ctx.stroke();

    // Labels
    ctx.font = '500 10px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillStyle = BLUE;
    ctx.fillText('USB', width / 2, usbY - 8);
    ctx.fillStyle = GREEN;
    ctx.fillText('Phone 5GHz Wi-Fi', width / 2, wifiY + 18);

    // Nodes
    drawNode(ctx, pcX, pcY, 'Windows PC', BLUE);
    drawNode(ctx, andX, andY, 'Android', GREEN);
  }

  function render(now) {
    const dt = (now - lastTime) / 1000;
    lastTime = now;

    // Throughput jitter every ~1s (100+ MB/s aggregate)
    jitterTimer += dt;
    if (jitterTimer >= 1.0) {
      jitterTimer = 0;
      const usbRate = 53.8 + Math.random() * 0.8;
      const wifiRate = 48.2 + Math.random() * 0.7;
      const total = (usbRate + wifiRate).toFixed(1);
      if (usbValEl) usbValEl.textContent = `${usbRate.toFixed(1)} MB/s`;
      if (wifiValEl) wifiValEl.textContent = `${wifiRate.toFixed(1)} MB/s`;
      if (totalValEl) totalValEl.textContent = `${total} MB/s`;
    }

    ctx.clearRect(0, 0, width, height);

    const pcX = 70, pcY = height / 2;
    const andX = width - 70, andY = height / 2;
    const usbY = height / 2 - 50;
    const wifiY = height / 2 + 50;

    // Draw tracks
    ctx.lineWidth = 2;

    ctx.strokeStyle = 'rgba(67, 153, 245, 0.15)';
    ctx.beginPath();
    ctx.moveTo(pcX + 30, pcY);
    ctx.bezierCurveTo(pcX + 100, usbY, andX - 100, usbY, andX - 30, andY);
    ctx.stroke();

    ctx.strokeStyle = 'rgba(53, 201, 149, 0.15)';
    ctx.beginPath();
    ctx.moveTo(pcX + 30, pcY);
    ctx.bezierCurveTo(pcX + 100, wifiY, andX - 100, wifiY, andX - 30, andY);
    ctx.stroke();

    // USB particles
    usbParticles.forEach(p => {
      p.progress += p.speed;
      if (p.progress > 1) p.progress = 0;
      const t = p.progress;
      const p0 = { x: pcX + 30, y: pcY };
      const p1 = { x: pcX + 100, y: usbY };
      const p2 = { x: andX - 100, y: usbY };
      const p3 = { x: andX - 30, y: andY };
      const cx = bezier(t, p0.x, p1.x, p2.x, p3.x);
      const cy = bezier(t, p0.y, p1.y, p2.y, p3.y);

      ctx.beginPath();
      ctx.arc(cx, cy, p.size, 0, Math.PI * 2);
      ctx.fillStyle = BLUE;
      ctx.globalAlpha = p.opacity;
      ctx.fill();
      ctx.globalAlpha = 1;
    });

    // Wi-Fi particles
    wifiParticles.forEach(p => {
      p.progress += p.speed;
      if (p.progress > 1) p.progress = 0;
      const t = p.progress;
      const p0 = { x: pcX + 30, y: pcY };
      const p1 = { x: pcX + 100, y: wifiY };
      const p2 = { x: andX - 100, y: wifiY };
      const p3 = { x: andX - 30, y: andY };
      const cx = bezier(t, p0.x, p1.x, p2.x, p3.x);
      const cy = bezier(t, p0.y, p1.y, p2.y, p3.y);

      ctx.beginPath();
      ctx.arc(cx, cy, p.size, 0, Math.PI * 2);
      ctx.fillStyle = GREEN;
      ctx.globalAlpha = p.opacity;
      ctx.fill();
      ctx.globalAlpha = 1;
    });

    // Labels
    ctx.font = '500 10px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillStyle = BLUE;
    ctx.fillText('USB', width / 2, usbY - 8);
    ctx.fillStyle = GREEN;
    ctx.fillText('Phone 5GHz Wi-Fi', width / 2, wifiY + 18);

    // Device nodes
    drawNode(ctx, pcX, pcY, 'Windows PC', BLUE);
    drawNode(ctx, andX, andY, 'Android', GREEN);

    requestAnimationFrame(render);
  }

  function bezier(t, p0, p1, p2, p3) {
    const mt = 1 - t;
    return mt * mt * mt * p0 + 3 * mt * mt * t * p1 + 3 * mt * t * t * p2 + t * t * t * p3;
  }

  function drawNode(context, x, y, label, color) {
    // Outer circle
    context.beginPath();
    context.arc(x, y, 32, 0, Math.PI * 2);
    context.fillStyle = '#0C1018';
    context.strokeStyle = color;
    context.lineWidth = 1.5;
    context.fill();
    context.stroke();

    // Inner circle
    context.beginPath();
    context.arc(x, y, 24, 0, Math.PI * 2);
    context.fillStyle = '#141D28';
    context.fill();

    // Label below
    context.font = '600 11px Inter, sans-serif';
    context.fillStyle = '#F1F5F9';
    context.textAlign = 'center';
    context.fillText(label, x, y + 50);
  }

  requestAnimationFrame(render);
}
