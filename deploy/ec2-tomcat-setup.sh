#!/usr/bin/env bash
# Bootstrap an Amazon Linux / RHEL-like EC2 VM as a stand-in for an on-prem app server.
# Run as root (or with sudo) on a fresh VM after copying the WAR.
set -euo pipefail

TOMCAT_VERSION="${TOMCAT_VERSION:-9.0.98}"
INSTALL_DIR="${INSTALL_DIR:-/opt/tomcat}"
APP_USER="${APP_USER:-tomcat}"
WAR_SRC="${1:-./loan-service.war}"

if [[ ! -f "$WAR_SRC" ]]; then
  echo "Usage: $0 /path/to/loan-service.war"
  exit 1
fi

echo "==> Installing Java 8 and tools"
if command -v yum >/dev/null 2>&1; then
  yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel wget tar
elif command -v apt-get >/dev/null 2>&1; then
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-8-jdk wget tar
fi

id -u "$APP_USER" >/dev/null 2>&1 || useradd -r -m -U -d "$INSTALL_DIR" -s /bin/false "$APP_USER"

echo "==> Installing Tomcat $TOMCAT_VERSION to $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
cd /tmp
wget -q "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz"
tar xzf "apache-tomcat-${TOMCAT_VERSION}.tar.gz"
rm -rf "${INSTALL_DIR:?}/"*
cp -a "apache-tomcat-${TOMCAT_VERSION}/." "$INSTALL_DIR/"
chown -R "$APP_USER:$APP_USER" "$INSTALL_DIR"

echo "==> Deploying WAR"
rm -rf "$INSTALL_DIR/webapps/loan-service" "$INSTALL_DIR/webapps/loan-service.war"
cp "$WAR_SRC" "$INSTALL_DIR/webapps/loan-service.war"
chown "$APP_USER:$APP_USER" "$INSTALL_DIR/webapps/loan-service.war"

cat > /etc/systemd/system/tomcat.service <<EOF
[Unit]
Description=Apache Tomcat - Legacy Loan Service
After=network.target

[Service]
Type=forking
User=$APP_USER
Group=$APP_USER
Environment=JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
Environment=CATALINA_PID=$INSTALL_DIR/temp/tomcat.pid
Environment=CATALINA_HOME=$INSTALL_DIR
Environment=CATALINA_BASE=$INSTALL_DIR
ExecStart=$INSTALL_DIR/bin/startup.sh
ExecStop=$INSTALL_DIR/bin/shutdown.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable tomcat
systemctl restart tomcat

echo "==> Done. Open http://<vm-public-ip>:8080/loan-service/"
echo "    Security group must allow inbound TCP 8080 from your demo client."
