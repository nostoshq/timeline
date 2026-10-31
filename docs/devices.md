# Devices

Timeline runs on Android 10 or newer with a 64-bit ARM processor. It is controller-first: every page works with a
d-pad and four face buttons, and touch works too.

## Tested

| Device | Android | Screen | What was tried |
|---|---|---|---|
| Retroid Pocket Nova | 13 | 1280 x 960, 120 Hz, 4:3 | Everything: every console, setup, the Retroid extras, docked to a television |
| 16:9 layout (Android emulator, 1920 x 1080) | 13 | 16:9 | Every page of the interface |

## What works where

| | Any Android handheld | Retroid devices | Retroid Pocket Nova |
|---|---|---|---|
| The timeline, shelves, search, game pages | yes | yes | yes |
| Setup, emulator installs, RetroArch and Dolphin set up | yes | yes | yes |
| CRT looks, signals, lag reduction | yes | yes | yes |
| Beam simulator | 120 Hz screens | 120 Hz screens | yes |
| Saves copied to the SD card | yes | yes | yes |
| PS2 memory cards and Xbox hard disk copied too | | yes | yes |
| PS2 autosave and 480-line look set up for you (switch on) | | yes | yes |
| Fan and power by console, heat guard (switch on) | | yes | yes |
| Stick lights, battery held at 80% on the charger (switch on) | | yes | yes |
| Measured color calibration (offered on the firmware it was measured on) | | | yes |
| Measured speaker correction | | | yes |

The Retroid extras use the maker's own settings service, which Retroid devices ship with. Timeline looks for it and
uses it only when it is there; it never asks you to root anything. Each extra marked "switch on" stays off until you
choose it in **Settings, Device**, and was measured on a Retroid Pocket Nova. On other Retroid models, try one at a
time. Switching power and fan off puts back the modes that were set before.
