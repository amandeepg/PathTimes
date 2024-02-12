#!/usr/bin/env bash

rm -rf app/src/test/snapshots/images ; ./gradlew recordPaparazziDebug && python3 organize_paparazzi_images.py
