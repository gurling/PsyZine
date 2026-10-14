#!/usr/bin/env bash
# GitHub-hosted Linux runner setup. PsyZine, GPL-3.0-or-later.
set -euo pipefail
mkdir -p build-logs "${ANDROID_AVD_HOME:?}" "${ANDROID_USER_HOME:?}"
sudo modprobe kvm
echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=kvm
sudo udevadm settle
sudo chmod a+rw /dev/kvm
test -r /dev/kvm && test -w /dev/kvm
"${ANDROID_HOME:?}/emulator/emulator" -accel-check
