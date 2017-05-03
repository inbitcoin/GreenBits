#!/bin/make -f

ANDROID_NDK="$(HOME)/Android/Sdk/ndk-bundle"
JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk-amd64"
ANDROID_RES="app/altana-android-res"

default: build-libwally check-resources

check-resources:
	@if [ ! -f $(ANDROID_RES)/.git ]; then \
		echo "No git submodule $(ANDROID_RES) found"; \
		exit 3; \
	fi
	@echo "git submodule $(ANDROID_RES) found"

build-libwally:
	@if [ ! -d $(ANDROID_NDK) ]; then \
		echo "Android NDK not found. Expected path: $(ANDROID_NDK)"; \
		exit 1; \
	fi
	@if [ ! -d $(JAVA_HOME) ]; then \
		echo "Java not found. Expected path: $(JAVA_HOME)"; \
		exit 2; \
	fi
	export JAVA_HOME=$(JAVA_HOME); export ANDROID_NDK=$(ANDROID_NDK); (cd app; ./prepare_libwally_clang.sh)
