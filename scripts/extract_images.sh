#!/bin/bash

IMAGE_DIR="/Users/jsheth/Downloads/image-data"

# Exports and converts all photos from the Apple System Photo Library to JPEG format and stores them in the specified directory, $IMAGE_DIR.
osxphotos export $IMAGE_DIR --only-photos --convert-to-jpeg --jpeg-quality 1.0 --fix-orientation --jpeg-ext jpeg