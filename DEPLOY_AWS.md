# 🚀 Vidhura AI — Production Deployment Guide for AWS

This guide provides end-to-end, production-ready instructions for deploying **Vidhura AI** to real users on **Amazon Web Services (AWS)**.

---

## 🏛️ Architecture Overview

```mermaid
graph TD
    Users([🌐 Real Users / Browsers]) -->|HTTPS :443 / HTTP :80| DNS[AWS Route 53 / Custom Domain]
    DNS --> EIP[AWS Elastic IP]
    EIP --> EC2[AWS EC2 Instance: Ubuntu 22.04 LTS / t3.medium]
    
    subgraph EC2 Instance [Docker Compose Production Stack]
        Nginx[Nginx Reverse Proxy & Rate Limiter<br/>SSL via Let's Encrypt]
        Frontend[Next.js 15 SSR Frontend<br/>ghcr.io/.../vidhura-frontend]
        Backend[Spring Boot 3 API<br/>ghcr.io/.../vidhura-backend]
        DB[(MongoDB 7 Database<br/>Persistent Docker Volume)]
        
        Nginx -->|/ & /_next/| Frontend
        Nginx -->|/api/ & /actuator/| Backend
        Backend --> DB
    end

    Backend -->|LLM Reasoning| Groq[Groq Cloud API<br/>Llama 3.3 70B]
    Backend -->|Email OTP & Alerts| SMTP[SMTP Server<br/>Gmail / AWS SES]
```

---

## 📋 Pre-Deployment Checklist

Before launching, make sure you have:
- [x] An active **AWS Account** ([aws.amazon.com](https://aws.amazon.com)).
- [x] A registered domain name (e.g. `vidhura.ai`) managed on **AWS Route 53**, Cloudflare, or Namecheap.
- [x] A free **Groq API Key** for AI policy analysis ([console.groq.com/keys](https://console.groq.com/keys)).
- [x] An SMTP App Password (Gmail 2-Step Verification App Password or AWS SES credentials).

---

## 🎯 Recommended Method: AWS EC2 (Single VM + Docker Compose + Nginx)

This is the **most cost-effective, straightforward, and performant** setup for Vidhura AI ($15–$25/month). It runs all services securely with SSL, automated restarts, and rate limiting.

### Step 1: Launch an AWS EC2 Instance

1. Log in to the [AWS Management Console](https://console.aws.amazon.com/ec2).
2. Click **Launch Instance**:
   - **Name**: `vidhura-ai-production`
   - **Application and OS Images (AMI)**: `Ubuntu Server 22.04 LTS (HVM), SSD Volume Type` (64-bit x86).
   - **Instance Type**:
     - **AWS Free Tier (100% Free for 12 months)**: Select `t2.micro` (1 vCPU, 1 GiB RAM) or `t3.micro` (marked with the green *"Free tier eligible"* badge).
       > 💡 **Memory Safety**: `scripts/deploy-aws-ec2.sh` automatically creates a **4 GB Linux Swapfile** and caps the Java JVM heap to `384MB`. This expands your addressable memory to **5 GB**, ensuring Spring Boot, Next.js, and MongoDB run stably on AWS Free Tier without out-of-memory errors!
     - *Paid Scaled alternative*: `t3.medium` (2 vCPU, 4 GiB RAM) for higher concurrent user traffic.
   - **Key Pair**: Select an existing key pair or click **Create new key pair** (e.g., `vidhura-key.pem`) and save it to your local machine.
   - **Network Settings**:
     - Check ✅ **Allow SSH traffic from Anywhere** (or your specific IP).
     - Check ✅ **Allow HTTP traffic from the internet** (Port 80).
     - Check ✅ **Allow HTTPS traffic from the internet** (Port 443).
   - **Configure Storage**:
     - Set Root volume to **30 GiB gp3 SSD** (*30 GB is 100% Free Tier eligible*).
3. Click **Launch Instance**.

---

### Step 2: Allocate & Associate an Elastic IP

*An Elastic IP prevents your public IP address from changing whenever the EC2 instance stops or restarts.*

1. In the EC2 Console left sidebar, click **Elastic IPs** (under *Network & Security*).
2. Click **Allocate Elastic IP address** → **Allocate**.
3. Select your new Elastic IP → Click **Actions** → **Associate Elastic IP address**.
4. Choose your `vidhura-ai-production` instance and click **Associate**.
5. Note your new **Public IPv4 Address** (e.g. `54.210.123.45`).

---

### Step 3: Configure Domain DNS (Route 53 or Cloudflare)

Point your domain to your Elastic IP:
- **Type**: `A`
- **Name**: `@` (or `vidhura.yourdomain.com`)
- **Value**: Your Elastic IP address (`54.210.123.45`)
- **TTL**: `300` (5 minutes)

Add a `CNAME` for `www`:
- **Type**: `CNAME`
- **Name**: `www`
- **Value**: `yourdomain.com`

---

### Step 4: Connect to Your EC2 Instance via SSH

On your local terminal:
```bash
# Set proper file permissions for the private key
chmod 400 vidhura-key.pem

# SSH into your EC2 instance
ssh -i vidhura-key.pem ubuntu@54.210.123.45
```

---

### Step 5: Clone the Repository & Run the Automated Deployer

Run the automated production setup script:

```bash
# 1. Clone repository
git clone https://github.com/dileepkumarsasanapuri/VidhuraAi.git
cd VidhuraAi

# 2. Run the deployment script with sudo
sudo bash scripts/deploy-aws-ec2.sh
```

The script will automatically:
1. Update system packages.
2. Install Docker Engine and the Docker Compose plugin.
3. Generate a cryptographically secure `JWT_SECRET`.
4. Prompt for your domain name and `GROQ_API_KEY`.
5. Pull pre-built images from GitHub Container Registry (`ghcr.io`).
6. Start MongoDB, Spring Boot Backend, Next.js Frontend, and Nginx.
7. Configure `systemd` (`vidhura.service`) so the application restarts automatically on server reboot.

---

### Step 6: Configure Free SSL Certificate (HTTPS)

Once your domain DNS resolves to your EC2 Elastic IP:

1. Request your certificate using Let's Encrypt Certbot:
   ```bash
   sudo certbot certonly --webroot \
     -w /var/lib/docker/volumes/vidhuraai_certbot_www/_data \
     -d yourdomain.com -d www.yourdomain.com
   ```
2. Link the certificates to Nginx:
   ```bash
   sudo mkdir -p nginx/ssl
   sudo cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem nginx/ssl/cert.pem
   sudo cp /etc/letsencrypt/live/yourdomain.com/privkey.pem nginx/ssl/key.pem
   ```
3. Uncomment the SSL section in `nginx/nginx.conf`:
   ```bash
   sudo sed -i 's/# listen 443 ssl/listen 443 ssl/g' nginx/nginx.conf
   sudo sed -i 's/# ssl_certificate/ssl_certificate/g' nginx/nginx.conf
   sudo sed -i 's/# ssl_protocols/ssl_protocols/g' nginx/nginx.conf
   sudo sed -i 's/# ssl_ciphers/ssl_ciphers/g' nginx/nginx.conf
   ```
4. Reload Nginx:
   ```bash
   sudo docker compose -f docker-compose.prod.yml restart nginx
   ```

Your site is now live with **HTTPS / A+ SSL** at `https://yourdomain.com`! 🎉

---

## ⚙️ Production Environment Variables (`.env`)

Verify your `/home/ubuntu/VidhuraAi/.env` file contains production values:

| Variable | Description | Example / Recommended Value |
| :--- | :--- | :--- |
| `DOMAIN` | Your live domain | `vidhura.yourdomain.com` |
| `SPRING_PROFILES_ACTIVE` | Backend profile | `prod` |
| `JWT_SECRET` | 32+ char secret for JWT authentication | Generated by deploy script |
| `GROQ_API_KEY` | Groq LLM API Key | `gsk_...` |
| `GROQ_MODEL` | Reasoning model | `llama-3.3-70b-versatile` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origin | `https://yourdomain.com` |
| `SMTP_HOST` | Email server | `smtp.gmail.com` or `email-smtp.us-east-1.amazonaws.com` |
| `SMTP_PORT` | Email port | `587` |
| `SMTP_USERNAME` | SMTP User / Email | `your-email@gmail.com` |
| `SMTP_PASSWORD` | App Password | `abcd efgh ijkl mnop` |

---

## 🛠️ Management & Maintenance Commands

### Check Live Application Status
```bash
sudo docker compose -f docker-compose.prod.yml ps
```

### View Live Logs
```bash
# All services
sudo docker compose -f docker-compose.prod.yml logs -f

# Backend only
sudo docker compose -f docker-compose.prod.yml logs -f backend

# Frontend only
sudo docker compose -f docker-compose.prod.yml logs -f frontend
```

### Update to the Latest Code / Release
When you push new commits to GitHub `main`, CD automatically builds new Docker images. To update your EC2 server:
```bash
cd /home/ubuntu/VidhuraAi
sudo docker compose -f docker-compose.prod.yml pull
sudo docker compose -f docker-compose.prod.yml up -d
```

### Automated MongoDB Backups to S3
Create a nightly backup cronjob:
```bash
# Create backup directory
mkdir -p /home/ubuntu/backups

# Add to crontab (runs daily at 3:00 AM)
crontab -e
# Paste:
0 3 * * * docker exec vidhura-mongodb mongodump --archive="/data/db/backup-\$(date +\%F).gz" --gzip && cp /var/lib/docker/volumes/vidhuraai_mongodb_data/_data/backup-*.gz /home/ubuntu/backups/
```

---

## 🌐 Alternative: AWS Managed Serverless Route

If you prefer **zero server management** without an EC2 virtual machine:

1. **Database**: Use [MongoDB Atlas](https://www.mongodb.com/cloud/atlas) (select AWS as cloud provider, region `us-east-1` or `ap-south-1`).
   - Copy connection string to `MONGODB_URI` (e.g. `mongodb+srv://admin:pass@vidhura.mongodb.net/vidhura`).
2. **Backend**: Deploy on **AWS App Runner**:
   - Create Service → Container registry → Image repository: `ghcr.io/dileepkumarsasanapuri/vidhura-backend:latest`.
   - Port: `5000`.
   - Set environment variables from table above.
3. **Frontend**: Deploy on **AWS Amplify Hosting**:
   - Connect your GitHub repo (`VidhuraAi`), select `frontend` directory.
   - Amplify auto-detects Next.js SSR and provides free global CDN and SSL.
   - Set `NEXT_PUBLIC_API_URL` to your AWS App Runner backend URL.

---

## 🔒 Security Best Practices
- **Firewall**: Ensure MongoDB port `27017` is **never** exposed to the public internet (in `docker-compose.prod.yml`, it is only on the internal `vidhura-net` bridge).
- **CORS**: Set `CORS_ALLOWED_ORIGINS` strictly to your domain `https://yourdomain.com`.
- **Rate Limiting**: Rate limits are active in Nginx (`30 req/s` general, `5 req/s` for `/api/auth/`).
- **Healthchecks**: Monitored via `/api/health` and `/actuator/health`.
