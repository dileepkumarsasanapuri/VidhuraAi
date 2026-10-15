#!/usr/bin/env bash
# ==============================================================================
# Vidhura AI — Automated Production Deployment Script for AWS EC2
# Supported OS: Ubuntu 22.04 LTS / Ubuntu 24.04 LTS (AWS EC2)
# ==============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}       Vidhura AI — AWS EC2 Production Deployment Engine       ${NC}"
echo -e "${CYAN}================================================================${NC}"

# 1. Root check
if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}[ERROR] Please run this script as root or with sudo:${NC}"
  echo "  sudo bash scripts/deploy-aws-ec2.sh"
  exit 1
fi

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

echo -e "\n${YELLOW}[1/6] Updating system packages and installing prerequisites...${NC}"
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release certbot openssl

# Configure 4GB Swap for AWS Free Tier (1GB RAM t2.micro/t3.micro)
SWAP_TOTAL=$(free -m | awk '/^Swap:/ {print $2}')
if [ -z "$SWAP_TOTAL" ] || [ "$SWAP_TOTAL" -lt 2000 ]; then
  echo -e "${CYAN}==> Configuring 4GB Swapfile for AWS Free Tier memory safety...${NC}"
  if [ ! -f /swapfile ]; then
    fallocate -l 4G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=4096
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    if ! grep -q '/swapfile' /etc/fstab; then
      echo '/swapfile none swap sw 0 0' >> /etc/fstab
    fi
    sysctl vm.swappiness=20 > /dev/null || true
    echo -e "${GREEN}==> 4GB Swap enabled! Total addressable memory is now 5GB.${NC}"
  fi
fi

# 2. Install Docker if not present
if ! command -v docker &> /dev/null; then
  echo -e "${YELLOW}[2/6] Installing Docker Engine & Docker Compose Plugin...${NC}"
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg --yes
  chmod a+r /etc/apt/keyrings/docker.gpg

  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null

  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable docker
  systemctl start docker
  echo -e "${GREEN}Docker installed successfully!${NC}"
else
  echo -e "${GREEN}[2/6] Docker is already installed.${NC}"
fi

# 3. Environment Configuration
echo -e "\n${YELLOW}[3/6] Configuring Production Environment (.env)...${NC}"
if [ ! -f .env ]; then
  echo "Creating .env from .env.example..."
  cp .env.example .env
fi

# Generate strong JWT secret if not set
CURRENT_JWT=$(grep "^JWT_SECRET=" .env | cut -d'=' -f2- || true)
if [ -z "$CURRENT_JWT" ] || [ "$CURRENT_JWT" = "your_super_secret_jwt_key_at_least_32_characters_long" ] || [ "$CURRENT_JWT" = "vidhura_secret_key_change_in_production_min_32_bytes" ]; then
  NEW_JWT=$(openssl rand -base64 36 | tr -dc 'a-zA-Z0-9' | head -c 48)
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${NEW_JWT}|" .env
  echo -e "${GREEN}Generated cryptographically secure JWT_SECRET.${NC}"
fi

# Prompt for Domain or IP
read -r -p "Enter your Domain Name or Public IP (e.g. vidhura.ai or 54.210.x.x) [localhost]: " USER_DOMAIN
USER_DOMAIN=${USER_DOMAIN:-localhost}
sed -i "s|^DOMAIN=.*|DOMAIN=${USER_DOMAIN}|" .env || echo "DOMAIN=${USER_DOMAIN}" >> .env

# Prompt for Groq Key
CURRENT_GROQ=$(grep "^GROQ_API_KEY=" .env | cut -d'=' -f2- || true)
if [ -z "$CURRENT_GROQ" ] || [ "$CURRENT_GROQ" = "your_groq_api_key_here" ]; then
  echo -e "${YELLOW}AI Inference requires a Groq API Key (Get a free key from https://console.groq.com/keys)${NC}"
  read -r -p "Enter your GROQ_API_KEY (leave blank to configure later in .env): " USER_GROQ
  if [ -n "$USER_GROQ" ]; then
    sed -i "s|^GROQ_API_KEY=.*|GROQ_API_KEY=${USER_GROQ}|" .env
  fi
fi

# 4. Pull or Build Docker Images
echo -e "\n${YELLOW}[4/6] Pulling pre-built Docker container images from GitHub Container Registry...${NC}"
COMPOSE_FILE="docker-compose.prod.yml"

if ! docker compose -f docker-compose.prod.yml pull; then
  echo -e "\n${YELLOW}----------------------------------------------------------------${NC}"
  echo -e "${YELLOW}Notice: GitHub Container Registry reported 'denied' (Package is Private).${NC}"
  echo -e "You have 3 easy options:"
  echo -e "  [1] Make packages Public at: https://github.com/dileepkumarsasanapuri?tab=packages (Recommended - instant)"
  echo -e "  [2] Login to ghcr.io with a GitHub Personal Access Token (PAT) with read:packages scope"
  echo -e "  [3] Build containers directly on this EC2 instance from local source code"
  echo -e "${YELLOW}----------------------------------------------------------------${NC}"
  
  read -r -p "Choose an option (1, 2, or 3) [1]: " PULL_OPT
  PULL_OPT=${PULL_OPT:-1}

  if [ "$PULL_OPT" = "2" ]; then
    read -r -p "GitHub Username [dileepkumarsasanapuri]: " GH_USER
    GH_USER=${GH_USER:-dileepkumarsasanapuri}
    read -r -s -p "GitHub Personal Access Token (classic PAT with read:packages): " GH_PAT
    echo ""
    echo "$GH_PAT" | docker login ghcr.io -u "$GH_USER" --password-stdin
    docker compose -f docker-compose.prod.yml pull
  elif [ "$PULL_OPT" = "3" ]; then
    echo -e "${CYAN}Building Docker containers locally on EC2...${NC}"
    COMPOSE_FILE="docker-compose.yml"
    docker compose -f docker-compose.yml build
  else
    echo -e "Retrying pull (ensure packages are set to Public in GitHub package settings)..."
    if ! docker compose -f docker-compose.prod.yml pull; then
      echo -e "${YELLOW}Could not pull from ghcr.io. Building containers locally from source instead...${NC}"
      COMPOSE_FILE="docker-compose.yml"
      docker compose -f docker-compose.yml build
    fi
  fi
fi

# 5. Launch Application Stack
echo -e "\n${YELLOW}[5/6] Launching Vidhura AI Production Stack using ${COMPOSE_FILE}...${NC}"
docker compose -f "$COMPOSE_FILE" down --remove-orphans || true
docker compose -f "$COMPOSE_FILE" up -d

# 6. Configure Systemd Service for Auto-start on reboot
echo -e "\n${YELLOW}[6/6] Setting up systemd auto-start service...${NC}"
cat <<EOF > /etc/systemd/system/vidhura.service
[Unit]
Description=Vidhura AI Production Service
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/docker compose -f ${COMPOSE_FILE} up -d
ExecStop=/usr/bin/docker compose -f ${COMPOSE_FILE} down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable vidhura.service

echo -e "\n${GREEN}================================================================${NC}"
echo -e "${GREEN}       🎉 Vidhura AI Successfully Deployed to Production!       ${NC}"
echo -e "${GREEN}================================================================${NC}"
echo -e "Access your application:"
echo -e "  🌐 Frontend:  http://${USER_DOMAIN}"
echo -e "  ⚙️ Backend:   http://${USER_DOMAIN}/api/health"
echo ""
echo -e "${CYAN}Next Steps for SSL (HTTPS):${NC}"
echo "If you pointed a real domain to this AWS IP, run Let's Encrypt Certbot:"
echo "  sudo certbot certonly --webroot -w /var/lib/docker/volumes/vidhuraai_certbot_www/_data -d ${USER_DOMAIN}"
echo ""
echo -e "To view live logs at any time:"
echo "  docker compose -f docker-compose.prod.yml logs -f"
echo "================================================================"
