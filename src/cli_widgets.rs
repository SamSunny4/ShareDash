//! Terminal UI widgets for the wifite-style ShareDash wizard.
//!
//! Provides progress bars, spinners, tables, phase headers, and
//! transfer result boxes using Unicode box-drawing characters and
//! ANSI color codes.

use std::io::{self, Write};
use std::time::{Duration, Instant};

// ──────────────── ANSI Color Helpers ────────────────

pub const RESET: &str = "\x1b[0m";
pub const BOLD: &str = "\x1b[1m";
pub const DIM: &str = "\x1b[2m";
pub const RED: &str = "\x1b[31m";
pub const GREEN: &str = "\x1b[32m";
pub const YELLOW: &str = "\x1b[33m";
pub const BLUE: &str = "\x1b[34m";
pub const MAGENTA: &str = "\x1b[35m";
pub const CYAN: &str = "\x1b[36m";
pub const WHITE: &str = "\x1b[37m";
pub const GRAY: &str = "\x1b[90m";
pub const BOLD_WHITE: &str = "\x1b[1;37m";
pub const BOLD_GREEN: &str = "\x1b[1;32m";
pub const BOLD_CYAN: &str = "\x1b[1;36m";
pub const BOLD_YELLOW: &str = "\x1b[1;33m";
pub const BOLD_RED: &str = "\x1b[1;31m";

// ──────────────── Banner ────────────────

pub fn print_wizard_banner() {
    println!("{CYAN}╔════════════════════════════════════════════════════════════════╗{RESET}");
    println!("{CYAN}║{RESET}  {BOLD_WHITE}ShareDash v0.1.0{RESET} — {CYAN}Multipath File Transfer Engine{RESET}            {CYAN}║{RESET}");
    println!("{CYAN}╚════════════════════════════════════════════════════════════════╝{RESET}");
}

// ──────────────── Phase Header ────────────────

pub fn print_phase_header(phase_num: u8, title: &str) {
    println!();
    println!("{BOLD_WHITE}[PHASE {phase_num}] {title}{RESET}");
    let separator_len = title.len() + 10;
    println!(
        "{GRAY}{}{RESET}",
        "─".repeat(separator_len.max(40))
    );
}

// ──────────────── Progress Bar ────────────────

/// Draw a one-shot progress bar, overwriting the current line.
/// `pct` is 0.0..=1.0
pub fn draw_progress_bar(pct: f64, width: usize, elapsed_secs: f64) {
    let filled = (pct * width as f64) as usize;
    let empty = width.saturating_sub(filled);
    let bar: String = "▓".repeat(filled) + &"░".repeat(empty);
    print!(
        "\r  {bar}  {pct:>5.1}%  ({elapsed:.1}s elapsed)",
        bar = bar,
        pct = pct * 100.0,
        elapsed = elapsed_secs,
    );
    let _ = io::stdout().flush();
}

/// Animated BLE scan progress bar that runs for `duration`.
pub async fn animated_scan_progress(duration: Duration) {
    let start = Instant::now();
    let total = duration.as_secs_f64();
    let width = 25;
    loop {
        let elapsed = start.elapsed().as_secs_f64();
        let pct = (elapsed / total).min(1.0);
        draw_progress_bar(pct, width, elapsed);
        if elapsed >= total {
            println!();
            break;
        }
        tokio::time::sleep(Duration::from_millis(80)).await;
    }
}

// ──────────────── Spinner ────────────────

const SPINNER_FRAMES: &[char] = &['⣾', '⣽', '⣻', '⢿', '⡿', '⣟', '⣯', '⣷'];

/// Print a spinner frame inline (no newline). Call in a loop.
pub fn draw_spinner_frame(msg: &str, frame_idx: usize) {
    let ch = SPINNER_FRAMES[frame_idx % SPINNER_FRAMES.len()];
    print!("\r  {msg} {ch}  ");
    let _ = io::stdout().flush();
}

/// Animated spinner that runs until a future completes.
/// Returns the result of the future.
pub async fn with_spinner<F, T>(msg: &str, future: F) -> T
where
    F: std::future::Future<Output = T>,
{
    let msg_owned = msg.to_string();

    let spinner_handle = tokio::spawn(async move {
        let mut i = 0usize;
        loop {
            draw_spinner_frame(&msg_owned, i);
            i += 1;
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
    });

    let result = future.await;
    spinner_handle.abort();
    // Clear spinner line
    print!("\r{}\r", " ".repeat(80));
    let _ = io::stdout().flush();
    result
}

// ──────────────── Table ────────────────

/// Print a box-drawing table. Headers and rows are vectors of strings.
/// Each column auto-sizes to the widest entry.
pub fn print_table(headers: &[&str], rows: &[Vec<String>]) {
    if headers.is_empty() {
        return;
    }
    let cols = headers.len();

    // Calculate column widths
    let mut widths: Vec<usize> = headers.iter().map(|h| h.len()).collect();
    for row in rows {
        for (i, cell) in row.iter().enumerate() {
            if i < cols {
                // Strip ANSI escape codes for width calculation
                let visible_len = strip_ansi(cell).len();
                widths[i] = widths[i].max(visible_len);
            }
        }
    }

    // Minimum padding
    for w in widths.iter_mut() {
        *w = (*w).max(3);
    }

    // Top border
    print!("  ┌");
    for (i, w) in widths.iter().enumerate() {
        print!("{}", "─".repeat(w + 2));
        if i < cols - 1 {
            print!("┬");
        }
    }
    println!("┐");

    // Header row
    print!("  │");
    for (i, h) in headers.iter().enumerate() {
        print!(" {BOLD}{:<width$}{RESET} │", h, width = widths[i]);
    }
    println!();

    // Header separator
    print!("  ├");
    for (i, w) in widths.iter().enumerate() {
        print!("{}", "─".repeat(w + 2));
        if i < cols - 1 {
            print!("┼");
        }
    }
    println!("┤");

    // Data rows
    for row in rows {
        print!("  │");
        for i in 0..cols {
            let cell = row.get(i).map(|s| s.as_str()).unwrap_or("");
            let visible_len = strip_ansi(cell).len();
            let padding = widths[i].saturating_sub(visible_len);
            print!(" {}{} │", cell, " ".repeat(padding));
        }
        println!();
    }

    // Bottom border
    print!("  └");
    for (i, w) in widths.iter().enumerate() {
        print!("{}", "─".repeat(w + 2));
        if i < cols - 1 {
            print!("┴");
        }
    }
    println!("┘");
}

fn strip_ansi(s: &str) -> String {
    let mut out = String::new();
    let mut in_escape = false;
    for ch in s.chars() {
        if ch == '\x1b' {
            in_escape = true;
        } else if in_escape {
            if ch.is_ascii_alphabetic() {
                in_escape = false;
            }
        } else {
            out.push(ch);
        }
    }
    out
}

// ──────────────── Info Tree ────────────────

/// Print a tree-style info line: ├─ key : value
pub fn print_tree_item(key: &str, value: &str, is_last: bool) {
    let connector = if is_last { "└─" } else { "├─" };
    println!("  {connector} {key:<18}: {BOLD}{value}{RESET}");
}

// ──────────────── Status Tick / Check ────────────────

pub fn print_ok(msg: &str) {
    println!("  {GREEN}✔{RESET} {msg}");
}

pub fn print_fail(msg: &str) {
    println!("  {RED}✖{RESET} {msg}");
}

pub fn print_warn(msg: &str) {
    println!("  {YELLOW}⚠{RESET} {msg}");
}

pub fn print_step(msg: &str) {
    println!("  {msg}");
}

pub fn print_step_result(step: &str, ok: bool) {
    let icon = if ok {
        format!("{GREEN}✔{RESET}")
    } else {
        format!("{RED}✖{RESET}")
    };
    println!("  {step}  ... {icon}");
}

// ──────────────── Channel Summary ────────────────

pub fn print_channel_summary(
    wifi_addr: Option<&str>,
    wifi_speed: Option<&str>,
    usb_addr: Option<&str>,
    usb_speed: Option<&str>,
) {
    let line = "═".repeat(60);
    println!();
    println!("{line}");
    println!("  {BOLD_GREEN}✅ READY TO SEND{RESET} — Both channels active");
    if let (Some(addr), Some(spd)) = (wifi_addr, wifi_speed) {
        println!("  ├─ 📶 Wi-Fi : {addr} ({spd})");
    }
    if let (Some(addr), Some(spd)) = (usb_addr, usb_speed) {
        println!("  ├─ ⚡ USB   : {addr} ({spd})");
    }
    if wifi_addr.is_some() && usb_addr.is_some() {
        println!("  └─ Combined : Multipath aggregation active");
    } else if wifi_addr.is_some() {
        println!("  └─ Single channel: Wi-Fi only");
    } else if usb_addr.is_some() {
        println!("  └─ Single channel: USB only");
    }
    println!("{line}");
}

// ──────────────── Transfer Result Box ────────────────

pub struct TransferResult {
    pub file_name: String,
    pub size_mb: f64,
    pub time_secs: f64,
    pub avg_speed_mb_s: f64,
    pub avg_speed_gbps: f64,
    pub usb_speed_mb_s: Option<f64>,
    pub usb_pct: Option<f64>,
    pub wifi_speed_mb_s: Option<f64>,
    pub wifi_pct: Option<f64>,
    pub chunk_size_bytes: usize,
    pub total_chunks: usize,
    pub integrity_ok: bool,
}

pub fn print_transfer_result(r: &TransferResult) {
    println!();
    println!("  {GREEN}✔ Transfer COMPLETED{RESET}");
    println!("  ┌────────────────────────────────────────────────┐");
    println!("  │ File        : {:<33} │", truncate_str(&r.file_name, 33));
    println!("  │ Size        : {:<33} │", format!("{:.2} MB", r.size_mb));
    println!("  │ Time        : {:<33} │", format!("{:.2}s", r.time_secs));
    println!(
        "  │ Avg Speed   : {:<33} │",
        format!("{:.1} MB/s ({:.2} Gbps)", r.avg_speed_mb_s, r.avg_speed_gbps)
    );
    if let (Some(usb_spd), Some(usb_p)) = (r.usb_speed_mb_s, r.usb_pct) {
        println!(
            "  │ USB Speed   : {:<33} │",
            format!("{:.1} MB/s · {:.1}%", usb_spd, usb_p)
        );
    }
    if let (Some(wifi_spd), Some(wifi_p)) = (r.wifi_speed_mb_s, r.wifi_pct) {
        println!(
            "  │ Wi-Fi Speed : {:<33} │",
            format!("{:.1} MB/s · {:.1}%", wifi_spd, wifi_p)
        );
    }
    let chunk_mb = r.chunk_size_bytes as f64 / (1024.0 * 1024.0);
    println!(
        "  │ Chunks      : {:<33} │",
        format!("{:.0} MB × {} chunks", chunk_mb, r.total_chunks)
    );
    let integrity = if r.integrity_ok {
        format!("SHA-256 {GREEN}✔ VERIFIED{RESET}")
    } else {
        format!("SHA-256 {RED}✖ FAILED{RESET}")
    };
    println!("  │ Integrity   : {:<33} │", integrity);
    println!("  └────────────────────────────────────────────────┘");
}

// ──────────────── Transfer Progress ────────────────

#[derive(Clone, Default)]
pub struct ChannelProgress {
    pub name: String,
    pub icon: String,
    pub bytes_sent: u64,
    pub speed_mb_s: f64,
    pub speed_gbps: f64,
    pub chunks_sent: usize,
}

pub fn init_transfer_progress(channel_count: usize) {
    for _ in 0..(1 + channel_count) {
        println!();
    }
}

pub fn draw_transfer_progress(
    pct: f64,
    eta_secs: f64,
    channels: &[ChannelProgress],
) {
    let width: usize = 25;
    let filled = ((pct * width as f64) as usize).min(width);
    let empty = width.saturating_sub(filled);
    let bar: String = "▓".repeat(filled) + &"░".repeat(empty);

    // Move cursor up to overwrite previous progress (1 line for bar + 1 per channel)
    let lines_to_clear = 1 + channels.len();
    for _ in 0..lines_to_clear {
        print!("\x1b[A\x1b[2K"); // move up + clear line
    }

    println!(
        "  {bar}  {pct:>5.1}%  ETA: {eta:.1}s",
        bar = bar,
        pct = pct * 100.0,
        eta = eta_secs,
    );
    for ch in channels {
        let mb = ch.bytes_sent as f64 / (1024.0 * 1024.0);
        println!(
            "  {icon} {name}: {mb:.1} MB sent · {spd:.1} MB/s ({gbps:.2} Gbps) · {chunks} chunks",
            icon = ch.icon,
            name = ch.name,
            mb = mb,
            spd = ch.speed_mb_s,
            gbps = ch.speed_gbps,
            chunks = ch.chunks_sent,
        );
    }
    let _ = io::stdout().flush();
}

// ──────────────── Prompt ────────────────

/// Read a line of input with a prompt prefix.
pub async fn prompt(msg: &str) -> String {
    print!("\n  {msg}");
    let _ = io::stdout().flush();
    let stdin = tokio::io::stdin();
    let mut reader = tokio::io::BufReader::new(stdin);
    let mut buf = String::new();
    let _ = tokio::io::AsyncBufReadExt::read_line(&mut reader, &mut buf).await;
    buf.trim().to_string()
}

/// Read a numeric choice in range [min, max].
pub async fn prompt_choice(msg: &str, min: usize, max: usize) -> usize {
    loop {
        let input = prompt(msg).await;
        if let Ok(n) = input.parse::<usize>() {
            if n >= min && n <= max {
                return n;
            }
        }
        println!("  {RED}Invalid choice. Enter a number between {min} and {max}.{RESET}");
    }
}

fn truncate_str(s: &str, max: usize) -> String {
    if s.len() > max {
        format!("{}...", &s[..max.saturating_sub(3)])
    } else {
        s.to_string()
    }
}
