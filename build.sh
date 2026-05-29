#!/bin/bash
# Build script for CPD Chat System — produces a runnable JAR

set -e

cd "$(dirname "$0")" || exit 1

VERSION="1.0"
BUILD_DIR="build"
OUT_DIR="${BUILD_DIR}/classes"
JAR_NAME="ChatSystem-${VERSION}.jar"

echo "=== CPD Chat System Build ==="
echo "Java version: $(java -version 2>&1 | head -1)"

# Clean
rm -rf "$BUILD_DIR"
mkdir -p "$OUT_DIR"

# Compile
echo "Compiling..."
find src/main/java -name "*.java" | sort > sources.txt
javac --release 21 -d "$OUT_DIR" @sources.txt

# Create manifest
echo "Creating manifest..."
cat > "${BUILD_DIR}/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Implementation-Title: CPD Chat System
Implementation-Version: ${VERSION}
Main-Class: chat.server.ChatServer
EOF

# Package JAR
echo "Packaging JAR..."
cd "$OUT_DIR"
jar cfm "../../${JAR_NAME}" "../../${BUILD_DIR}/MANIFEST.MF" .
cd ../..

echo ""
echo "Build successful: ${JAR_NAME}"
echo ""
echo "Usage:"
echo "  Server:  java -jar ${JAR_NAME} [port] [users-file] [--tls]"
echo "  Client:  java -cp ${JAR_NAME} chat.client.ChatClient [host] [port] [--tls]"
echo ""
echo "Examples:"
echo "  # Start server on default port 12345"
echo "  java -jar ${JAR_NAME}"
echo ""
echo "  # Start server on port 9000 with TLS (requires data/chat-server.p12)"
echo "  java -jar ${JAR_NAME} 9000 data/users.db --tls"
echo ""
echo "  # Generate self-signed keystore for TLS:"
echo "  keytool -genkey -alias chat-server -keystore data/chat-server.p12 -storetype PKCS12 -storepass 12345678 -validity 365 -keyalg RSA -keysize 2048"
echo ""
