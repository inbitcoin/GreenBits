#!/bin/make -f

JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk-amd64"
ANDROID_RES="app/altana-android-res"

ANDROID_NDK_VERSION=r13b
ANDROID_NDK_FILENAME=android-ndk-$(ANDROID_NDK_VERSION)-linux-x86_64.zip
ANDROID_NDK_URL="http://dl.google.com/android/repository/$(ANDROID_NDK_FILENAME)"
ANDROID_NDK=$(PWD)/android-ndk-$(ANDROID_NDK_VERSION)

default: build-libwally check-resources

download-ndk:
	wget -c $(ANDROID_NDK_URL)
	unzip -q $(ANDROID_NDK_FILENAME)

rm-ndk:
	@if [ ! -d $(ANDROID_NDK) ]; then \
		echo "Android NDK not found. Expected path: $(ANDROID_NDK)"; \
		exit 1; \
	fi
	rm -i $(PWD)/$(ANDROID_NDK_FILENAME)
	rm -rI $(ANDROID_NDK)

check-resources:
	@if [ ! -f $(ANDROID_RES)/.git ]; then \
		echo "No git submodule $(ANDROID_RES) found"; \
		exit 3; \
	fi
	@echo "git submodule $(ANDROID_RES) found"

build-libwally: download-ndk
	@if [ ! -d $(ANDROID_NDK) ]; then \
		echo "Android NDK not found. Expected path: $(ANDROID_NDK)"; \
		exit 1; \
	fi
	@if [ ! -d $(JAVA_HOME) ]; then \
		echo "Java not found. Expected path: $(JAVA_HOME)"; \
		exit 2; \
	fi
	export JAVA_HOME=$(JAVA_HOME); export ANDROID_NDK=$(ANDROID_NDK); (cd app; ./prepare_libwally_clang.sh)

clean: rm-ndk
