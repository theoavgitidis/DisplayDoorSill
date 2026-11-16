#!/bin/bash

# nice print functions
debug() { if [[ -n "${BUILD_DEBUG}" ]]; then printf "\033[1;34m%s" "[DEBUG] " && printf "\033[0m%s\n" "$*"; fi; }
info() { printf "\033[1;32m%s" "[INFO] " && printf "\033[0m%s\n" "$*"; }
warn() { printf "\033[1;33m%s" "[WARN] " && printf "\033[0m%s\n" "$*"; }
error() { printf "\033[1;31m%s" "[ERROR] " && printf "\033[0m%s\n" "$*"; }

# print info about prerequisites
info "Prerequisites for this script to work:"
info "  - Installed Android Studio with SDK"
info "  - JDK (>=17) must be installed and \"java\" command  must be in PATH or JAVA_HOME needs to be set "
info "  - Internet access for gradle dependencies must be given"

# check here the os version
export UNAME_OS=`uname -a`
info "reported os by uname ${UNAME_OS}"

case "${UNAME_OS}" in
    *WSL*)      OS_TYPE="WSL";;
    *Linux*) 	OS_TYPE="LINUX";;
	*Darwin*)	OS_TYPE="MACOS";;
	*Msys*)		OS_TYPE="WINDOWS";;
	*)			OS_TYPE="UNKNOWN:UNAME_OS";;
esac

info "assumed OS_TYPE ${OS_TYPE}"

export USER_HOME=$(eval echo "~")

if [[ "$OS_TYPE" == "MACOS" ]]; then
    export USER_HOME=$(eval echo "~")
    export ANDROID_SDK_ROOT=${USER_HOME}/Library/Android/sdk
elif [[ "$OS_TYPE" == "WINDOWS" ]]; then
    export ANDROID_SDK_ROOT=${USER_HOME}/AppData/Local/Android/Sdk
elif [[ "$OS_TYPE" == "LINUX" ]]; then
    export ANDROID_SDK_ROOT=${USER_HOME}/Android/Sdk
else
    error "Unsupported OS_TYPE \"${OS_TYPE}\" for uname OS \"${UNAME_OS}\""
    exit 1
fi

export ANDROID_HOME=${ANDROID_SDK_ROOT}

info "USER_HOME${USER_HOME}"
info "ANDROID_HOME ${ANDROID_HOME}"
info "ANDROID_SDK_ROOT ${ANDROID_SDK_ROOT}"

info "# Please download and install android tools"

info "# Building app apk"
chmod +x ./gradlew
./gradlew assembleDebug
