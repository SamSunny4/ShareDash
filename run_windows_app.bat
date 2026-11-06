@echo off
title ShareDash Windows GUI (GPUI)
cd /d "%~dp0"
echo Starting ShareDash Native Windows GPUI GUI...
cargo run --bin sharedash-gui --features gui
pause

