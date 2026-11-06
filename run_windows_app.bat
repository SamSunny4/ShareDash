@echo off
title ShareDash Desktop
cd /d "%~dp0"
echo Starting ShareDash Native Windows GUI...
cargo run --release --bin sharedash
pause

